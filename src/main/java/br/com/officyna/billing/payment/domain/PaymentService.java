package br.com.officyna.billing.payment.domain;

import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import br.com.officyna.billing.infrastructure.messaging.publisher.OrderPaidTopicPublisher;
import br.com.officyna.billing.infrastructure.messaging.resources.OrderPaidEvent;
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
	private final OrderPaidTopicPublisher orderPaidTopicPublisher;

	public PaymentService(MercadoPagoOrderGateway gateway, WebhookSignatureValidator signatureValidator,
			PaymentRepository paymentRepository, OrderPaidTopicPublisher orderPaidTopicPublisher) {
		this.gateway = gateway;
		this.signatureValidator = signatureValidator;
		this.paymentRepository = paymentRepository;
		this.orderPaidTopicPublisher = orderPaidTopicPublisher;
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

		Payment updated = payment.toBuilder()
				.status(newStatus)
				.paidAmount(newStatus == PaymentStatus.PAID ? new BigDecimal(order.path("total_amount").asText("0")) : payment.getPaidAmount())
				.paidAt(newStatus == PaymentStatus.PAID ? Instant.now() : payment.getPaidAt())
				.build();
		paymentRepository.save(updated);

		if (justPaid) {
			orderPaidTopicPublisher.publish(new OrderPaidEvent(
					null,
					updated.getOrderId(),
					new OrderPaidEvent.PaymentInfo(updated.getExternalPaymentId(), newStatus.name(),
							updated.getPaymentMethod().name(), updated.getPaidAmount(), updated.getPaidAt().toString())));
		}

		log.info("Pagamento {} da OS {} atualizado para {} via webhook", dataId, payment.getOrderId(), newStatus);
		return true;
	}

	private PaymentStatus mapStatus(String mercadoPagoStatus) {
		return switch (mercadoPagoStatus) {
			case "processed", "approved", "accredited" -> PaymentStatus.PAID;
			case "action_required", "in_process", "pending" -> PaymentStatus.WAITING_PAYMENT;
			case "failed", "rejected", "cancelled" -> PaymentStatus.FAILED;
			default -> PaymentStatus.WAITING_PAYMENT;
		};
	}

}
