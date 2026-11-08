package br.com.officyna.billing.infrastructure.mercadopago;

import br.com.officyna.billing.infrastructure.config.MercadoPagoProperties;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
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
	private final MercadoPagoProperties properties;

	public MercadoPagoOrderGateway(RestClient mercadoPagoRestClient, MercadoPagoProperties properties) {
		this.restClient = mercadoPagoRestClient;
		this.properties = properties;
	}

	public JsonNode createPixOrder(BigDecimal amount, String externalReference, String payerEmail) {
		return createPixOrder(amount, externalReference, payerEmail, null);
	}

	/**
	 * @param customerDocument CPF do pagador — o Pix no Brasil exige identificação por regra do
	 *                         Bacen; sem isso a Orders API pode recusar a cobrança. Omitido
	 *                         (null/branco) quando quem chama ainda não tem esse dado.
	 */
	public JsonNode createPixOrder(BigDecimal amount, String externalReference, String payerEmail, String customerDocument) {
		String amountStr = amount.toPlainString();
		Map<String, Object> body = Map.of(
				"type", "online",
				"processing_mode", "automatic",
				"total_amount", amountStr,
				"external_reference", externalReference,
				"payer", buildPayer(payerEmail, customerDocument),
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

	/**
	 * Em sandbox, a Orders API so aprova um Pix de teste sozinha (e dispara o webhook de
	 * verdade) se o payer vier com first_name "APRO" - sem isso o pedido fica parado pra
	 * sempre em action_required/pendente, porque nao existe Pix real por tras do sandbox.
	 * So entra quando mercadopago.test-mode esta ligado, pra nao arriscar mandar isso como
	 * nome de um cliente de verdade em producao.
	 */
	private Map<String, Object> buildPayer(String payerEmail, String customerDocument) {
		Map<String, Object> payer = new HashMap<>();
		payer.put("email", payerEmail);
		if (customerDocument != null && !customerDocument.isBlank()) {
			payer.put("identification", Map.of("type", "CPF", "number", customerDocument));
		}
		if (properties.testMode()) {
			payer.put("first_name", "APRO");
		}
		return payer;
	}

	/**
	 * @param cardPaymentMethodType "credit_card" ou "debit_card" — a Orders API usa esse campo
	 *                              pra diferenciar, não é algo inferido do token.
	 */
	public JsonNode createCardOrder(BigDecimal amount, String externalReference, String payerEmail,
			String cardToken, String paymentMethodId, String cardPaymentMethodType, int installments) {
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
								"type", cardPaymentMethodType,
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

	/**
	 * Estorno total do pedido — sem corpo, a Orders API devolve o pedido já com os detalhes do
	 * estorno em "transactions.payments[].refunds".
	 */
	public JsonNode refundOrder(String orderId) {
		return restClient.post()
				.uri("/v1/orders/{id}/refund", orderId)
				.header("X-Idempotency-Key", UUID.randomUUID().toString())
				.retrieve()
				.body(JsonNode.class);
	}

}
