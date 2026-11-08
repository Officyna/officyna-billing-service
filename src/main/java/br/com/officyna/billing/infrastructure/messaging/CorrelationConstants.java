package br.com.officyna.billing.infrastructure.messaging;

import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.Map;

/**
 * Mesmos nomes de attribute que o os-service usa na {@code AbstractSqsQueueListener}/
 * {@code ServiceOrderSqsPublisherGateway}: correlationId viaja como message attribute do
 * SQS/SNS, nunca no corpo da mensagem. Sem esses nomes batendo, cada lado fica cego pro
 * correlationId que o outro manda.
 */
public final class CorrelationConstants {

	public static final String CORRELATION_ID_HEADER = "correlationId";
	public static final String LEGACY_CORRELATION_ID_HEADER = "X-Correlation-ID";

	private CorrelationConstants() {
	}

	public static Map<String, MessageAttributeValue> sqsAttribute(String correlationId) {
		if (correlationId == null || correlationId.isBlank()) {
			return Map.of();
		}
		return Map.of(CORRELATION_ID_HEADER, MessageAttributeValue.builder()
				.dataType("String")
				.stringValue(correlationId)
				.build());
	}

	public static Map<String, software.amazon.awssdk.services.sns.model.MessageAttributeValue> snsAttribute(String correlationId) {
		if (correlationId == null || correlationId.isBlank()) {
			return Map.of();
		}
		return Map.of(CORRELATION_ID_HEADER, software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
				.dataType("String")
				.stringValue(correlationId)
				.build());
	}

}
