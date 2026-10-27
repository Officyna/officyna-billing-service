package br.com.officyna.billing.infrastructure.persistence.jpa.repository;

import br.com.officyna.billing.infrastructure.persistence.jpa.entity.BudgetJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BudgetJpaRepository extends JpaRepository<BudgetJpaEntity, UUID> {

	Optional<BudgetJpaEntity> findByOrderId(String orderId);

}
