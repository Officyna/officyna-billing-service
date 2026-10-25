package br.com.officyna.billing.budget.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetCalculationServiceTest {

	@Mock
	private BudgetRepository budgetRepository;

	private BudgetCalculationService service() {
		return new BudgetCalculationService(budgetRepository, new BigDecimal("0.05"), new BigDecimal("0.12"));
	}

	@Test
	@DisplayName("Deve calcular totais e impostos do orçamento inicial com todos os itens pendentes")
	void deveCalcularOrcamentoInicial() {
		when(budgetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		BudgetCalculationService service = service();

		Budget budget = service.generateInitialBudget(
				"os-1", UUID.randomUUID(), "12345678900",
				List.of(new LineItemInput("lab_1", "Troca de pastilha", new BigDecimal("150.00"))),
				List.of(new LineItemInput("sup_1", "Jogo de pastilhas", new BigDecimal("180.00"))));

		assertThat(budget.getTotalLaborsAmount()).isEqualByComparingTo("150.00");
		assertThat(budget.getTotalSupplyAmount()).isEqualByComparingTo("180.00");
		assertThat(budget.getIssAmount()).isEqualByComparingTo("7.50");
		assertThat(budget.getIcmsAmount()).isEqualByComparingTo("21.60");
		assertThat(budget.getTotalTaxesAmount()).isEqualByComparingTo("29.10");
		assertThat(budget.getTotalBudgetAmount()).isEqualByComparingTo("359.10");
		assertThat(budget.getStatus()).isEqualTo(BudgetStatus.PENDING_APPROVAL);
		assertThat(budget.getItems()).hasSize(2)
				.allSatisfy(item -> assertThat(item.getSituation()).isEqualTo(ItemSituation.PENDING_APPROVAL));
	}

	@Test
	@DisplayName("Deve recalcular só com os itens aprovados, marcando o resto como rejeitado")
	void deveRecalcularSoComItensAprovados() {
		Budget existing = Budget.builder()
				.id(UUID.randomUUID())
				.orderId("os-1")
				.correlationId(UUID.randomUUID())
				.createdAt(java.time.Instant.now())
				.status(BudgetStatus.PENDING_APPROVAL)
				.items(List.of(
						BudgetItem.builder().id(UUID.randomUUID()).externalItemId("lab_1").itemType(ItemType.LABOR)
								.description("Troca de pastilha").amount(new BigDecimal("150.00")).situation(ItemSituation.PENDING_APPROVAL).build(),
						BudgetItem.builder().id(UUID.randomUUID()).externalItemId("lab_2").itemType(ItemType.LABOR)
								.description("Alinhamento").amount(new BigDecimal("80.00")).situation(ItemSituation.PENDING_APPROVAL).build(),
						BudgetItem.builder().id(UUID.randomUUID()).externalItemId("sup_1").itemType(ItemType.SUPPLY)
								.description("Jogo de pastilhas").amount(new BigDecimal("180.00")).situation(ItemSituation.PENDING_APPROVAL).build()))
				.build();
		when(budgetRepository.findByOrderId("os-1")).thenReturn(Optional.of(existing));
		ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
		when(budgetRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		Budget updated = service().approveItems("os-1", Set.of("lab_1"), Set.of("sup_1"));

		assertThat(updated.getTotalLaborsAmount()).isEqualByComparingTo("150.00");
		assertThat(updated.getTotalSupplyAmount()).isEqualByComparingTo("180.00");
		assertThat(updated.getStatus()).isEqualTo(BudgetStatus.APPROVED);

		var itemsByExternalId = updated.getItems().stream()
				.collect(java.util.stream.Collectors.toMap(BudgetItem::getExternalItemId, BudgetItem::getSituation));
		assertThat(itemsByExternalId.get("lab_1")).isEqualTo(ItemSituation.APPROVED);
		assertThat(itemsByExternalId.get("lab_2")).isEqualTo(ItemSituation.REJECTED);
		assertThat(itemsByExternalId.get("sup_1")).isEqualTo(ItemSituation.APPROVED);
	}

	@Test
	@DisplayName("Deve lançar exceção ao aprovar itens de uma OS sem orçamento gerado")
	void deveLancarExcecaoQuandoOrcamentoNaoExiste() {
		when(budgetRepository.findByOrderId("os-inexistente")).thenReturn(Optional.empty());

		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> service().approveItems("os-inexistente", Set.of(), Set.of()));
	}

}
