package br.com.officyna.billing.payment.api;

import br.com.officyna.billing.payment.api.resources.CheckoutRequest;
import br.com.officyna.billing.payment.api.resources.CheckoutResponse;
import br.com.officyna.billing.payment.domain.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoint síncrono — chamado pelo os-service depois que o cliente aprova a O.S. Precisa
 * responder na hora porque quem chama precisa do QR Code/dado do pagamento imediatamente.
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

	private final CheckoutService checkoutService;

	public CheckoutController(CheckoutService checkoutService) {
		this.checkoutService = checkoutService;
	}

	@PostMapping("/create")
	public ResponseEntity<CheckoutResponse> create(@Valid @RequestBody CheckoutRequest request) {
		Set<String> approvedLaborIds = toIds(request.approvedLabors(), CheckoutRequest.ApprovedLabor::laborId);
		Set<String> approvedSupplyIds = toIds(request.approvedSupplies(), CheckoutRequest.ApprovedSupply::supplyId);

		CheckoutService.CheckoutResult result = checkoutService.checkout(
				request.serviceOrderId(), request.paymentMethod(), request.payerEmail(),
				approvedLaborIds, approvedSupplyIds,
				request.cardToken(), request.cardBrand(), request.installments());

		return ResponseEntity.ok(new CheckoutResponse(
				result.serviceOrderId(), result.status(), result.qrCode(), result.qrCodeBase64(), result.totalAmount()));
	}

	private <T> Set<String> toIds(List<T> items, java.util.function.Function<T, String> idExtractor) {
		return items == null ? Set.of() : items.stream().map(idExtractor).collect(Collectors.toSet());
	}

}
