package br.com.officyna.billing.infrastructure.messaging.publisher;

import br.com.officyna.billing.infrastructure.messaging.config.SqsQueueUrlResolver;
import br.com.officyna.billing.infrastructure.messaging.resources.BudgetGeneratedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Component
public class BudgetEventPublisher {

	private final SqsClient sqsClient;
	private final ObjectMapper objectMapper;
	private final SqsQueueUrlResolver queueUrlResolver;
	private final String queueName;

	public BudgetEventPublisher(SqsClient sqsClient, ObjectMapper messagingObjectMapper,
			SqsQueueUrlResolver queueUrlResolver,
			@Value("${aws.sqs.budget-generated-queue}") String queueName) {
		this.sqsClient = sqsClient;
		this.objectMapper = messagingObjectMapper;
		this.queueUrlResolver = queueUrlResolver;
		this.queueName = queueName;
	}

	public void publish(BudgetGeneratedEvent event) {
		sqsClient.sendMessage(SendMessageRequest.builder()
				.queueUrl(queueUrlResolver.resolve(queueName))
				.messageBody(objectMapper.writeValueAsString(event))
				.build());
	}

}
