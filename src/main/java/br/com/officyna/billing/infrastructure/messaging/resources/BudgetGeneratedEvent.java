package br.com.officyna.billing.infrastructure.messaging.resources;

import java.math.BigDecimal;

/**
 * {@code paymentLink}/{@code qrCode}/{@code qrCodeBase64} só vêm preenchidos na 2ª publicação
 * por OS — quando o listener já gerou o Pix de verdade no Mercado Pago (ver
 * {@link GeneratePaymentEvent}). Na 1ª publicação (cálculo inicial) ficam nulos.
 * {@code paymentLink} é a URL pra acompanhar/pagar (quem só precisa saber a situação do
 * pagamento usa esse); {@code qrCode} é o Pix copia-e-cola; {@code qrCodeBase64} é a imagem do
 * QR Code pronta pra exibir (só quem realmente mostra o QR precisa disso).
 */
public record BudgetGeneratedEvent(
		String correlationId,
		String serviceOrderId,
		Totals totals,
		TaxDetails taxDetails,
		String paymentLink,
		String qrCode,
		String qrCodeBase64
) {

	public record Totals(BigDecimal totalLaborsAmount, BigDecimal totalSupplyAmount,
			BigDecimal totalTaxesAmount, BigDecimal totalBudgetAmount) {
	}

	public record TaxDetails(BigDecimal iss, BigDecimal icms, BigDecimal pisCofins) {
	}

}
