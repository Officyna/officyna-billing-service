package br.com.officyna.billing.payment.domain;

import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import br.com.officyna.billing.infrastructure.messaging.publisher.OrderPaidTopicPublisher;
import br.com.officyna.billing.infrastructure.messaging.publisher.RejectionTopicPublisher;
import br.com.officyna.billing.infrastructure.messaging.resources.OrderPaidEvent;
import br.com.officyna.billing.infrastructure.messaging.resources.RejectionEvent;
import br.com.officyna.billing.infrastructure.security.WebhookSignatureValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final ObjectMapper MAPPER = new tools.jackson.databind.json.JsonMapper();

	@Mock
	private MercadoPagoOrderGateway gateway;

	@Mock
	private WebhookSignatureValidator signatureValidator;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private OrderPaidTopicPublisher orderPaidTopicPublisher;

	@Mock
	private RejectionTopicPublisher rejectionTopicPublisher;

	private PaymentService paymentService() {
		return new PaymentService(gateway, signatureValidator, paymentRepository, orderPaidTopicPublisher, rejectionTopicPublisher);
	}

	@Test
	@DisplayName("Deve rejeitar webhook com assinatura inválida sem consultar o pagamento")
	void deveRejeitarWebhookComAssinaturaInvalida() {
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

		boolean accepted = paymentService().handleWebhook("ts=1,v1=x", "req-1", "payment", "order-123");

		assertThat(accepted).isFalse();
		verifyNoInteractions(gateway, paymentRepository, orderPaidTopicPublisher);
	}

	@Test
	@DisplayName("Deve ignorar webhook válido referente a um pagamento desconhecido")
	void deveIgnorarWebhookDePagamentoDesconhecido() {
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
		when(paymentRepository.findByExternalPaymentId("order-123")).thenReturn(Optional.empty());

		boolean accepted = paymentService().handleWebhook("ts=1,v1=x", "req-1", "payment", "order-123");

		assertThat(accepted).isTrue();
		verifyNoInteractions(gateway, orderPaidTopicPublisher);
	}

	@Test
	@DisplayName("Deve marcar pagamento como PAID e publicar eventOrderPaid quando o Mercado Pago confirma")
	void deveMarcarComoPagoEPublicarEvento() throws Exception {
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
		Payment existing = Payment.builder()
				.id(UUID.randomUUID())
				.budgetId(UUID.randomUUID())
				.orderId("os-1")
				.externalPaymentId("order-123")
				.paymentMethod(PaymentMethod.PIX)
				.status(PaymentStatus.WAITING_PAYMENT)
				.build();
		when(paymentRepository.findByExternalPaymentId("order-123")).thenReturn(Optional.of(existing));
		JsonNode order = MAPPER.readTree("""
				{ "status": "processed", "total_amount": "350.00" }
				""");
		when(gateway.getOrder("order-123")).thenReturn(order);
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		boolean accepted = paymentService().handleWebhook("ts=1,v1=x", "req-1", "payment", "order-123");

		assertThat(accepted).isTrue();
		verify(paymentRepository).save(argThatStatusIs(PaymentStatus.PAID));
		verify(orderPaidTopicPublisher).publish(any(OrderPaidEvent.class));
	}

	@Test
	@DisplayName("Não deve publicar eventOrderPaid quando o pagamento ainda está pendente")
	void naoDevePublicarEventoQuandoAindaPendente() throws Exception {
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
		Payment existing = Payment.builder()
				.id(UUID.randomUUID())
				.budgetId(UUID.randomUUID())
				.orderId("os-1")
				.externalPaymentId("order-123")
				.paymentMethod(PaymentMethod.PIX)
				.status(PaymentStatus.WAITING_PAYMENT)
				.build();
		when(paymentRepository.findByExternalPaymentId("order-123")).thenReturn(Optional.of(existing));
		JsonNode order = MAPPER.readTree("""
				{ "status": "action_required", "total_amount": "350.00" }
				""");
		when(gateway.getOrder("order-123")).thenReturn(order);
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		boolean accepted = paymentService().handleWebhook("ts=1,v1=x", "req-1", "payment", "order-123");

		assertThat(accepted).isTrue();
		verifyNoInteractions(orderPaidTopicPublisher);
	}

	@Test
	@DisplayName("Deve marcar pagamento como FAILED e publicar eventTopicRejeicao quando o Mercado Pago recusa")
	void deveMarcarComoFailedEPublicarRejeicao() throws Exception {
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
		Payment existing = Payment.builder()
				.id(UUID.randomUUID())
				.budgetId(UUID.randomUUID())
				.orderId("os-1")
				.externalPaymentId("order-123")
				.paymentMethod(PaymentMethod.PIX)
				.status(PaymentStatus.WAITING_PAYMENT)
				.build();
		when(paymentRepository.findByExternalPaymentId("order-123")).thenReturn(Optional.of(existing));
		JsonNode order = MAPPER.readTree("""
				{ "status": "rejected", "total_amount": "350.00" }
				""");
		when(gateway.getOrder("order-123")).thenReturn(order);
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		boolean accepted = paymentService().handleWebhook("ts=1,v1=x", "req-1", "payment", "order-123");

		assertThat(accepted).isTrue();
		verify(paymentRepository).save(argThatStatusIs(PaymentStatus.FAILED));
		verify(rejectionTopicPublisher).publish(any(RejectionEvent.class));
		verifyNoInteractions(orderPaidTopicPublisher);
	}

	@Test
	@DisplayName("Deve estornar via Mercado Pago quando a OS é recusada e o pagamento já estava PAID")
	void deveEstornarQuandoOsRecusadaComPagamentoJaConfirmado() {
		Payment existing = Payment.builder()
				.id(UUID.randomUUID())
				.budgetId(UUID.randomUUID())
				.orderId("os-1")
				.externalPaymentId("order-123")
				.paymentMethod(PaymentMethod.PIX)
				.status(PaymentStatus.PAID)
				.build();
		when(paymentRepository.findByOrderId("os-1")).thenReturn(Optional.of(existing));
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		paymentService().handleServiceOrderRecused("os-1");

		verify(gateway).refundOrder("order-123");
		verify(paymentRepository).save(argThatStatusIs(PaymentStatus.REFUNDED));
	}

	@Test
	@DisplayName("Não deve estornar quando a OS é recusada mas o pagamento nunca foi confirmado")
	void naoDeveEstornarQuandoOsRecusadaSemPagamentoConfirmado() {
		Payment existing = Payment.builder()
				.id(UUID.randomUUID())
				.budgetId(UUID.randomUUID())
				.orderId("os-1")
				.externalPaymentId("order-123")
				.paymentMethod(PaymentMethod.PIX)
				.status(PaymentStatus.WAITING_PAYMENT)
				.build();
		when(paymentRepository.findByOrderId("os-1")).thenReturn(Optional.of(existing));
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		paymentService().handleServiceOrderRecused("os-1");

		verifyNoInteractions(gateway);
		verify(paymentRepository).save(argThatStatusIs(PaymentStatus.FAILED));
	}

	@Test
	@DisplayName("Deve ignorar eventServiceOrderRecused de uma OS sem pagamento algum")
	void deveIgnorarRecusaDeOsSemPagamento() {
		when(paymentRepository.findByOrderId("os-sem-pagamento")).thenReturn(Optional.empty());

		paymentService().handleServiceOrderRecused("os-sem-pagamento");

		verifyNoInteractions(gateway);
		verify(paymentRepository, org.mockito.Mockito.never()).save(any());
	}

	private static Payment argThatStatusIs(PaymentStatus status) {
		return org.mockito.ArgumentMatchers.argThat(payment -> payment != null && payment.getStatus() == status);
	}

}
