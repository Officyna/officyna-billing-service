package br.com.officyna.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(String apiBaseUrl, String accessToken, String webhookSecret) {
}
