package br.com.officyna.billing.infrastructure.messaging.listener;

import br.com.officyna.billing.infrastructure.messaging.config.SqsQueueUrlResolver;
import br.com.officyna.billing.infrastructure.messaging.resources.ServiceOrderRecusedEvent;
import br.com.officyna.billing.payment.domain.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consome {@code eventServiceOrderRecused}, publicado quando o cliente cancela/recusa a ordem
 * de serviço, e dispara o estorno (se já pago) via {@link PaymentService#handleServiceOrderRecused}.
 * A fila é inscrita no tópico SNS {@code eventServiceOrderRecused}: como o billing-service não
 * tem (ainda) uma classe base compartilhada como o {@code AbstractSqsQueueListener} do
 * os-service, o envelope SNS é desembrulhado manualmente aqui, mesmo formato.
 */
@Component
public class ServiceOrderRecusedEventListener {

	private static final Logger log = LoggerFactory.getLogger(ServiceOrderRecusedEventListener.class);

	private final SqsClient sqsClient;
	private final ObjectMapper objectMapper;
	private final PaymentService paymentService;
	private final SqsQueueUrlResolver queueUrlResolver;
	private final String queueName;

	public ServiceOrderRecusedEventListener(SqsClient sqsClient, ObjectMapper messagingObjectMapper,
			PaymentService paymentService, SqsQueueUrlResolver queueUrlResolver,
			@Value("${aws.sqs.service-order-recused-queue}") String queueName) {
		this.sqsClient = sqsClient;
		this.objectMapper = messagingObjectMapper;
		this.paymentService = paymentService;
		this.queueUrlResolver = queueUrlResolver;
		this.queueName = queueName;
	}

	@Scheduled(fixedDelay = 5000)
	public void poll() {
		String queueUrl = queueUrlResolver.resolve(queueName);
		ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
				.queueUrl(queueUrl)
				.maxNumberOfMessages(10)
				.waitTimeSeconds(5)
				.build());

		for (Message message : response.messages()) {
			try {
				process(message);
				sqsClient.deleteMessage(DeleteMessageRequest.builder()
						.queueUrl(queueUrl)
						.receiptHandle(message.receiptHandle())
						.build());
			} catch (Exception e) {
				log.error("Falha ao processar eventServiceOrderRecused messageId={}", message.messageId(), e);
			}
		}
	}

	private void process(Message message) {
		String payloadJson = extractPayload(message.body());
		ServiceOrderRecusedEvent event = objectMapper.readValue(payloadJson, ServiceOrderRecusedEvent.class);
		paymentService.handleServiceOrderRecused(event.serviceOrderId());
	}

	private String extractPayload(String body) {
		if (body == null || body.isBlank()) {
			return body;
		}
		try {
			JsonNode root = objectMapper.readTree(body);
			if (root.has("Type") && "Notification".equals(root.path("Type").asText()) && root.has("Message")) {
				return root.path("Message").asText();
			}
		} catch (Exception ignored) {
			// Não é um envelope SNS — segue com o corpo original.
		}
		return body;
	}

}
