package br.com.officyna.billing.payment.domain;

import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import br.com.officyna.billing.infrastructure.security.WebhookSignatureValidator;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final MercadoPagoOrderGateway gateway;
	private final WebhookSignatureValidator signatureValidator;
	private final PaymentStatusStore store;

	public PaymentService(MercadoPagoOrderGateway gateway, WebhookSignatureValidator signatureValidator,
			PaymentStatusStore store) {
		this.gateway = gateway;
		this.signatureValidator = signatureValidator;
		this.store = store;
	}

	public PixCheckoutResult checkoutPix(BigDecimal amount, String payerEmail) {
		String externalReference = UUID.randomUUID().toString();
		JsonNode order = gateway.createPixOrder(amount, externalReference, payerEmail);
		String orderId = order.path("id").asText();
		String status = order.path("status").asText();
		JsonNode paymentMethod = order.path("transactions").path("payments").path(0).path("payment_method");
		String qrCode = paymentMethod.path("qr_code").asText(null);
		String qrCodeBase64 = paymentMethod.path("qr_code_base64").asText(null);

		store.save(new PaymentRecord(orderId, externalReference, "pix", status));
		return new PixCheckoutResult(orderId, externalReference, status, qrCode, qrCodeBase64);
	}

	public CardCheckoutResult checkoutCard(BigDecimal amount, String payerEmail, String cardToken,
			String paymentMethodId, int installments) {
		String externalReference = UUID.randomUUID().toString();
		JsonNode order = gateway.createCardOrder(amount, externalReference, payerEmail, cardToken, paymentMethodId, installments);
		String orderId = order.path("id").asText();
		String status = order.path("status").asText();

		store.save(new PaymentRecord(orderId, externalReference, "card", status));
		return new CardCheckoutResult(orderId, externalReference, status);
	}

	public boolean handleWebhook(String xSignature, String xRequestId, String type, String dataId) {
		if (!signatureValidator.isValid(xSignature, xRequestId, dataId)) {
			log.warn("Rejected webhook call with invalid x-signature (data.id={})", dataId);
			return false;
		}
		if (dataId == null || store.findByOrderId(dataId).isEmpty()) {
			log.info("Ignoring webhook notification (type='{}') for unknown order id {}", type, dataId);
			return true;
		}

		JsonNode order = gateway.getOrder(dataId);
		String status = order.path("status").asText();
		store.updateStatus(dataId, status);
		log.info("Order {} updated to status {} via webhook", dataId, status);
		return true;
	}

	public record PixCheckoutResult(String orderId, String externalReference, String status, String qrCode, String qrCodeBase64) {
	}

	public record CardCheckoutResult(String orderId, String externalReference, String status) {
	}

}
