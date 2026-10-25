package br.com.officyna.billing.infrastructure.persistence.jpa.entity;

import br.com.officyna.billing.payment.domain.PaymentMethod;
import br.com.officyna.billing.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentJpaEntity {

	@Id
	private UUID id;

	@Column(name = "budget_id", nullable = false)
	private UUID budgetId;

	@Column(name = "order_id", nullable = false)
	private String orderId;

	@Column(name = "external_payment_id")
	private String externalPaymentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false)
	private PaymentMethod paymentMethod;

	@Column(name = "qr_code", columnDefinition = "TEXT")
	private String qrCode;

	@Column(name = "qr_code_base64", columnDefinition = "TEXT")
	private String qrCodeBase64;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(name = "paid_amount")
	private BigDecimal paidAmount;

	@Column(name = "paid_at")
	private Instant paidAt;

}
