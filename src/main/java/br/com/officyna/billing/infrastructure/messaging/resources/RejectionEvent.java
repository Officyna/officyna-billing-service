package br.com.officyna.billing.infrastructure.messaging.resources;

public record RejectionEvent(
		String correlationId,
		String serviceOrderId,
		String statusPayment,
		String description
) {
}
