package com.colligendis.server.controller;

import com.colligendis.server.database.numista.model.CollectibleType;
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
@RequestMapping("/api/public/countries")
@RequiredArgsConstructor
public class CountryController {

	private final Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	@GetMapping
	public Mono<List<CountryResponse>> getAllCountries() {
		final String cypher = """
				MATCH (c:COUNTRY)
				WHERE c.name IS NOT NULL AND trim(c.name) <> ''
				RETURN c.numistaCode AS numistaCode, c.name AS name
				ORDER BY toLower(c.name)
				""";

		log.info("Request to get all countries");
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new CountryResponse(
								record.get("numistaCode").isNull() ? "" : record.get("numistaCode").asString(),
								record.get("name").asString())),
				ReactiveSession::close)
				.distinct(dto -> "%s|%s".formatted(dto.numistaCode(), dto.name()))
				.collectList()
				.doOnSuccess(countries -> log.info("Countries list returned successfully, count={}", countries.size()));
	}

	@GetMapping("/stats-by-country")
	public Mono<CountryStatsResponse> getStatsByCountry(@RequestParam(required = true) String country) {
		final String cypher = """
				MATCH (country:COUNTRY)
				WHERE toLower(coalesce(country.numistaCode, '')) = toLower(trim($country))
				   OR toLower(coalesce(country.name, '')) = toLower(trim($country))
				OPTIONAL MATCH (n:NTYPE)-[:ISSUED_BY]->(:ISSUER)-[:RELATE_TO_COUNTRY]->(country)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(:COLLECTIBLE_TYPE)
				                <-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(top:COLLECTIBLE_TYPE)
				WHERE top IS NULL OR top.code IN [
				  $coinsCode, $tokensCode, $medalsCode, $banknotesCode, $paperExonumiaCode
				]
				RETURN
				  count(DISTINCT CASE WHEN top.code = $coinsCode THEN n END) AS coinCount,
				  count(DISTINCT CASE WHEN top.code = $tokensCode THEN n END) AS tokenCount,
				  count(DISTINCT CASE WHEN top.code = $medalsCode THEN n END) AS medalCount,
				  count(DISTINCT CASE WHEN top.code = $banknotesCode THEN n END) AS banknoteCount,
				  count(DISTINCT CASE WHEN top.code = $paperExonumiaCode THEN n END) AS paperExonumiaCount
				""";

		log.info("Request to get stats by country={}", country);
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters(
						"country", country,
						"coinsCode", CollectibleType.COINS_CODE,
						"tokensCode", CollectibleType.TOKENS_CODE,
						"medalsCode", CollectibleType.MEDALS_CODE,
						"banknotesCode", CollectibleType.BANKNOTES_CODE,
						"paperExonumiaCode", CollectibleType.PAPER_EXONUMIA_CODE)))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new CountryStatsResponse(record.get("coinCount").asInt(),
								record.get("tokenCount").asInt(), record.get("medalCount").asInt(),
								record.get("banknoteCount").asInt(), record.get("paperExonumiaCount").asInt())),
				ReactiveSession::close)
				.singleOrEmpty()
				.doOnSuccess(stats -> log.info("Stats for country={} returned successfully", country));
	}

	public record CountryStatsResponse(int coinCount, int tokenCount, int medalCount, int banknoteCount,
			int paperExonumiaCount) {
	}

	public record CountryResponse(String numistaCode, String name) {
	}
}
