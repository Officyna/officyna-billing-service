package br.com.officyna.billing.infrastructure.security;

import br.com.officyna.billing.infrastructure.config.MercadoPagoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the x-signature header Mercado Pago sends on webhook calls.
 * Manifest template: "id:{data.id};request-id:{x-request-id};ts:{ts};" — data.id entra em
 * minusculo, conforme a documentacao do MP (confirmado contra uma entrega real de webhook do
 * topico "Order": o id vem em maiuscula na URL, mas o HMAC foi assinado com ele em minusculo).
 */
@Component
public class WebhookSignatureValidator {

	private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Pattern SIGNATURE_PART = Pattern.compile("(ts|v1)=([^,]+)");

	private final String webhookSecret;

	public WebhookSignatureValidator(MercadoPagoProperties properties) {
		this.webhookSecret = properties.webhookSecret();
	}

	public boolean isValid(String xSignature, String xRequestId, String dataId) {
		if (xSignature == null || xRequestId == null || dataId == null || webhookSecret == null || webhookSecret.isBlank()) {
			return false;
		}
		Map<String, String> parts = parseSignatureHeader(xSignature);
		String ts = parts.get("ts");
		String v1 = parts.get("v1");
		if (ts == null || v1 == null) {
			return false;
		}
		String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";
		String computed = hmacSha256Hex(manifest, webhookSecret);
		boolean valid = computed.equalsIgnoreCase(v1);

		if (!valid) {
			log.warn("Assinatura invalida. dataId={} ts={} v1Recebido={} v1Calculado={}", dataId, ts, v1, computed);
		}

		return valid;
	}

	private Map<String, String> parseSignatureHeader(String xSignature) {
		Matcher matcher = SIGNATURE_PART.matcher(xSignature);
		Map<String, String> parts = new java.util.HashMap<>();
		while (matcher.find()) {
			parts.put(matcher.group(1), matcher.group(2));
		}
		return parts;
	}

	private String hmacSha256Hex(String data, String secret) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to compute webhook signature", e);
		}
	}

}
