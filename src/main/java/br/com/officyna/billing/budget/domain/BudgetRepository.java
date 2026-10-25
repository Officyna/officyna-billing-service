package br.com.officyna.billing.budget.domain;

import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository {

	Budget save(Budget budget);

	Optional<Budget> findById(UUID id);

	Optional<Budget> findByOrderId(String orderId);

}
