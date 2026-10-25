package br.com.officyna.billing.payment.api.resources;

import java.math.BigDecimal;

public record CheckoutResponse(String serviceOrderId, String status, String qrCode, String qrCodeBase64, BigDecimal totalAmount) {
}
