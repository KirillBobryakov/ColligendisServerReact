package com.colligendis.server.controller;

import com.colligendis.server.util.IssuerSearchMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.colligendis.server.database.numista.model.CollectibleType;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/public/issuers")
@RequiredArgsConstructor
public class IssuerController {

	private final Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	private static final String ISSUER_LIVE_FILTER = """
			  AND i.uuid IS NOT NULL
			  AND NONE(l IN labels(i) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
			""";

	@GetMapping("/search")
	public Mono<List<IssuerResponse>> searchIssuers(
			@RequestParam(name = "query", required = false) String query) {
		if (query != null && query.trim().length() >= 2) {
			final String normalizedQuery = IssuerSearchMatcher.normalizeQuery(query);
			if (normalizedQuery.isBlank()) {
				return Mono.just(List.of());
			}
			final String searchCypher = """
					MATCH (i:ISSUER)
					WHERE i.name IS NOT NULL
					  AND trim(i.name) <> ''
					  AND (
					    (coalesce(i.normalizedName, '') <> '' AND i.normalizedName CONTAINS $normalizedQuery)
					    OR toLower(i.name) CONTAINS $normalizedQuery
					  )
					""" + ISSUER_LIVE_FILTER + """
					RETURN i.numistaCode AS numistaCode, i.name AS name
					ORDER BY toLower(i.name)
					LIMIT 500
					""";

			log.info("Request to search issuers by query: {}", query);
			return Flux.usingWhen(
					Mono.just(driver.session(ReactiveSession.class,
							SessionConfig.builder().withDatabase(neo4jDatabase).build())),
					session -> Flux.from(session.run(searchCypher,
							Values.parameters("normalizedQuery", normalizedQuery)))
							.flatMap(result -> Flux.from(result.records()))
							.map(record -> new IssuerResponse(
									record.get("numistaCode").isNull() ? "" : record.get("numistaCode").asString(),
									record.get("name").isNull() ? "" : record.get("name").asString()))
							.filter(issuer -> IssuerSearchMatcher.matches(query, issuer.name(), null)),
					ReactiveSession::close)
					.distinct(dto -> "%s|%s".formatted(dto.numistaCode(), dto.name()))
					.collectList()
					.doOnSuccess(issuers -> log.info("Issuer search completed, count={}", issuers.size()));
		}

		return Mono.just(List.of());
	}

	@GetMapping("/by-country")
	public Mono<List<IssuerResponse>> getIssuersByCountry(
			@RequestParam(name = "countryNumistaCode", required = true) String countryNumistaCode) {
		final String cypher = """
				MATCH (c:COUNTRY {numistaCode: $countryNumistaCode})<-[*]-(i:ISSUER)
				RETURN DISTINCT i.numistaCode AS numistaCode, i.name AS name
				ORDER BY toLower(i.name)
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters("countryNumistaCode", countryNumistaCode)))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new IssuerResponse(
								record.get("numistaCode").isNull() ? "" : record.get("numistaCode").asString(),
								record.get("name").isNull() ? "" : record.get("name").asString())),
				ReactiveSession::close)
				.collectList();
	}

	@GetMapping("/stats-by-issuer")
	public Mono<IssuerStatsResponse> getStatsByIssuer(@RequestParam(required = true) String issuerNumistaCode) {
		final String cypher = """
				MATCH (i:ISSUER {numistaCode: $issuerNumistaCode})
				OPTIONAL MATCH (n:NTYPE)-[:ISSUED_BY]->(i)
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

		log.info("Request to get stats by issuer={}", issuerNumistaCode);
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters(
						"issuerNumistaCode", issuerNumistaCode,
						"coinsCode", CollectibleType.COINS_CODE,
						"tokensCode", CollectibleType.TOKENS_CODE,
						"medalsCode", CollectibleType.MEDALS_CODE,
						"banknotesCode", CollectibleType.BANKNOTES_CODE,
						"paperExonumiaCode", CollectibleType.PAPER_EXONUMIA_CODE)))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new IssuerStatsResponse(record.get("coinCount").asInt(),
								record.get("tokenCount").asInt(), record.get("medalCount").asInt(),
								record.get("banknoteCount").asInt(), record.get("paperExonumiaCount").asInt())),
				ReactiveSession::close)
				.singleOrEmpty()
				.doOnSuccess(stats -> log.info("Stats for issuer={} returned successfully", issuerNumistaCode));
	}

	public record IssuerStatsResponse(int coinCount, int tokenCount, int medalCount, int banknoteCount,
			int paperExonumiaCount) {
	}

	public record IssuerResponse(String numistaCode, String name) {
	}
}
