package br.com.officyna.billing.payment.api.resources;

public record PixCheckoutResponse(
		String orderId,
		String externalReference,
		String status,
		String qrCode,
		String qrCodeBase64
) {
}
