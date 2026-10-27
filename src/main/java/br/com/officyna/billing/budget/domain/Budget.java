package br.com.officyna.billing.budget.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Budget {

	private final UUID id;
	private final String orderId;
	private final UUID correlationId;
	private final String customerDocument;
	private final Instant createdAt;
	private final BigDecimal totalLaborsAmount;
	private final BigDecimal totalSupplyAmount;
	private final BigDecimal totalTaxesAmount;
	private final BigDecimal totalBudgetAmount;
	private final BigDecimal issAmount;
	private final BigDecimal icmsAmount;
	private final BudgetStatus status;
	private final List<BudgetItem> items;

}
