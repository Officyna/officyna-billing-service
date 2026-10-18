package br.com.officyna.billing.payment.api.resources;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PixCheckoutRequest(
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotNull @Email String payerEmail
) {
}
