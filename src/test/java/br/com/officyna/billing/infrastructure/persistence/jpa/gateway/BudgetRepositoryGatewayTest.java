package br.com.officyna.billing.infrastructure.persistence.jpa.gateway;

import br.com.officyna.billing.budget.domain.Budget;
import br.com.officyna.billing.budget.domain.BudgetItem;
import br.com.officyna.billing.budget.domain.BudgetStatus;
import br.com.officyna.billing.budget.domain.ItemSituation;
import br.com.officyna.billing.budget.domain.ItemType;
import br.com.officyna.billing.infrastructure.persistence.jpa.entity.BudgetJpaEntity;
import br.com.officyna.billing.infrastructure.persistence.jpa.repository.BudgetJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetRepositoryGatewayTest {

	@Mock
	private BudgetJpaRepository jpaRepository;

	private BudgetRepositoryGateway gateway() {
		return new BudgetRepositoryGateway(jpaRepository);
	}

	@Test
	@DisplayName("Deve salvar o orçamento mapeando os itens com a referência de volta pro pai")
	void deveSalvarOrcamentoComItens() {
		ArgumentCaptor<BudgetJpaEntity> captor = ArgumentCaptor.forClass(BudgetJpaEntity.class);
		when(jpaRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		Budget budget = Budget.builder()
				.id(UUID.randomUUID())
				.orderId("os-1")
				.correlationId(UUID.randomUUID())
				.createdAt(Instant.now())
				.status(BudgetStatus.PENDING_APPROVAL)
				.totalLaborsAmount(BigDecimal.TEN)
				.totalSupplyAmount(BigDecimal.ZERO)
				.totalTaxesAmount(BigDecimal.ONE)
				.totalBudgetAmount(new BigDecimal("11"))
				.issAmount(BigDecimal.ONE)
				.icmsAmount(BigDecimal.ZERO)
				.items(List.of(BudgetItem.builder()
						.id(UUID.randomUUID())
						.externalItemId("lab_1")
						.itemType(ItemType.LABOR)
						.description("Troca de óleo")
						.amount(BigDecimal.TEN)
						.situation(ItemSituation.PENDING_APPROVAL)
						.build()))
				.build();

		Budget saved = gateway().save(budget);

		assertThat(saved.getOrderId()).isEqualTo("os-1");
		assertThat(saved.getItems()).hasSize(1);
		BudgetJpaEntity persistedEntity = captor.getValue();
		assertThat(persistedEntity.getItems()).hasSize(1);
		assertThat(persistedEntity.getItems().get(0).getBudget()).isSameAs(persistedEntity);
	}

	@Test
	@DisplayName("Deve buscar orçamento por order id")
	void deveBuscarPorOrderId() {
		BudgetJpaEntity entity = BudgetJpaEntity.builder()
				.id(UUID.randomUUID())
				.orderId("os-1")
				.correlationId(UUID.randomUUID())
				.createdAt(Instant.now())
				.status(BudgetStatus.APPROVED)
				.totalLaborsAmount(BigDecimal.ZERO)
				.totalSupplyAmount(BigDecimal.ZERO)
				.totalTaxesAmount(BigDecimal.ZERO)
				.totalBudgetAmount(BigDecimal.ZERO)
				.issAmount(BigDecimal.ZERO)
				.icmsAmount(BigDecimal.ZERO)
				.items(List.of())
				.build();
		when(jpaRepository.findByOrderId("os-1")).thenReturn(Optional.of(entity));

		Optional<Budget> found = gateway().findByOrderId("os-1");

		assertThat(found).isPresent();
		assertThat(found.get().getStatus()).isEqualTo(BudgetStatus.APPROVED);
	}

	@Test
	@DisplayName("Deve retornar vazio quando não encontrar orçamento pelo id")
	void deveRetornarVazioPorId() {
		when(jpaRepository.findById(any())).thenReturn(Optional.empty());

		assertThat(gateway().findById(UUID.randomUUID())).isEmpty();
	}

}
