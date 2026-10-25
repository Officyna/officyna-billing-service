package br.com.officyna.billing.infrastructure.persistence.jpa.repository;

import br.com.officyna.billing.infrastructure.persistence.jpa.entity.PaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

	Optional<PaymentJpaEntity> findByOrderId(String orderId);

	Optional<PaymentJpaEntity> findByExternalPaymentId(String externalPaymentId);

}
