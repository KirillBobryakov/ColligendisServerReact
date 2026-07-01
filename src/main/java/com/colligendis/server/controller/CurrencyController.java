package com.colligendis.server.controller;

import com.colligendis.server.parser.numista.CurrencyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/public/currencies")
@RequiredArgsConstructor
public class CurrencyController {

	private final Driver driver;
	private final CurrencyParser currencyParser;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	@GetMapping
	public Mono<List<CurrencyResponse>> getAllCurrencies() {
		final String cypher = """
				MATCH (c:CURRENCY)
				WHERE coalesce(c.nid, c.name, c.fullName) IS NOT NULL
				OPTIONAL MATCH (c)-[:CIRCULATE_WHEN_BEEN]->(:ISSUER)-[:RELATE_TO_COUNTRY]->(country:COUNTRY)
				RETURN c.nid AS nid,
				       c.name AS name,
				       c.fullName AS fullName,
				       country.numistaCode AS countryNumistaCode,
				       country.name AS countryName
				ORDER BY toLower(coalesce(c.name, c.fullName, c.nid))
				""";

		log.info("Request to get all currencies");
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new CurrencyResponse(
								record.get("nid").isNull() ? null : record.get("nid").asString(),
								record.get("name").isNull() ? null : record.get("name").asString(),
								record.get("fullName").isNull() ? null : record.get("fullName").asString(),
								record.get("countryNumistaCode").isNull() ? null
										: record.get("countryNumistaCode").asString(),
								record.get("countryName").isNull() ? null : record.get("countryName").asString())),
				ReactiveSession::close)
				.distinct(dto -> "%s|%s|%s|%s|%s".formatted(
						dto.nid() == null ? "" : dto.nid(),
						dto.name() == null ? "" : dto.name(),
						dto.fullName() == null ? "" : dto.fullName(),
						dto.countryNumistaCode() == null ? "" : dto.countryNumistaCode(),
						dto.countryName() == null ? "" : dto.countryName()))
				.collectList()
				.doOnSuccess(
						currencies -> log.info("Currencies list returned successfully, count={}", currencies.size()));
	}

	@GetMapping("/by-country")
	public Mono<List<CurrencyResponse>> getCurrenciesByCountry(@RequestParam(required = true) String country) {
		final String cypher = """
				MATCH (c:CURRENCY)
				WHERE coalesce(c.nid, c.name, c.fullName) IS NOT NULL
				MATCH (c)-[:CIRCULATE_WHEN_BEEN]->(:ISSUER)-[:RELATE_TO_COUNTRY]->(country:COUNTRY)
				WHERE toLower(coalesce(country.numistaCode, '')) = toLower(trim($country))
				   OR toLower(coalesce(country.name, '')) = toLower(trim($country))
				RETURN c.nid AS nid,
				       c.name AS name,
				       c.fullName AS fullName,
				       country.numistaCode AS countryNumistaCode,
				       country.name AS countryName
				""";

		log.info("Request to get currencies by country={}", country);
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters("country", country)))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new CurrencyResponse(
								record.get("nid").isNull() ? null : record.get("nid").asString(),
								record.get("name").isNull() ? null : record.get("name").asString(),
								record.get("fullName").isNull() ? null : record.get("fullName").asString(),
								record.get("countryNumistaCode").isNull() ? null
										: record.get("countryNumistaCode").asString(),
								record.get("countryName").isNull() ? null : record.get("countryName").asString())),
				ReactiveSession::close)
				.distinct(dto -> "%s|%s|%s|%s|%s".formatted(
						dto.nid() == null ? "" : dto.nid(),
						dto.name() == null ? "" : dto.name(),
						dto.fullName() == null ? "" : dto.fullName(),
						dto.countryNumistaCode() == null ? "" : dto.countryNumistaCode(),
						dto.countryName() == null ? "" : dto.countryName()))
				.collectList()
				.doOnSuccess(
						currencies -> log.info("Currencies list returned successfully, count={}", currencies.size()));
	}

	@GetMapping("/by-issuer")
	public Mono<List<CurrencyResponse>> getCurrenciesByIssuer(
			@RequestParam(name = "issuerNumistaCode", required = true) String issuerNumistaCode) {
		final String cypher = """
				MATCH (c:CURRENCY)
				WHERE coalesce(c.nid, c.name, c.fullName) IS NOT NULL
				MATCH (c)-[:CIRCULATE_WHEN_BEEN]->(issuer:ISSUER)
				WHERE toLower(coalesce(issuer.numistaCode, '')) = toLower(trim($issuerNumistaCode))
				OPTIONAL MATCH (issuer)-[:RELATE_TO_COUNTRY]->(country:COUNTRY)
				RETURN c.nid AS nid,
				       c.name AS name,
				       c.fullName AS fullName,
				       country.numistaCode AS countryNumistaCode,
				       country.name AS countryName
				""";

		log.info("Request to get currencies by issuerNumistaCode={}", issuerNumistaCode);
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters("issuerNumistaCode", issuerNumistaCode)))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new CurrencyResponse(
								record.get("nid").isNull() ? null : record.get("nid").asString(),
								record.get("name").isNull() ? null : record.get("name").asString(),
								record.get("fullName").isNull() ? null : record.get("fullName").asString(),
								record.get("countryNumistaCode").isNull() ? null
										: record.get("countryNumistaCode").asString(),
								record.get("countryName").isNull() ? null : record.get("countryName").asString())),
				ReactiveSession::close)
				.distinct(dto -> "%s|%s|%s|%s|%s".formatted(
						dto.nid() == null ? "" : dto.nid(),
						dto.name() == null ? "" : dto.name(),
						dto.fullName() == null ? "" : dto.fullName(),
						dto.countryNumistaCode() == null ? "" : dto.countryNumistaCode(),
						dto.countryName() == null ? "" : dto.countryName()))
				.collectList()
				.doOnSuccess(
						currencies -> log.info("Currencies list returned successfully, count={}", currencies.size()));
	}

	@GetMapping("/parse")
	public Mono<ResponseEntity<ParseCurrenciesResponse>> parseCurrenciesByIssuer(
			@RequestParam(name = "issuerNumistaCode", required = true) String issuerNumistaCode) {
		log.info("Request to parse currencies from Numista for issuerNumistaCode={}", issuerNumistaCode);
		return currencyParser.loadAndParseCurrenciesByIssuerCode(issuerNumistaCode)
				.map(success -> ResponseEntity.ok(new ParseCurrenciesResponse(issuerNumistaCode, success, null)))
				.onErrorResume(error -> {
					log.error("Failed to parse currencies from Numista for issuerNumistaCode={}", issuerNumistaCode,
							error);
					return Mono.just(ResponseEntity
							.ok(new ParseCurrenciesResponse(issuerNumistaCode, false, error.getMessage())));
				});
	}

	public record CurrencyResponse(String nid, String name, String fullName, String countryNumistaCode,
			String countryName) {
	}

	public record ParseCurrenciesResponse(String issuerNumistaCode, boolean success, String error) {
	}
}
