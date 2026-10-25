package br.com.officyna.billing.infrastructure.persistence.jpa.gateway;

import br.com.officyna.billing.infrastructure.persistence.jpa.entity.PaymentJpaEntity;
import br.com.officyna.billing.infrastructure.persistence.jpa.repository.PaymentJpaRepository;
import br.com.officyna.billing.payment.domain.Payment;
import br.com.officyna.billing.payment.domain.PaymentMethod;
import br.com.officyna.billing.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryGatewayTest {

	@Mock
	private PaymentJpaRepository jpaRepository;

	private PaymentRepositoryGateway gateway() {
		return new PaymentRepositoryGateway(jpaRepository);
	}

	@Test
	@DisplayName("Deve salvar e mapear o pagamento de volta pro domínio")
	void deveSalvarPagamento() {
		when(jpaRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

		Payment payment = Payment.builder()
				.id(UUID.randomUUID())
				.budgetId(UUID.randomUUID())
				.orderId("os-1")
				.externalPaymentId("order-123")
				.paymentMethod(PaymentMethod.PIX)
				.status(PaymentStatus.WAITING_PAYMENT)
				.build();

		Payment saved = gateway().save(payment);

		assertThat(saved.getOrderId()).isEqualTo("os-1");
		assertThat(saved.getStatus()).isEqualTo(PaymentStatus.WAITING_PAYMENT);
	}

	@Test
	@DisplayName("Deve buscar pagamento pelo id externo do Mercado Pago")
	void deveBuscarPorExternalPaymentId() {
		PaymentJpaEntity entity = PaymentJpaEntity.builder()
				.id(UUID.randomUUID())
				.budgetId(UUID.randomUUID())
				.orderId("os-1")
				.externalPaymentId("order-123")
				.paymentMethod(PaymentMethod.PIX)
				.status(PaymentStatus.WAITING_PAYMENT)
				.build();
		when(jpaRepository.findByExternalPaymentId("order-123")).thenReturn(Optional.of(entity));

		Optional<Payment> found = gateway().findByExternalPaymentId("order-123");

		assertThat(found).isPresent();
		assertThat(found.get().getOrderId()).isEqualTo("os-1");
	}

	@Test
	@DisplayName("Deve buscar pagamento pela OS")
	void deveBuscarPorOrderId() {
		when(jpaRepository.findByOrderId("os-1")).thenReturn(Optional.empty());

		assertThat(gateway().findByOrderId("os-1")).isEmpty();
	}

}
