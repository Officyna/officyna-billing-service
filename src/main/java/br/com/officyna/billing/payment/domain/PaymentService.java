package br.com.officyna.billing.payment.domain;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetRepository;
import br.com.officyna.billing.budget.domain.BudgetStatus;
import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import br.com.officyna.billing.infrastructure.messaging.publisher.OrderPaidTopicPublisher;
import br.com.officyna.billing.infrastructure.messaging.publisher.RejectionTopicPublisher;
import br.com.officyna.billing.infrastructure.messaging.resources.OrderPaidEvent;
import br.com.officyna.billing.infrastructure.messaging.resources.RejectionEvent;
import br.com.officyna.billing.infrastructure.security.WebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final MercadoPagoOrderGateway gateway;
	private final WebhookSignatureValidator signatureValidator;
	private final PaymentRepository paymentRepository;
	private final BudgetRepository budgetRepository;
	private final OrderPaidTopicPublisher orderPaidTopicPublisher;
	private final RejectionTopicPublisher rejectionTopicPublisher;

	public PaymentService(MercadoPagoOrderGateway gateway, WebhookSignatureValidator signatureValidator,
			PaymentRepository paymentRepository, BudgetRepository budgetRepository,
			OrderPaidTopicPublisher orderPaidTopicPublisher, RejectionTopicPublisher rejectionTopicPublisher) {
		this.gateway = gateway;
		this.signatureValidator = signatureValidator;
		this.paymentRepository = paymentRepository;
		this.budgetRepository = budgetRepository;
		this.orderPaidTopicPublisher = orderPaidTopicPublisher;
		this.rejectionTopicPublisher = rejectionTopicPublisher;
	}

	/**
	 * {@code data.id} do webhook é o id do pedido (Orders API) que já persistimos como
	 * {@code externalPaymentId} na hora do checkout.
	 */
	public boolean handleWebhook(String xSignature, String xRequestId, String type, String dataId) {
		if (!signatureValidator.isValid(xSignature, xRequestId, dataId)) {
			log.warn("Rejeitado webhook com assinatura inválida (data.id={})", dataId);
			return false;
		}
		if (dataId == null) {
			return true;
		}

		var paymentOpt = paymentRepository.findByExternalPaymentId(dataId);
		if (paymentOpt.isEmpty()) {
			log.info("Ignorando webhook (type='{}') de pedido desconhecido {}", type, dataId);
			return true;
		}
		Payment payment = paymentOpt.get();

		JsonNode order = gateway.getOrder(dataId);
		PaymentStatus newStatus = mapStatus(order.path("status").asText());
		boolean justPaid = newStatus == PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PAID;
		boolean justFailed = newStatus == PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.FAILED;

		Payment updated = payment.toBuilder()
				.status(newStatus)
				.paidAmount(newStatus == PaymentStatus.PAID ? new BigDecimal(order.path("total_amount").asText("0")) : payment.getPaidAmount())
				.paidAt(newStatus == PaymentStatus.PAID ? Instant.now() : payment.getPaidAt())
				.build();
		paymentRepository.save(updated);

		if (justPaid) {
			OrderPaidEvent event = new OrderPaidEvent(updated.getOrderId(),
					new OrderPaidEvent.PaymentInfo(updated.getExternalPaymentId(), newStatus.name(),
							updated.getPaymentMethod().name(), updated.getPaidAmount(), updated.getPaidAt().toString()));
			orderPaidTopicPublisher.publish(null, event);
			log.info("Publicado eventOrderPaid para OS {} paymentId={} status={}",
					updated.getOrderId(), updated.getExternalPaymentId(), newStatus);
		}
		if (justFailed) {
			rejectionTopicPublisher.publish(new RejectionEvent(
					null, updated.getOrderId(), newStatus.name(), "Pagamento recusado pelo Mercado Pago"));
		}

		log.info("Pagamento {} da OS {} atualizado para {} via webhook", dataId, payment.getOrderId(), newStatus);
		return true;
	}

	/**
	 * Cliente cancelou/recusou a ordem de serviço: se já havia pagamento aprovado, solicita o
	 * estorno ao Mercado Pago e marca REFUNDED; senão, apenas marca FAILED (nunca chegou a pagar).
	 * Se a OS foi recusada antes de sequer gerar pagamento (ainda na 1ª rodada do
	 * eventGeneratePayment, só orçamento), não existe Payment pra atualizar, mas o Budget sim -
	 * marca REJECTED nele pra não ficar PENDING_APPROVAL pra sempre (rastreabilidade/auditoria).
	 */
	public void handleServiceOrderRecused(String serviceOrderId) {
		var paymentOpt = paymentRepository.findByOrderId(serviceOrderId);
		if (paymentOpt.isEmpty()) {
			rejectBudgetIfPresent(serviceOrderId);
			log.info("Ignorando eventServiceOrderRecused: nenhum pagamento encontrado para a OS {}", serviceOrderId);
			return;
		}
		Payment payment = paymentOpt.get();

		if (payment.getStatus() == PaymentStatus.PAID) {
			gateway.refundOrder(payment.getExternalPaymentId());
			Payment refunded = payment.toBuilder().status(PaymentStatus.REFUNDED).build();
			paymentRepository.save(refunded);
			log.info("Pagamento {} da OS {} estornado após recusa/cancelamento da ordem de serviço",
					payment.getExternalPaymentId(), serviceOrderId);
		} else if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.REFUNDED) {
			Payment failed = payment.toBuilder().status(PaymentStatus.FAILED).build();
			paymentRepository.save(failed);
			log.info("Pagamento da OS {} marcado como FAILED após recusa/cancelamento da ordem de serviço (sem pagamento a estornar)",
					serviceOrderId);
		}
	}

	private void rejectBudgetIfPresent(String serviceOrderId) {
		budgetRepository.findByOrderId(serviceOrderId).ifPresent(budget -> {
			if (budget.getStatus() == BudgetStatus.REJECTED) {
				return;
			}
			Budget rejected = budget.toBuilder().status(BudgetStatus.REJECTED).build();
			budgetRepository.save(rejected);
			log.info("Orçamento da OS {} marcado como REJECTED após recusa/cancelamento (sem pagamento gerado ainda)",
					serviceOrderId);
		});
	}

	private PaymentStatus mapStatus(String mercadoPagoStatus) {
		return switch (mercadoPagoStatus) {
			case "processed", "approved", "accredited" -> PaymentStatus.PAID;
			case "action_required", "in_process", "pending" -> PaymentStatus.WAITING_PAYMENT;
			case "failed", "rejected", "cancelled" -> PaymentStatus.FAILED;
			default -> {
				log.error("PAGAMENTO COM STATUS NAO TRATADO recebido do Mercado Pago: '{}' - IMPLEMENTAR TRATATIVA " +
						"(ex: 'refunded' apos estorno). Mantendo como WAITING_PAYMENT ate decisao.", mercadoPagoStatus);
				yield PaymentStatus.WAITING_PAYMENT;
			}
		};
	}

}
