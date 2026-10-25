package br.com.officyna.billing.infrastructure.messaging.listener;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetCalculationService;
import br.com.officyna.billing.budget.domain.LineItemInput;
import br.com.officyna.billing.infrastructure.messaging.config.SqsQueueUrlResolver;
import br.com.officyna.billing.infrastructure.messaging.publisher.BudgetEventPublisher;
import br.com.officyna.billing.infrastructure.messaging.resources.BudgetGeneratedEvent;
import br.com.officyna.billing.infrastructure.messaging.resources.GeneratePaymentEvent;
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
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Consome {@code eventGeneratePayment} (publicado pelo os-service logo após o diagnóstico),
 * calcula o orçamento inicial e publica {@code eventBudgetGenerated} com os totais.
 * Polling via {@code @Scheduled}, mesmo padrão do {@code ServiceOrderQueueListener} do
 * officyna-os-service.
 */
@Component
public class GeneratePaymentEventListener {

	private static final Logger log = LoggerFactory.getLogger(GeneratePaymentEventListener.class);

	private final SqsClient sqsClient;
	private final ObjectMapper objectMapper;
	private final BudgetCalculationService budgetCalculationService;
	private final BudgetEventPublisher budgetEventPublisher;
	private final SqsQueueUrlResolver queueUrlResolver;
	private final String queueName;

	public GeneratePaymentEventListener(SqsClient sqsClient, ObjectMapper messagingObjectMapper,
			BudgetCalculationService budgetCalculationService, BudgetEventPublisher budgetEventPublisher,
			SqsQueueUrlResolver queueUrlResolver,
			@Value("${aws.sqs.generate-payment-queue}") String queueName) {
		this.sqsClient = sqsClient;
		this.objectMapper = messagingObjectMapper;
		this.budgetCalculationService = budgetCalculationService;
		this.budgetEventPublisher = budgetEventPublisher;
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
				log.error("Falha ao processar eventGeneratePayment messageId={}", message.messageId(), e);
			}
		}
	}

	private void process(Message message) {
		GeneratePaymentEvent event = objectMapper.readValue(message.body(), GeneratePaymentEvent.class);

		var labors = event.labors().stream()
				.map(l -> new LineItemInput(l.laborId(), l.description(), l.amount()))
				.toList();
		var supplies = event.supplies().stream()
				.map(s -> new LineItemInput(s.supplyId(), s.description(), s.amount()))
				.toList();

		UUID correlationId = event.correlationId() != null ? UUID.fromString(event.correlationId()) : UUID.randomUUID();

		Budget budget = budgetCalculationService.generateInitialBudget(
				event.serviceOrderId(), correlationId, null, labors, supplies);

		budgetEventPublisher.publish(new BudgetGeneratedEvent(
				event.correlationId(),
				budget.getOrderId(),
				new BudgetGeneratedEvent.Totals(
						budget.getTotalLaborsAmount(), budget.getTotalSupplyAmount(),
						budget.getTotalTaxesAmount(), budget.getTotalBudgetAmount()),
				new BudgetGeneratedEvent.TaxDetails(budget.getIssAmount(), budget.getIcmsAmount(), java.math.BigDecimal.ZERO)));

		log.info("Orçamento inicial gerado orderId={} totalBudgetAmount={}", budget.getOrderId(), budget.getTotalBudgetAmount());
	}

}
