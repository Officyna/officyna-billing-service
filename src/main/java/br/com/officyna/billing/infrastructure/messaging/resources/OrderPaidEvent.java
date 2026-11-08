package br.com.officyna.billing.infrastructure.messaging.resources;

import java.math.BigDecimal;

/**
 * correlationId não entra aqui de propósito: já viaja via message attribute do SNS
 * (CorrelationConstants), igual o resto da mensageria deste serviço - o os-service alinhou o
 * ApprovalEventMessage dele pra aceitar esse formato aninhado.
 */
public record OrderPaidEvent(String serviceOrderId, PaymentInfo payment) {

	public record PaymentInfo(String paymentId, String status, String paymentMethod,
			BigDecimal totalPaidAmount, String paidAt) {
	}

}
