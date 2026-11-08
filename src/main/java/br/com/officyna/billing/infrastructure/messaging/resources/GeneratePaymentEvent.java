package br.com.officyna.billing.infrastructure.messaging.resources;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code payerEmail}/{@code customerDocument} só são obrigatórios na 2ª publicação dessa fila
 * por OS (quando o orçamento já existe e o cliente confirmou os itens finais) — é nesse momento
 * que o listener gera o Pix de verdade no Mercado Pago, que exige e-mail e CPF do pagador. Na
 * 1ª publicação (cálculo inicial, logo após o diagnóstico) podem vir nulos, ainda não precisa.
 */
public record GeneratePaymentEvent(
		String correlationId,
		String serviceOrderId,
		String payerEmail,
		String customerDocument,
		List<LaborPayload> labors,
		List<SupplyPayload> supplies
) {

	public record LaborPayload(String laborId, String description, BigDecimal amount, String situation) {
	}

	public record SupplyPayload(String supplyId, String description, BigDecimal amount, String situation) {
	}

}
