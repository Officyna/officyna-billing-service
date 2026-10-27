package br.com.officyna.billing.infrastructure.security;

import br.com.officyna.billing.infrastructure.config.MercadoPagoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureValidatorTest {

	private static final String SECRET = "test-secret";

	private WebhookSignatureValidator validator;

	@BeforeEach
	void setUp() {
		validator = new WebhookSignatureValidator(new MercadoPagoProperties("https://api.mercadopago.com", "token", SECRET));
	}

	@Test
	@DisplayName("Deve aceitar assinatura calculada corretamente com o manifest oficial")
	void deveAceitarAssinaturaValida() throws Exception {
		String dataId = "123456789";
		String requestId = "req-abc";
		String ts = "1700000000";
		String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
		String v1 = hmacSha256Hex(manifest, SECRET);
		String xSignature = "ts=" + ts + ",v1=" + v1;

		boolean valid = validator.isValid(xSignature, requestId, dataId);

		assertThat(valid).isTrue();
	}

	@Test
	@DisplayName("Deve rejeitar assinatura com hash incorreto")
	void deveRejeitarAssinaturaComHashIncorreto() {
		String xSignature = "ts=1700000000,v1=hashinvalido";

		boolean valid = validator.isValid(xSignature, "req-abc", "123456789");

		assertThat(valid).isFalse();
	}

	@Test
	@DisplayName("Deve rejeitar quando o header x-signature estiver ausente")
	void deveRejeitarQuandoHeaderAusente() {
		boolean valid = validator.isValid(null, "req-abc", "123456789");

		assertThat(valid).isFalse();
	}

	private String hmacSha256Hex(String data, String secret) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(hash);
	}

}
