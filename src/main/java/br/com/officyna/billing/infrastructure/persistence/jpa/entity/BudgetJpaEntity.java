package br.com.officyna.billing.infrastructure.persistence.jpa.entity;

import br.com.officyna.billing.budget.domain.BudgetStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetJpaEntity {

	@Id
	private UUID id;

	@Column(name = "order_id", nullable = false)
	private String orderId;

	@Column(name = "correlation_id", nullable = false)
	private UUID correlationId;

	@Column(name = "customer_document")
	private String customerDocument;

	@Column(name = "total_labors_amount", nullable = false)
	private BigDecimal totalLaborsAmount;

	@Column(name = "total_supply_amount", nullable = false)
	private BigDecimal totalSupplyAmount;

	@Column(name = "total_taxes_amount", nullable = false)
	private BigDecimal totalTaxesAmount;

	@Column(name = "total_budget_amount", nullable = false)
	private BigDecimal totalBudgetAmount;

	@Column(name = "iss_amount", nullable = false)
	private BigDecimal issAmount;

	@Column(name = "icms_amount", nullable = false)
	private BigDecimal icmsAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BudgetStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Builder.Default
	@OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<BudgetItemJpaEntity> items = new java.util.ArrayList<>();

}
