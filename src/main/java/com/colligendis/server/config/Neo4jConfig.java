package com.colligendis.server.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

@Slf4j
@Configuration
public class Neo4jConfig {

	@Value("${spring.neo4j.uri}")
	private String uri;

	@Value("${spring.neo4j.authentication.username}")
	private String username;

	@Value("${spring.neo4j.authentication.password}")
	private String password;

	private Driver driver;

	@Bean
	public Driver neo4jDriver() {
		log.debug("Configuring Neo4j Driver with URI: {}", uri);

		// Configure connection pool to prevent "pending acquisition queue is full"
		// errors
		Config config = Config.builder()
				.withMaxConnectionPoolSize(50) // Reduced from 100 to better match actual needs
				.withMaxConnectionLifetime(1, TimeUnit.HOURS) // Connections live for 1 hour
				.withConnectionAcquisitionTimeout(120, TimeUnit.SECONDS) // Increase timeout for acquisition
				.withConnectionTimeout(30, TimeUnit.SECONDS) // Connection establishment timeout
				.withMaxTransactionRetryTime(30, TimeUnit.SECONDS) // Retry failed transactions
				.build();

		driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
		log.debug("Neo4j Driver configured successfully with max pool size: {}", config.maxConnectionPoolSize());
		return driver;
	}

	@PreDestroy
	public void closeDriver() {
		if (driver != null) {
			log.debug("Closing Neo4j Driver");
			driver.close();
		}
	}
}
