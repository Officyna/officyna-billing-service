package br.com.officyna.billing.infrastructure.messaging.resources;

public record ServiceOrderRecusedEvent(
		String correlationId,
		String serviceOrderId,
		String reason
) {
}
