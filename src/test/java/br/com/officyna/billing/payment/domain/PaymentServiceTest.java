package br.com.officyna.billing.payment.domain;

import br.com.officyna.billing.infrastructure.mercadopago.MercadoPagoOrderGateway;
import br.com.officyna.billing.infrastructure.security.WebhookSignatureValidator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private MercadoPagoOrderGateway gateway;

	@Mock
	private WebhookSignatureValidator signatureValidator;

	private PaymentStatusStore store;
	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		store = new PaymentStatusStore();
		paymentService = new PaymentService(gateway, signatureValidator, store);
	}

	@Test
	@DisplayName("Deve gerar cobrança Pix e salvar o status retornado pelo Mercado Pago")
	void deveGerarCobrancaPix() throws Exception {
		JsonNode order = MAPPER.readTree("""
				{
				  "id": "order-1",
				  "status": "action_required",
				  "transactions": { "payments": [ {
				    "payment_method": {
				      "qr_code": "copia-e-cola",
				      "qr_code_base64": "base64image"
				    }
				  } ] }
				}
				""");
		when(gateway.createPixOrder(any(), anyString(), eq("cliente@teste.com"))).thenReturn(order);

		PaymentService.PixCheckoutResult result = paymentService.checkoutPix(new BigDecimal("50.00"), "cliente@teste.com");

		assertThat(result.orderId()).isEqualTo("order-1");
		assertThat(result.status()).isEqualTo("action_required");
		assertThat(result.qrCode()).isEqualTo("copia-e-cola");
		assertThat(result.qrCodeBase64()).isEqualTo("base64image");
		assertThat(store.findByOrderId("order-1")).isPresent();
	}

	@Test
	@DisplayName("Deve rejeitar webhook com assinatura inválida sem consultar o pagamento")
	void deveRejeitarWebhookComAssinaturaInvalida() {
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

		boolean accepted = paymentService.handleWebhook("ts=1,v1=x", "req-1", "payment", "payment-123");

		assertThat(accepted).isFalse();
		verifyNoInteractions(gateway);
	}

	@Test
	@DisplayName("Deve atualizar o status do pedido ao receber webhook válido")
	void deveAtualizarStatusAoReceberWebhookValido() throws Exception {
		store.save(new PaymentRecord("order-1", "ext-ref-1", "pix", "action_required"));
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
		JsonNode order = MAPPER.readTree("""
				{ "id": "order-1", "status": "approved" }
				""");
		when(gateway.getOrder("order-1")).thenReturn(order);

		boolean accepted = paymentService.handleWebhook("ts=1,v1=x", "req-1", "payment", "order-1");

		assertThat(accepted).isTrue();
		assertThat(store.findByOrderId("order-1")).isPresent().get()
				.extracting(PaymentRecord::status).isEqualTo("approved");
		verify(gateway).getOrder("order-1");
	}

	@Test
	@DisplayName("Deve ignorar webhook válido referente a um pedido desconhecido")
	void deveIgnorarWebhookDePedidoDesconhecido() {
		when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);

		boolean accepted = paymentService.handleWebhook("ts=1,v1=x", "req-1", "payment", "order-desconhecido");

		assertThat(accepted).isTrue();
		verifyNoInteractions(gateway);
	}

}
