package br.com.officyna.billing.budget.domain;

import java.math.BigDecimal;

/**
 * Raw labor/supply line coming in from {@code eventGeneratePayment} — not yet persisted,
 * situation always starts as {@link ItemSituation#PENDING_APPROVAL}.
 */
public record LineItemInput(String externalItemId, String description, BigDecimal amount) {
}
