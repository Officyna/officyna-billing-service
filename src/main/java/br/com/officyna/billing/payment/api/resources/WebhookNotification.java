package br.com.officyna.billing.payment.api.resources;

public record WebhookNotification(String type, String action, Data data) {

	public record Data(String id) {
	}

}
