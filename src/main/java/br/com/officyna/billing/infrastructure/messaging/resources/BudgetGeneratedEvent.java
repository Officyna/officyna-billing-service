package br.com.officyna.billing.infrastructure.messaging.resources;

import java.math.BigDecimal;

public record BudgetGeneratedEvent(
		String correlationId,
		String serviceOrderId,
		Totals totals,
		TaxDetails taxDetails
) {

	public record Totals(BigDecimal totalLaborsAmount, BigDecimal totalSupplyAmount,
			BigDecimal totalTaxesAmount, BigDecimal totalBudgetAmount) {
	}

	public record TaxDetails(BigDecimal iss, BigDecimal icms, BigDecimal pisCofins) {
	}

}
