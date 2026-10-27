package br.com.officyna.billing.payment.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Payment {

	private final UUID id;
	private final UUID budgetId;
	private final String orderId;
	private final String externalPaymentId;
	private final PaymentMethod paymentMethod;
	private final String qrCode;
	private final String qrCodeBase64;
	private final PaymentStatus status;
	private final BigDecimal paidAmount;
	private final Instant paidAt;

}
