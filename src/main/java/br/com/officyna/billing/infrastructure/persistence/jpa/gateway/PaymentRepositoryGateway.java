package br.com.officyna.billing.infrastructure.persistence.jpa.gateway;

import br.com.officyna.billing.infrastructure.persistence.jpa.entity.PaymentJpaEntity;
import br.com.officyna.billing.infrastructure.persistence.jpa.repository.PaymentJpaRepository;
import br.com.officyna.billing.payment.domain.Payment;
import br.com.officyna.billing.payment.domain.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentRepositoryGateway extends AbstractJpaRepositoryGateway<Payment, PaymentJpaEntity, PaymentJpaRepository>
		implements PaymentRepository {

	public PaymentRepositoryGateway(PaymentJpaRepository jpaRepository) {
		super(jpaRepository, PaymentRepositoryGateway::toJpaEntity, PaymentRepositoryGateway::toDomain);
	}

	@Override
	public Optional<Payment> findByOrderId(String orderId) {
		return execute("findByOrderId", () -> jpaRepository.findByOrderId(orderId).map(PaymentRepositoryGateway::toDomain));
	}

	@Override
	public Optional<Payment> findByExternalPaymentId(String externalPaymentId) {
		return execute("findByExternalPaymentId",
				() -> jpaRepository.findByExternalPaymentId(externalPaymentId).map(PaymentRepositoryGateway::toDomain));
	}

	private static PaymentJpaEntity toJpaEntity(Payment payment) {
		return PaymentJpaEntity.builder()
				.id(payment.getId())
				.budgetId(payment.getBudgetId())
				.orderId(payment.getOrderId())
				.externalPaymentId(payment.getExternalPaymentId())
				.paymentMethod(payment.getPaymentMethod())
				.qrCode(payment.getQrCode())
				.qrCodeBase64(payment.getQrCodeBase64())
				.status(payment.getStatus())
				.paidAmount(payment.getPaidAmount())
				.paidAt(payment.getPaidAt())
				.build();
	}

	private static Payment toDomain(PaymentJpaEntity entity) {
		return Payment.builder()
				.id(entity.getId())
				.budgetId(entity.getBudgetId())
				.orderId(entity.getOrderId())
				.externalPaymentId(entity.getExternalPaymentId())
				.paymentMethod(entity.getPaymentMethod())
				.qrCode(entity.getQrCode())
				.qrCodeBase64(entity.getQrCodeBase64())
				.status(entity.getStatus())
				.paidAmount(entity.getPaidAmount())
				.paidAt(entity.getPaidAt())
				.build();
	}

}
