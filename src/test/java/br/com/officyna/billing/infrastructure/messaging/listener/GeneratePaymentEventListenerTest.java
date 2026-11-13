package br.com.officyna.billing.infrastructure.messaging.listener;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetCalculationService;
import br.com.officyna.billing.budget.domain.BudgetRepository;
import br.com.officyna.billing.budget.domain.BudgetStatus;
import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import br.com.officyna.billing.infrastructure.messaging.config.SqsQueueUrlResolver;
import br.com.officyna.billing.infrastructure.messaging.publisher.BudgetEventPublisher;
import br.com.officyna.billing.infrastructure.messaging.resources.BudgetGeneratedEvent;
import br.com.officyna.billing.payment.domain.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratePaymentEventListenerTest {

	private static final JsonMapper MAPPER = new JsonMapper();

	@Mock
	private SqsClient sqsClient;

	@Mock
	private BudgetCalculationService budgetCalculationService;

	@Mock
	private BudgetRepository budgetRepository;

	@Mock
	private BudgetEventPublisher budgetEventPublisher;

	@Mock
	private MercadoPagoOrderGateway gateway;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private SqsQueueUrlResolver queueUrlResolver;

	private GeneratePaymentEventListener listener() {
		return new GeneratePaymentEventListener(sqsClient, MAPPER, budgetCalculationService, budgetRepository,
				budgetEventPublisher, gateway, paymentRepository, queueUrlResolver, "eventGeneratePayment");
	}

	private Budget budgetComTotais() {
		return Budget.builder()
				.id(UUID.randomUUID())
				.orderId("os-1")
				.correlationId(UUID.randomUUID())
				.status(BudgetStatus.PENDING_APPROVAL)
				.totalLaborsAmount(new BigDecimal("100.00"))
				.totalSupplyAmount(new BigDecimal("50.00"))
				.issAmount(new BigDecimal("5.00"))
				.icmsAmount(new BigDecimal("6.00"))
				.totalTaxesAmount(new BigDecimal("11.00"))
				.totalBudgetAmount(new BigDecimal("161.00"))
				.items(java.util.List.of())
				.build();
	}

	private void mockReceiveSingleMessage(String body) {
		mockReceiveSingleMessage(body, Map.of());
	}

	private void mockReceiveSingleMessage(String body, Map<String, MessageAttributeValue> attributes) {
		when(queueUrlResolver.resolve("eventGeneratePayment")).thenReturn("queue-url");
		when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
				.messages(Message.builder().messageId("m1").receiptHandle("rh1").body(body).messageAttributes(attributes).build())
				.build());
	}

	@Test
	@DisplayName("1ª rodada (sem orçamento ainda): só calcula o orçamento inicial, sem gerar Pix")
	void deveCalcularOrcamentoInicialNaPrimeiraRodada() {
		mockReceiveSingleMessage("""
				{ "correlationId": null, "serviceOrderId": "os-1", "payerEmail": null, "customerDocument": null, "labors": [], "supplies": [] }
				""");
		when(budgetRepository.findByOrderId("os-1")).thenReturn(Optional.empty());
		when(budgetCalculationService.generateInitialBudget(eq("os-1"), any(), any(), any(), any()))
				.thenReturn(budgetComTotais());

		listener().poll();

		verify(budgetCalculationService).generateInitialBudget(eq("os-1"), any(), any(), any(), any());
		verifyNoInteractions(gateway, paymentRepository);
		verify(budgetEventPublisher).publish(argThatQrCodeIs(null));
		verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
	}

	@Test
	@DisplayName("2ª rodada (orçamento já existe): aprova os itens, gera o Pix e publica o QR Code")
	void deveGerarPixNaSegundaRodada() throws Exception {
		mockReceiveSingleMessage("""
				{ "correlationId": null, "serviceOrderId": "os-1", "payerEmail": "cliente@teste.com", "customerDocument": "12345678909",
				  "labors": [{ "laborId": "labor-1", "description": "Troca de oleo", "amount": 100.00, "situation": "APROVADO" }],
				  "supplies": [] }
				""");
		Budget existing = budgetComTotais();
		when(budgetRepository.findByOrderId("os-1")).thenReturn(Optional.of(existing));
		when(budgetCalculationService.approveItems(eq("os-1"), any(), any())).thenReturn(existing);

		JsonNode order = MAPPER.readTree("""
				{ "id": "order-abc", "transactions": { "payments": [ {
				  "payment_method": { "qr_code": "copia-cola", "qr_code_base64": "base64img", "ticket_url": "https://mp.com/ticket/abc" }
				} ] } }
				""");
		when(gateway.createPixOrder(any(), anyString(), eq("cliente@teste.com"), eq("12345678909"))).thenReturn(order);

		listener().poll();

		verify(budgetCalculationService).approveItems(eq("os-1"), any(), any());
		verify(gateway).createPixOrder(eq(existing.getTotalBudgetAmount()), anyString(), eq("cliente@teste.com"), eq("12345678909"));
		verify(paymentRepository).save(any());
		verify(budgetEventPublisher).publish(argThatQrCodeIs("copia-cola"));
		verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
	}

	@Test
	@DisplayName("Le o correlationId do message attribute da SQS (nao do corpo), igual a AbstractSqsQueueListener do os-service")
	void deveLerCorrelationIdDoMessageAttributeEmVezDoCorpo() {
		mockReceiveSingleMessage(
				"""
				{ "correlationId": null, "serviceOrderId": "os-1", "payerEmail": null, "customerDocument": null, "labors": [], "supplies": [] }
				""",
				Map.of("correlationId", MessageAttributeValue.builder().dataType("String").stringValue("corr-abc").build()));
		when(budgetRepository.findByOrderId("os-1")).thenReturn(Optional.empty());
		when(budgetCalculationService.generateInitialBudget(eq("os-1"), any(), any(), any(), any()))
				.thenReturn(budgetComTotais());

		listener().poll();

		verify(budgetEventPublisher).publish(argThatCorrelationIdIs("corr-abc"));
	}

	private static BudgetGeneratedEvent argThatQrCodeIs(String qrCode) {
		return org.mockito.ArgumentMatchers.argThat(evt -> evt != null && java.util.Objects.equals(evt.qrCode(), qrCode));
	}

	private static BudgetGeneratedEvent argThatCorrelationIdIs(String correlationId) {
		return org.mockito.ArgumentMatchers.argThat(evt -> evt != null && java.util.Objects.equals(evt.correlationId(), correlationId));
	}

}
