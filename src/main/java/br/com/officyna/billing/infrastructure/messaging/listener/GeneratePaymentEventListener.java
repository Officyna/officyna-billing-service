package br.com.officyna.billing.infrastructure.messaging.listener;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetCalculationService;
import br.com.officyna.billing.budget.domain.BudgetRepository;
import br.com.officyna.billing.budget.domain.LineItemInput;
import br.com.officyna.billing.infrastructure.messaging.CorrelationConstants;
import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import br.com.officyna.billing.infrastructure.messaging.config.SqsQueueUrlResolver;
import br.com.officyna.billing.infrastructure.messaging.publisher.BudgetEventPublisher;
import br.com.officyna.billing.infrastructure.messaging.resources.BudgetGeneratedEvent;
import br.com.officyna.billing.infrastructure.messaging.resources.GeneratePaymentEvent;
import br.com.officyna.billing.payment.domain.Payment;
import br.com.officyna.billing.payment.domain.PaymentMethod;
import br.com.officyna.billing.payment.domain.PaymentRepository;
import br.com.officyna.billing.payment.domain.PaymentStatus;
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

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consome {@code eventGeneratePayment}, publicado pelo os-service duas vezes por OS (conforme o
 * diagrama da saga): a 1ª logo após o diagnóstico, só pra calcular o orçamento inicial; a 2ª
 * depois que o cliente confirma os itens finais, quando então já geramos o Pix de verdade no
 * Mercado Pago e devolvemos o QR Code em {@code eventBudgetGenerated}. As duas rodadas mandam o
 * mesmo formato de mensagem — a diferença é só se já existe orçamento salvo pra essa OS.
 * Polling via {@code @Scheduled}, mesmo padrão do {@code ServiceOrderQueueListener} do
 * officyna-os-service.
 */
@Component
public class GeneratePaymentEventListener {

	private static final Logger log = LoggerFactory.getLogger(GeneratePaymentEventListener.class);

	private final SqsClient sqsClient;
	private final ObjectMapper objectMapper;
	private final BudgetCalculationService budgetCalculationService;
	private final BudgetRepository budgetRepository;
	private final BudgetEventPublisher budgetEventPublisher;
	private final MercadoPagoOrderGateway gateway;
	private final PaymentRepository paymentRepository;
	private final SqsQueueUrlResolver queueUrlResolver;
	private final String queueName;

	public GeneratePaymentEventListener(SqsClient sqsClient, ObjectMapper messagingObjectMapper,
			BudgetCalculationService budgetCalculationService, BudgetRepository budgetRepository,
			BudgetEventPublisher budgetEventPublisher, MercadoPagoOrderGateway gateway,
			PaymentRepository paymentRepository, SqsQueueUrlResolver queueUrlResolver,
			@Value("${aws.sqs.generate-payment-queue}") String queueName) {
		this.sqsClient = sqsClient;
		this.objectMapper = messagingObjectMapper;
		this.budgetCalculationService = budgetCalculationService;
		this.budgetRepository = budgetRepository;
		this.budgetEventPublisher = budgetEventPublisher;
		this.gateway = gateway;
		this.paymentRepository = paymentRepository;
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
				.messageAttributeNames("All")
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
		String correlationId = extractCorrelationId(message, event.correlationId());

		boolean recalculo = budgetRepository.findByOrderId(event.serviceOrderId()).isPresent();

		Budget budget;
		String paymentLink = null;
		String qrCode = null;
		String qrCodeBase64 = null;

		if (!recalculo) {
			var labors = event.labors().stream()
					.map(l -> new LineItemInput(l.laborId(), l.description(), l.amount()))
					.toList();
			var supplies = event.supplies().stream()
					.map(s -> new LineItemInput(s.supplyId(), s.description(), s.amount()))
					.toList();

			// UUID interno do orcamento (usado como external_reference no Mercado Pago) e
			// sempre gerado por nos, independente do correlationId de rastreio - o do
			// os-service e uma string livre, nao necessariamente em formato UUID.
			budget = budgetCalculationService.generateInitialBudget(
					event.serviceOrderId(), UUID.randomUUID(), event.customerDocument(), labors, supplies);

			log.info("Orçamento inicial gerado orderId={} totalBudgetAmount={}", budget.getOrderId(), budget.getTotalBudgetAmount());
		} else {
			var laborIds = event.labors().stream().map(GeneratePaymentEvent.LaborPayload::laborId)
					.collect(Collectors.toSet());
			var supplyIds = event.supplies().stream().map(GeneratePaymentEvent.SupplyPayload::supplyId)
					.collect(Collectors.toSet());

			budget = budgetCalculationService.approveItems(event.serviceOrderId(), laborIds, supplyIds);

			JsonNode order = gateway.createPixOrder(budget.getTotalBudgetAmount(), budget.getCorrelationId().toString(),
					event.payerEmail(), event.customerDocument());
			JsonNode paymentNode = order.path("transactions").path("payments").path(0);
			JsonNode paymentMethodNode = paymentNode.path("payment_method");
			qrCode = paymentMethodNode.path("qr_code").asText(null);
			qrCodeBase64 = paymentMethodNode.path("qr_code_base64").asText(null);
			paymentLink = paymentMethodNode.path("ticket_url").asText(null);

			paymentRepository.save(Payment.builder()
					.id(UUID.randomUUID())
					.budgetId(budget.getId())
					.orderId(event.serviceOrderId())
					.externalPaymentId(order.path("id").asText())
					.paymentMethod(PaymentMethod.PIX)
					.qrCode(qrCode)
					.qrCodeBase64(qrCodeBase64)
					.status(PaymentStatus.WAITING_PAYMENT)
					.build());

			log.info("Pix gerado via recálculo orderId={} totalBudgetAmount={}", budget.getOrderId(), budget.getTotalBudgetAmount());
		}

		budgetEventPublisher.publish(new BudgetGeneratedEvent(
				correlationId,
				budget.getOrderId(),
				new BudgetGeneratedEvent.Totals(
						budget.getTotalLaborsAmount(), budget.getTotalSupplyAmount(),
						budget.getTotalTaxesAmount(), budget.getTotalBudgetAmount()),
				new BudgetGeneratedEvent.TaxDetails(budget.getIssAmount(), budget.getIcmsAmount(), java.math.BigDecimal.ZERO),
				paymentLink,
				qrCode,
				qrCodeBase64));
	}

	/**
	 * O os-service manda o correlationId como message attribute do SQS (nao no corpo da
	 * mensagem) - mesmo padrao da {@code AbstractSqsQueueListener} dele. Sem pedir
	 * {@code messageAttributeNames("All")} no receive, a gente nunca via esse valor. O campo
	 * do corpo fica so como fallback (ex: testes manuais mandando direto na fila).
	 */
	private String extractCorrelationId(Message message, String bodyFallback) {
		var attributes = message.messageAttributes();
		if (attributes.containsKey(CorrelationConstants.CORRELATION_ID_HEADER)) {
			return attributes.get(CorrelationConstants.CORRELATION_ID_HEADER).stringValue();
		}
		if (attributes.containsKey(CorrelationConstants.LEGACY_CORRELATION_ID_HEADER)) {
			return attributes.get(CorrelationConstants.LEGACY_CORRELATION_ID_HEADER).stringValue();
		}
		return bodyFallback;
	}

}
