package br.com.officyna.billing.budget.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BudgetCalculationService {

	private final BudgetRepository budgetRepository;
	private final BigDecimal issRate;
	private final BigDecimal icmsRate;

	public BudgetCalculationService(BudgetRepository budgetRepository,
			@Value("${billing.tax.iss-rate}") BigDecimal issRate,
			@Value("${billing.tax.icms-rate}") BigDecimal icmsRate) {
		this.budgetRepository = budgetRepository;
		this.issRate = issRate;
		this.icmsRate = icmsRate;
	}

	/**
	 * Primeiro cálculo, disparado por {@code eventGeneratePayment} logo após o diagnóstico.
	 * Todos os itens entram como {@link ItemSituation#PENDING_APPROVAL} — nada foi aprovado
	 * pelo cliente ainda.
	 */
	public Budget generateInitialBudget(String orderId, UUID correlationId, String customerDocument,
			List<LineItemInput> labors, List<LineItemInput> supplies) {
		List<BudgetItem> items = buildItems(labors, ItemType.LABOR, ItemSituation.PENDING_APPROVAL);
		items.addAll(buildItems(supplies, ItemType.SUPPLY, ItemSituation.PENDING_APPROVAL));

		Budget budget = totalize(Budget.builder()
				.id(UUID.randomUUID())
				.orderId(orderId)
				.correlationId(correlationId)
				.customerDocument(customerDocument)
				.status(BudgetStatus.PENDING_APPROVAL)
				.createdAt(java.time.Instant.now())
				.items(items)
				.build(), items);

		return budgetRepository.save(budget);
	}

	/**
	 * Recálculo disparado pelo {@code POST /checkout/create}, só com os itens que o cliente
	 * aprovou. O que não veio nas listas de aprovados vira {@link ItemSituation#REJECTED} —
	 * preserva o histórico do item em vez de apagar.
	 */
	public Budget approveItems(String orderId, Set<String> approvedLaborIds, Set<String> approvedSupplyIds) {
		Budget budget = budgetRepository.findByOrderId(orderId)
				.orElseThrow(() -> new IllegalStateException("Nenhum orçamento encontrado para a OS " + orderId));

		List<BudgetItem> updatedItems = budget.getItems().stream()
				.map(item -> {
					Set<String> approvedIds = item.getItemType() == ItemType.LABOR ? approvedLaborIds : approvedSupplyIds;
					ItemSituation situation = approvedIds.contains(item.getExternalItemId())
							? ItemSituation.APPROVED
							: ItemSituation.REJECTED;
					return item.toBuilder().situation(situation).build();
				})
				.toList();

		List<BudgetItem> approvedItems = updatedItems.stream()
				.filter(item -> item.getSituation() == ItemSituation.APPROVED)
				.toList();

		Budget updated = totalize(budget.toBuilder()
				.items(updatedItems)
				.status(BudgetStatus.APPROVED)
				.build(), approvedItems);

		return budgetRepository.save(updated);
	}

	private List<BudgetItem> buildItems(List<LineItemInput> inputs, ItemType type, ItemSituation situation) {
		return inputs.stream()
				.map(input -> BudgetItem.builder()
						.id(UUID.randomUUID())
						.externalItemId(input.externalItemId())
						.itemType(type)
						.description(input.description())
						.amount(input.amount())
						.situation(situation)
						.build())
				.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
	}

	private Budget totalize(Budget budget, List<BudgetItem> itemsToSum) {
		BigDecimal totalLabors = sum(itemsToSum, ItemType.LABOR);
		BigDecimal totalSupply = sum(itemsToSum, ItemType.SUPPLY);
		BigDecimal iss = totalLabors.multiply(issRate).setScale(2, RoundingMode.HALF_UP);
		BigDecimal icms = totalSupply.multiply(icmsRate).setScale(2, RoundingMode.HALF_UP);
		BigDecimal totalTaxes = iss.add(icms);
		BigDecimal totalBudget = totalLabors.add(totalSupply).add(totalTaxes);

		return budget.toBuilder()
				.totalLaborsAmount(totalLabors)
				.totalSupplyAmount(totalSupply)
				.issAmount(iss)
				.icmsAmount(icms)
				.totalTaxesAmount(totalTaxes)
				.totalBudgetAmount(totalBudget)
				.build();
	}

	private BigDecimal sum(List<BudgetItem> items, ItemType type) {
		return items.stream()
				.filter(item -> item.getItemType() == type)
				.map(BudgetItem::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
	}

}
