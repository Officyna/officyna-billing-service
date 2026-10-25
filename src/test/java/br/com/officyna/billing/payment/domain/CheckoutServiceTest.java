package br.com.officyna.billing.payment.domain;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetCalculationService;
import br.com.officyna.billing.budget.domain.BudgetStatus;
import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

	private static final JsonMapper MAPPER = new JsonMapper();

	@Mock
	private BudgetCalculationService budgetCalculationService;

	@Mock
	private MercadoPagoOrderGateway gateway;

	@Mock
	private PaymentRepository paymentRepository;

	private CheckoutService service() {
		return new CheckoutService(budgetCalculationService, gateway, paymentRepository);
	}

	private Budget approvedBudget() {
		return Budget.builder()
				.id(UUID.randomUUID())
				.orderId("os-1")
				.correlationId(UUID.randomUUID())
				.status(BudgetStatus.APPROVED)
				.totalBudgetAmount(new BigDecimal("350.00"))
				.items(java.util.List.of())
				.build();
	}

	@Test
	@DisplayName("Deve gerar checkout Pix usando o total recalculado do orçamento aprovado")
	void deveGerarCheckoutPix() throws Exception {
		when(budgetCalculationService.approveItems(anyString(), any(), any())).thenReturn(approvedBudget());
		JsonNode order = MAPPER.readTree("""
				{ "id": "order-abc", "transactions": { "payments": [ {
				  "payment_method": { "qr_code": "copia-cola", "qr_code_base64": "base64img" }
				} ] } }
				""");
		when(gateway.createPixOrder(any(), anyString(), anyString())).thenReturn(order);
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CheckoutService.CheckoutResult result = service().checkout("os-1", PaymentMethod.PIX, "cliente@teste.com",
				Set.of("lab_1"), Set.of(), null, null, null);

		assertThat(result.qrCode()).isEqualTo("copia-cola");
		assertThat(result.qrCodeBase64()).isEqualTo("base64img");
		assertThat(result.totalAmount()).isEqualByComparingTo("350.00");
		assertThat(result.status()).isEqualTo("WAITING_PAYMENT");
		verify(paymentRepository).save(any());
	}

	@Test
	@DisplayName("Deve gerar checkout de cartão de débito repassando o tipo correto pro gateway")
	void deveGerarCheckoutCartaoDebito() throws Exception {
		when(budgetCalculationService.approveItems(anyString(), any(), any())).thenReturn(approvedBudget());
		JsonNode order = MAPPER.readTree("""
				{ "id": "order-xyz", "status": "processed" }
				""");
		when(gateway.createCardOrder(any(), anyString(), anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(order);
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service().checkout("os-1", PaymentMethod.DEBIT_CARD, "cliente@teste.com",
				Set.of("lab_1"), Set.of(), "tok_123", "elo", 1);

		verify(gateway).createCardOrder(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq("tok_123"),
				org.mockito.ArgumentMatchers.eq("elo"), org.mockito.ArgumentMatchers.eq("debit_card"), org.mockito.ArgumentMatchers.eq(1));
	}

}
