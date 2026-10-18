package br.com.officyna.billing.payment.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for the real {@code payments} table — scoped to this POC, which
 * deliberately skips Postgres/Flyway to focus on validating the Mercado Pago integration.
 */
@Component
public class PaymentStatusStore {

	private final Map<String, PaymentRecord> records = new ConcurrentHashMap<>();

	public void save(PaymentRecord record) {
		records.put(record.orderId(), record);
	}

	public Optional<PaymentRecord> findByOrderId(String orderId) {
		return Optional.ofNullable(records.get(orderId));
	}

	public void updateStatus(String orderId, String status) {
		records.computeIfPresent(orderId, (id, record) -> record.withStatus(status));
	}

}
