package com.colligendis.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/public/denominations")
@RequiredArgsConstructor
public class DenominationController {

	private final Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	@GetMapping
	public Mono<List<DenominationResponse>> getDenominationsByCurrency(
			@RequestParam(name = "currency", required = false) String currency) {
		if (currency == null || currency.trim().isEmpty()) {
			return Mono.just(List.of());
		}

		final String normalized = currency.trim().toLowerCase();
		final String cypher = """
				MATCH (d:DENOMINATION)-[:UNDER_CURRENCY]->(c:CURRENCY)
				WHERE toLower(coalesce(c.name, '')) = $currency
				   OR toLower(coalesce(c.fullName, '')) = $currency
				   OR toLower(coalesce(c.nid, '')) = $currency
				WITH d
				WHERE coalesce(d.nid, d.name, d.fullName, toString(d.numericValue)) IS NOT NULL
				RETURN d.nid AS nid,
				       d.name AS name,
				       d.fullName AS fullName,
				       d.numericValue AS numericValue
				ORDER BY toLower(toString(d.numericValue))
				""";

		log.info("Request to get denominations for currency: {}", currency);
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters("currency", normalized)))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new DenominationResponse(
								record.get("nid").isNull() ? "" : record.get("nid").asString(),
								record.get("name").isNull() ? "" : record.get("name").asString(),
								record.get("fullName").isNull() ? "" : record.get("fullName").asString(),
								record.get("numericValue").isNull() ? null : record.get("numericValue").asDouble())),
				ReactiveSession::close)
				.distinct(dto -> "%s|%s|%s|%s".formatted(
						dto.nid(),
						dto.name(),
						dto.fullName(),
						dto.numericValue() == null ? "" : dto.numericValue().toString()))
				.collectList()
				.doOnSuccess(values -> log.info("Denominations returned successfully, count={}", values.size()));
	}

	public record DenominationResponse(
			String nid,
			String name,
			String fullName,
			Double numericValue) {
	}
}
