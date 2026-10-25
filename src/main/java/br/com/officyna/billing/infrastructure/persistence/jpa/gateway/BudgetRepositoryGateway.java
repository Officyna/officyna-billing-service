package br.com.officyna.billing.infrastructure.persistence.jpa.gateway;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetItem;
import br.com.officyna.billing.budget.domain.BudgetRepository;
import br.com.officyna.billing.infrastructure.persistence.jpa.entity.BudgetItemJpaEntity;
import br.com.officyna.billing.infrastructure.persistence.jpa.entity.BudgetJpaEntity;
import br.com.officyna.billing.infrastructure.persistence.jpa.repository.BudgetJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BudgetRepositoryGateway extends AbstractJpaRepositoryGateway<Budget, BudgetJpaEntity, BudgetJpaRepository>
		implements BudgetRepository {

	public BudgetRepositoryGateway(BudgetJpaRepository jpaRepository) {
		super(jpaRepository, BudgetRepositoryGateway::toJpaEntity, BudgetRepositoryGateway::toDomain);
	}

	@Override
	public Optional<Budget> findByOrderId(String orderId) {
		return execute("findByOrderId", () -> jpaRepository.findByOrderId(orderId).map(BudgetRepositoryGateway::toDomain));
	}

	private static BudgetJpaEntity toJpaEntity(Budget budget) {
		BudgetJpaEntity entity = BudgetJpaEntity.builder()
				.id(budget.getId())
				.orderId(budget.getOrderId())
				.correlationId(budget.getCorrelationId())
				.customerDocument(budget.getCustomerDocument())
				.totalLaborsAmount(budget.getTotalLaborsAmount())
				.totalSupplyAmount(budget.getTotalSupplyAmount())
				.totalTaxesAmount(budget.getTotalTaxesAmount())
				.totalBudgetAmount(budget.getTotalBudgetAmount())
				.issAmount(budget.getIssAmount())
				.icmsAmount(budget.getIcmsAmount())
				.status(budget.getStatus())
				.createdAt(budget.getCreatedAt())
				.build();

		List<BudgetItemJpaEntity> items = budget.getItems().stream()
				.map(item -> toJpaItem(item, entity))
				.toList();
		entity.setItems(items);
		return entity;
	}

	private static BudgetItemJpaEntity toJpaItem(BudgetItem item, BudgetJpaEntity budget) {
		return BudgetItemJpaEntity.builder()
				.id(item.getId())
				.budget(budget)
				.externalItemId(item.getExternalItemId())
				.itemType(item.getItemType())
				.description(item.getDescription())
				.amount(item.getAmount())
				.situation(item.getSituation())
				.build();
	}

	private static Budget toDomain(BudgetJpaEntity entity) {
		return Budget.builder()
				.id(entity.getId())
				.orderId(entity.getOrderId())
				.correlationId(entity.getCorrelationId())
				.customerDocument(entity.getCustomerDocument())
				.totalLaborsAmount(entity.getTotalLaborsAmount())
				.totalSupplyAmount(entity.getTotalSupplyAmount())
				.totalTaxesAmount(entity.getTotalTaxesAmount())
				.totalBudgetAmount(entity.getTotalBudgetAmount())
				.issAmount(entity.getIssAmount())
				.icmsAmount(entity.getIcmsAmount())
				.status(entity.getStatus())
				.createdAt(entity.getCreatedAt())
				.items(entity.getItems().stream().map(BudgetRepositoryGateway::toDomainItem).toList())
				.build();
	}

	private static BudgetItem toDomainItem(BudgetItemJpaEntity entity) {
		return BudgetItem.builder()
				.id(entity.getId())
				.externalItemId(entity.getExternalItemId())
				.itemType(entity.getItemType())
				.description(entity.getDescription())
				.amount(entity.getAmount())
				.situation(entity.getSituation())
				.build();
	}

}
