package br.com.officyna.billing.infrastructure.messaging.publisher;

import br.com.officyna.billing.infrastructure.messaging.resources.RejectionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Billing-service é dono deste tópico, mesmo padrão do OrderPaidTopicPublisher: cria de forma
 * idempotente em vez de exigir um ARN pré-provisionado na config.
 */
@Component
public class RejectionTopicPublisher {

	private final SnsClient snsClient;
	private final ObjectMapper objectMapper;
	private final String topicName;
	private volatile String cachedTopicArn;

	public RejectionTopicPublisher(SnsClient snsClient, ObjectMapper messagingObjectMapper,
			@Value("${aws.sns.rejection-topic-name}") String topicName) {
		this.snsClient = snsClient;
		this.objectMapper = messagingObjectMapper;
		this.topicName = topicName;
	}

	public void publish(RejectionEvent event) {
		snsClient.publish(PublishRequest.builder()
				.topicArn(topicArn())
				.message(objectMapper.writeValueAsString(event))
				.build());
	}

	private String topicArn() {
		if (cachedTopicArn == null) {
			cachedTopicArn = snsClient.createTopic(CreateTopicRequest.builder().name(topicName).build()).topicArn();
		}
		return cachedTopicArn;
	}

}
