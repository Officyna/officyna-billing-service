package br.com.officyna.billing.payment.api.resources;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CardCheckoutRequest(
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotNull @Email String payerEmail,
		@NotBlank String cardToken,
		@NotBlank String paymentMethodId,
		@Min(1) int installments
) {
}
