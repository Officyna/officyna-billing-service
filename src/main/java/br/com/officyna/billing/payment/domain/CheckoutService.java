package br.com.officyna.billing.payment.domain;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetCalculationService;
import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Orquestra o {@code POST /checkout/create}: recalcula o orçamento só com os itens aprovados
 * pelo cliente, gera a cobrança no Mercado Pago (Pix ou cartão — Checkout Transparente) e
 * persiste o pagamento como {@link PaymentStatus#WAITING_PAYMENT}.
 */
@Service
public class CheckoutService {

	private final BudgetCalculationService budgetCalculationService;
	private final MercadoPagoOrderGateway gateway;
	private final PaymentRepository paymentRepository;

	public CheckoutService(BudgetCalculationService budgetCalculationService, MercadoPagoOrderGateway gateway,
			PaymentRepository paymentRepository) {
		this.budgetCalculationService = budgetCalculationService;
		this.gateway = gateway;
		this.paymentRepository = paymentRepository;
	}

	public CheckoutResult checkout(String orderId, PaymentMethod paymentMethod, String payerEmail,
			Set<String> approvedLaborIds, Set<String> approvedSupplyIds,
			String cardToken, String cardBrand, Integer installments) {

		Budget budget = budgetCalculationService.approveItems(orderId, approvedLaborIds, approvedSupplyIds);
		BigDecimal amount = budget.getTotalBudgetAmount();
		String externalReference = budget.getCorrelationId().toString();

		String qrCode = null;
		String qrCodeBase64 = null;
		JsonNode order;

		if (paymentMethod == PaymentMethod.PIX) {
			order = gateway.createPixOrder(amount, externalReference, payerEmail);
			JsonNode paymentMethodNode = order.path("transactions").path("payments").path(0).path("payment_method");
			qrCode = paymentMethodNode.path("qr_code").asText(null);
			qrCodeBase64 = paymentMethodNode.path("qr_code_base64").asText(null);
		} else {
			String cardType = paymentMethod == PaymentMethod.CREDIT_CARD ? "credit_card" : "debit_card";
			order = gateway.createCardOrder(amount, externalReference, payerEmail, cardToken, cardBrand, cardType,
					installments != null ? installments : 1);
		}

		String externalPaymentId = order.path("id").asText();

		Payment payment = Payment.builder()
				.id(UUID.randomUUID())
				.budgetId(budget.getId())
				.orderId(orderId)
				.externalPaymentId(externalPaymentId)
				.paymentMethod(paymentMethod)
				.qrCode(qrCode)
				.qrCodeBase64(qrCodeBase64)
				.status(PaymentStatus.WAITING_PAYMENT)
				.build();
		paymentRepository.save(payment);

		return new CheckoutResult(orderId, PaymentStatus.WAITING_PAYMENT.name(), qrCode, qrCodeBase64, amount);
	}

	public record CheckoutResult(String serviceOrderId, String status, String qrCode, String qrCodeBase64, BigDecimal totalAmount) {
	}

}
