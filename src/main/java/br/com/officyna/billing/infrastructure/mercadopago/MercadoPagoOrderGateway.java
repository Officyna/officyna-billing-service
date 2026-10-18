package br.com.officyna.billing.infrastructure.mercadopago;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Talks to the Mercado Pago Orders API (POST /v1/orders) and Payments API (GET /v1/payments/{id}).
 * Request bodies are built as nested maps instead of typed DTOs: the Orders API payload has many
 * optional nested fields and modeling it fully isn't worth it for this POC.
 */
@Component
public class MercadoPagoOrderGateway {

	private final RestClient restClient;

	public MercadoPagoOrderGateway(RestClient mercadoPagoRestClient) {
		this.restClient = mercadoPagoRestClient;
	}

	public JsonNode createPixOrder(BigDecimal amount, String externalReference, String payerEmail) {
		String amountStr = amount.toPlainString();
		Map<String, Object> body = Map.of(
				"type", "online",
				"processing_mode", "automatic",
				"total_amount", amountStr,
				"external_reference", externalReference,
				"payer", Map.of("email", payerEmail),
				"transactions", Map.of("payments", List.of(Map.of(
						"amount", amountStr,
						"payment_method", Map.of(
								"id", "pix",
								"type", "bank_transfer"
						)
				)))
		);
		return postOrder(body);
	}

	public JsonNode createCardOrder(BigDecimal amount, String externalReference, String payerEmail,
			String cardToken, String paymentMethodId, int installments) {
		String amountStr = amount.toPlainString();
		Map<String, Object> body = Map.of(
				"type", "online",
				"processing_mode", "automatic",
				"total_amount", amountStr,
				"external_reference", externalReference,
				"payer", Map.of("email", payerEmail),
				"transactions", Map.of("payments", List.of(Map.of(
						"amount", amountStr,
						"payment_method", Map.of(
								"id", paymentMethodId,
								"type", "credit_card",
								"token", cardToken,
								"installments", installments
						)
				)))
		);
		return postOrder(body);
	}

	private JsonNode postOrder(Map<String, Object> body) {
		return restClient.post()
				.uri("/v1/orders")
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.body(body)
				.retrieve()
				.body(JsonNode.class);
	}

	/**
	 * Orders API payment ids (prefix PAY...) aren't queryable through the legacy
	 * /v1/payments/{id} endpoint — status is always re-checked through the order itself.
	 */
	public JsonNode getOrder(String orderId) {
		return restClient.get()
				.uri("/v1/orders/{id}", orderId)
				.retrieve()
				.body(JsonNode.class);
	}

}
