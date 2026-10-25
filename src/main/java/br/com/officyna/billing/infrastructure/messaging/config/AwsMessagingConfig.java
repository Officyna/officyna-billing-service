package br.com.officyna.billing.infrastructure.messaging.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;

/**
 * SQS/SNS via AWS SDK v2 puro — Spring Cloud AWS ainda não tem versão compatível com
 * Spring Boot 4 (ver README). {@code aws.endpoint} aponta para o LocalStack localmente;
 * vazio usa a AWS real.
 */
@Configuration
@EnableScheduling
public class AwsMessagingConfig {

	private static final Logger log = LoggerFactory.getLogger(AwsMessagingConfig.class);

	@Value("${aws.region}")
	private String region;

	@Value("${aws.endpoint}")
	private String endpoint;

	@Value("${aws.access-key}")
	private String accessKey;

	@Value("${aws.secret-key}")
	private String secretKey;

	@Bean
	public SqsClient sqsClient() {
		log.info("Configurando SqsClient region={} endpointOverride={}", region, endpoint);
		SqsClientBuilder builder = SqsClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
		if (endpoint != null && !endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint));
		}
		return builder.build();
	}

	@Bean
	public SnsClient snsClient() {
		log.info("Configurando SnsClient region={} endpointOverride={}", region, endpoint);
		SnsClientBuilder builder = SnsClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
		if (endpoint != null && !endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint));
		}
		return builder.build();
	}

	@Bean
	public ObjectMapper messagingObjectMapper() {
		return JsonMapper.builder().findAndAddModules().build();
	}

}
