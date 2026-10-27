package br.com.officyna.billing.budget.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class BudgetItem {

	private final UUID id;
	private final String externalItemId;
	private final ItemType itemType;
	private final String description;
	private final BigDecimal amount;
	private final ItemSituation situation;

}
