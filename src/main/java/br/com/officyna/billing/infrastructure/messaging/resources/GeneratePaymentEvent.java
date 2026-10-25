package br.com.officyna.billing.infrastructure.messaging.resources;

import java.math.BigDecimal;
import java.util.List;

public record GeneratePaymentEvent(
		String correlationId,
		String serviceOrderId,
		List<LaborPayload> labors,
		List<SupplyPayload> supplies
) {

	public record LaborPayload(String laborId, String description, BigDecimal amount, String situation) {
	}

	public record SupplyPayload(String supplyId, String description, BigDecimal amount, String situation) {
	}

}
