package br.com.officyna.billing.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring Boot 4.0.3 ainda não publicou um módulo de autoconfiguração para o Flyway (só existem
 * {@code spring-boot-hibernate}/{@code spring-boot-jpa}/{@code spring-boot-mongodb} — mesma
 * categoria de lacuna do Jackson 3 e do Spring Cloud AWS: o Boot 4 é recente demais e o
 * ecossistema ainda não acompanhou).
 *
 * <p>Em vez de depender da autoconfiguração, construímos o {@link DataSource} aqui mesmo e
 * rodamos a migração como efeito colateral da criação do bean — isso garante a ordem certa
 * (migração antes do JPA validar o schema) só com injeção de dependência normal, sem precisar
 * de {@code BeanFactoryPostProcessor}/truques de ordenação (uma tentativa anterior com
 * {@code @DependsOn} via post-processor esbarrou em resolução prematura do FactoryBean do JPA).
 * Definir nosso próprio bean {@code DataSource} também impede a autoconfiguração padrão de
 * criar outro (ela só age com {@code @ConditionalOnMissingBean(DataSource.class)}).
 */
@Configuration
public class FlywayConfig {

	@Bean
	public DataSource dataSource(@Value("${spring.datasource.url}") String url) {
		DataSource dataSource = DataSourceBuilder.create().url(url).build();
		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.load()
				.migrate();
		return dataSource;
	}

}
