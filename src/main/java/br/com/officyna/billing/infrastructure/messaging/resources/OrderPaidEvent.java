package br.com.officyna.billing.infrastructure.messaging.resources;

import java.math.BigDecimal;

public record OrderPaidEvent(
		String correlationId,
		String serviceOrderId,
		PaymentInfo payment
) {

	public record PaymentInfo(String paymentId, String status, String paymentMethod,
			BigDecimal totalPaidAmount, String paidAt) {
	}

}
