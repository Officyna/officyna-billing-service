package br.com.officyna.billing.payment.api;

import br.com.officyna.billing.payment.api.resources.WebhookNotification;
import br.com.officyna.billing.payment.domain.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único endpoint REST que não é síncrono-por-necessidade-de-negócio: o Mercado Pago chama essa
 * URL direto pela internet, então precisa continuar sendo HTTP (não dá pra trocar por
 * mensageria — é o provedor externo quem decide como notifica).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/webhook")
	public ResponseEntity<Void> webhook(
			@RequestHeader("x-signature") String xSignature,
			@RequestHeader("x-request-id") String xRequestId,
			@RequestParam(value = "data.id", required = false) String dataIdQueryParam,
			@RequestBody WebhookNotification notification) {
		String dataId = dataIdQueryParam != null ? dataIdQueryParam
				: notification.data() != null ? notification.data().id() : null;
		boolean accepted = paymentService.handleWebhook(xSignature, xRequestId, notification.type(), dataId);
		return accepted ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
	}

}
