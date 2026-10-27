package br.com.officyna.billing.infrastructure.messaging.config;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a queue name to its full URL via the SQS API, caching the result. Uses
 * {@code CreateQueue} (idempotent — returns the existing queue's URL if it's already there)
 * instead of {@code GetQueueUrl}: billing-service owns both queues it touches, so it creates
 * them on first use rather than assuming another service already did.
 */
@Component
public class SqsQueueUrlResolver {

	private final SqsClient sqsClient;
	private final Map<String, String> cache = new ConcurrentHashMap<>();

	public SqsQueueUrlResolver(SqsClient sqsClient) {
		this.sqsClient = sqsClient;
	}

	public String resolve(String queueName) {
		return cache.computeIfAbsent(queueName,
				name -> sqsClient.createQueue(CreateQueueRequest.builder().queueName(name).build()).queueUrl());
	}

}
