package br.com.officyna.billing.payment.api.resources;

import br.com.officyna.billing.payment.domain.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code cardToken}/{@code cardBrand}/{@code installments} não apareciam no documento original
 * (só tinha o exemplo de Pix) — são extensões necessárias pra Checkout Transparente de cartão
 * funcionar (a tokenização acontece em quem chama, billing-service nunca vê o cartão cru).
 */
public record CheckoutRequest(
		@NotBlank String correlationId,
		@NotBlank String serviceOrderId,
		@NotNull PaymentMethod paymentMethod,
		@NotNull @Email String payerEmail,
		List<ApprovedLabor> approvedLabors,
		List<ApprovedSupply> approvedSupplies,
		String cardToken,
		String cardBrand,
		Integer installments
) {

	public record ApprovedLabor(String laborId, BigDecimal amount) {
	}

	public record ApprovedSupply(String supplyId, BigDecimal amount) {
	}

}
