package br.com.officyna.billing.payment.api.resources;

public record CardCheckoutResponse(
		String orderId,
		String externalReference,
		String status
) {
}
