package br.com.officyna.billing.payment.api;

import br.com.officyna.billing.payment.api.resources.CardCheckoutRequest;
import br.com.officyna.billing.payment.api.resources.CardCheckoutResponse;
import br.com.officyna.billing.payment.api.resources.PixCheckoutRequest;
import br.com.officyna.billing.payment.api.resources.PixCheckoutResponse;
import br.com.officyna.billing.payment.api.resources.WebhookNotification;
import br.com.officyna.billing.payment.domain.PaymentRecord;
import br.com.officyna.billing.payment.domain.PaymentService;
import br.com.officyna.billing.payment.domain.PaymentStatusStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

	private final PaymentService paymentService;
	private final PaymentStatusStore paymentStatusStore;

	public PaymentController(PaymentService paymentService, PaymentStatusStore paymentStatusStore) {
		this.paymentService = paymentService;
		this.paymentStatusStore = paymentStatusStore;
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<PaymentRecord> getPayment(@PathVariable String orderId) {
		return paymentStatusStore.findByOrderId(orderId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/checkout/pix")
	public ResponseEntity<PixCheckoutResponse> checkoutPix(@Valid @RequestBody PixCheckoutRequest request) {
		PaymentService.PixCheckoutResult result = paymentService.checkoutPix(request.amount(), request.payerEmail());
		return ResponseEntity.ok(new PixCheckoutResponse(
				result.orderId(), result.externalReference(), result.status(), result.qrCode(), result.qrCodeBase64()));
	}

	@PostMapping("/checkout/card")
	public ResponseEntity<CardCheckoutResponse> checkoutCard(@Valid @RequestBody CardCheckoutRequest request) {
		PaymentService.CardCheckoutResult result = paymentService.checkoutCard(
				request.amount(), request.payerEmail(), request.cardToken(), request.paymentMethodId(), request.installments());
		return ResponseEntity.ok(new CardCheckoutResponse(result.orderId(), result.externalReference(), result.status()));
	}

	@PostMapping("/webhook")
	public ResponseEntity<Void> webhook(
			@RequestHeader("x-signature") String xSignature,
			@RequestHeader("x-request-id") String xRequestId,
			@RequestParam(value = "data.id", required = false) String dataIdQueryParam,
			@RequestBody WebhookNotification notification) {
		// MP signs the data.id query param it appends to the webhook URL, not the JSON body's
		// data.id — the two happen to match in practice, but the query param is the one that
		// must match the x-signature manifest.
		String dataId = dataIdQueryParam != null ? dataIdQueryParam
				: notification.data() != null ? notification.data().id() : null;
		boolean accepted = paymentService.handleWebhook(xSignature, xRequestId, notification.type(), dataId);
		return accepted ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
	}

}
