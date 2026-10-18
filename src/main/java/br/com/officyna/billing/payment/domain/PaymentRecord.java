package br.com.officyna.billing.payment.domain;

public record PaymentRecord(String orderId, String externalReference, String method, String status) {

	public PaymentRecord withStatus(String newStatus) {
		return new PaymentRecord(orderId, externalReference, method, newStatus);
	}

}
