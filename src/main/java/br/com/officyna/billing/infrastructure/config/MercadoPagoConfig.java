package br.com.officyna.billing.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class MercadoPagoConfig {

	@Bean
	public RestClient mercadoPagoRestClient(MercadoPagoProperties properties) {
		return RestClient.builder()
				.baseUrl(properties.apiBaseUrl())
				.defaultHeader("Authorization", "Bearer " + properties.accessToken())
				.build();
	}

}
