package com.colligendis.server.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.controller.CatalogueSummaryController.CountryLoadNTypesCountResponse;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.controller.CatalogueSummaryController.CountrySummaryResponse;
import com.colligendis.server.controller.CatalogueSummaryController.IssuerLoadNTypesCountResponse;
import com.colligendis.server.controller.CatalogueSummaryController.IssuerSummaryResponse;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.service.IssuerService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.NumistaPageLoadException;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser.CatalogueParseResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueSummaryService {

	private static final String COOKIE_NOT_CONFIGURED = "Numista cookie is not configured. Add it in Settings / Profile.";

	private final Driver driver;
	private final CatalogueParser catalogueParser;
	private final IssuerService issuerService;
	private final NTypeService nTypeService;
	private final ColligendisUserService colligendisUserService;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	public Mono<List<CountrySummaryResponse>> getCountriesSummary() {
		log.info("Getting countries summary");

		final String cypher = """
				MATCH (c:COUNTRY)
				WHERE c.name IS NOT NULL AND trim(c.name) <> ''
				CALL (c) {
				  MATCH (i:ISSUER)-[:RELATE_TO_COUNTRY]->(c)
				  RETURN i AS issuer
				  UNION
				  MATCH (i:ISSUER)-[:RELATE_TO_SUBJECT]->(:SUBJECT)-[:RELATE_TO_COUNTRY]->(c)
				  RETURN i AS issuer
				  UNION
				  MATCH (i:ISSUER)-[:RELATE_TO_SUBJECT]->(:SUBJECT)-[:PARENT_SUBJECT*1..15]->(:SUBJECT)-[:RELATE_TO_COUNTRY]->(c)
				  RETURN i AS issuer
				}
				WITH c, issuer
				OPTIONAL MATCH (n:NTYPE)-[:ISSUED_BY]->(issuer)
				RETURN c.numistaCode AS numistaCode,
				       c.name AS name,
				       count(DISTINCT issuer) AS relatedIssuersCount,
				       c.countNTypesOnNumista AS countNTypesOnNumista,
				       count(DISTINCT n) AS countNTypesOnServer
				ORDER BY toLower(c.name)
								""";

		return Flux.usingWhen(
				Mono.just(driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new CountrySummaryResponse(
								record.get("numistaCode").isNull() ? "" : record.get("numistaCode").asString(),
								record.get("name").isNull() ? "" : record.get("name").asString(),
								record.get("relatedIssuersCount").isNull() ? 0
										: record.get("relatedIssuersCount").asLong(),
								record.get("countNTypesOnNumista").isNull() ? null
										: record.get("countNTypesOnNumista").asInt(),
								record.get("countNTypesOnServer").isNull() ? 0
										: record.get("countNTypesOnServer").asLong())),
				ReactiveSession::close)
				.collectList();
	}

	/**
	 * Finds nids of all NTypes stored in the database that are issued by the issuer
	 * with the given Numista code.
	 */
	public Mono<List<String>> findNTypeNidsByIssuerNumistaCode(String issuerNumistaCode) {
		final String code = issuerNumistaCode == null ? "" : issuerNumistaCode.trim();
		if (code.isEmpty()) {
			return Mono.just(List.of());
		}

		final String cypher = """
				MATCH (n:NTYPE)-[:ISSUED_BY]->(issuer:ISSUER)
				WHERE toLower(coalesce(issuer.numistaCode, '')) = toLower($issuerNumistaCode)
				  AND n.nid IS NOT NULL AND trim(n.nid) <> ''
				RETURN DISTINCT n.nid AS nid
				""";

		return runNidsQuery(cypher, Values.parameters("issuerNumistaCode", code));
	}

	/**
	 * Finds nids of all NTypes stored in the database that are related to the
	 * country with the given Numista code.
	 */
	public Mono<List<String>> findNTypeNidsByCountryNumistaCode(String countryNumistaCode) {
		final String code = countryNumistaCode == null ? "" : countryNumistaCode.trim();
		if (code.isEmpty()) {
			return Mono.just(List.of());
		}

		final String cypher = """
				MATCH (country:COUNTRY)
				WHERE toLower(coalesce(country.numistaCode, '')) = toLower($countryNumistaCode)
				MATCH (n:NTYPE)-[:ISSUED_BY]->(:ISSUER)-[*0..]->(country)
				WHERE n.nid IS NOT NULL AND trim(n.nid) <> ''
				RETURN DISTINCT n.nid AS nid
				""";

		return runNidsQuery(cypher, Values.parameters("countryNumistaCode", code));
	}

	private Mono<List<String>> runNidsQuery(String cypher, org.neo4j.driver.Value parameters) {
		return Flux.usingWhen(
				Mono.just(driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, parameters))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> record.get("nid").isNull() ? "" : record.get("nid").asString())
						.filter(nid -> !nid.isBlank()),
				ReactiveSession::close)
				.collectList();
	}

	public Mono<List<IssuerSummaryResponse>> getIssuersSummary() {
		log.info("Getting issuers summary");

		final String cypher = """
				MATCH (i:ISSUER)
				WHERE i.name IS NOT NULL AND trim(i.name) <> ''
				OPTIONAL MATCH (n:NTYPE)-[:ISSUED_BY]->(i)
				RETURN i.numistaCode AS numistaCode,
				       i.name AS name,
				       i.countNTypesOnNumista AS countNTypesOnNumista,
				       count(DISTINCT n) AS countNTypesOnServer
				ORDER BY toLower(i.name)
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new IssuerSummaryResponse(
								record.get("numistaCode").isNull() ? "" : record.get("numistaCode").asString(),
								record.get("name").isNull() ? "" : record.get("name").asString(),
								record.get("countNTypesOnNumista").isNull() ? null
										: record.get("countNTypesOnNumista").asInt(),
								record.get("countNTypesOnServer").isNull() ? 0
										: record.get("countNTypesOnServer").asLong())),
				ReactiveSession::close)
				.collectList();
	}

	public Mono<CountryLoadNTypesCountResponse> loadCountryNTypesCount(
			ColligendisUser user,
			String countryNumistaCode,
			boolean withNids) {
		log.info("Loading ntypes count for country={}, withNids={}", countryNumistaCode, withNids);
		return requireNumistaCookie(user)
				.then(loadCountryNTypesCountInternal(user, countryNumistaCode, withNids))
				.onErrorMap(NumistaPageLoadException.class, this::toBadGateway);
	}

	public Mono<IssuerLoadNTypesCountResponse> loadIssuerNTypesCount(
			ColligendisUser user,
			String issuerNumistaCode,
			boolean withNids) {
		log.info("Loading ntypes count for issuer={}, withNids={}", issuerNumistaCode, withNids);
		return requireNumistaCookie(user)
				.then(loadIssuerNTypesCountInternal(user, issuerNumistaCode, withNids))
				.onErrorMap(NumistaPageLoadException.class, this::toBadGateway);
	}

	private Mono<Void> requireNumistaCookie(ColligendisUser user) {
		if (!StringUtils.hasText(user.getNumistaCookie())) {
			return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, COOKIE_NOT_CONFIGURED));
		}
		return Mono.empty();
	}

	private ResponseStatusException toBadGateway(NumistaPageLoadException ex) {
		log.warn("Numista catalogue load failed for {}: {}", ex.getUrl(), ex.getMessage());
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
	}

	private Mono<CountryLoadNTypesCountResponse> loadCountryNTypesCountInternal(
			ColligendisUser user,
			String countryNumistaCode,
			boolean withNids) {
		final String normalizedCode = countryNumistaCode.trim();
		if (normalizedCode.isEmpty()) {
			return Mono.just(new CountryLoadNTypesCountResponse(normalizedCode, null, false, List.of()));
		}

		return readExistingCountryCount(normalizedCode)
				.flatMap(existingCountOptional -> {
					final Integer existingCount = existingCountOptional.orElse(null);
					final boolean shouldLoadFromNumista = existingCount == null || existingCount <= 0 || withNids;
					if (!shouldLoadFromNumista) {
						return Mono.just(new CountryLoadNTypesCountResponse(
								normalizedCode, existingCount, false, List.of()));
					}

					return parseFromNumista(() -> parseCountryFromNumista(user, normalizedCode, withNids))
							.flatMap(aggregation -> {
								final int resultingCount = aggregation.count();
								final List<String> resultingNids = aggregation.nids();

								final Mono<Void> saveMono = (existingCount == null || existingCount <= 0)
										? saveCountryCountNTypesOnNumista(normalizedCode, resultingCount)
										: Mono.empty();

								return saveMono.thenReturn(new CountryLoadNTypesCountResponse(
										normalizedCode, resultingCount, true, resultingNids));
							});
				});
	}

	private Mono<IssuerLoadNTypesCountResponse> loadIssuerNTypesCountInternal(
			ColligendisUser user,
			String issuerNumistaCode,
			boolean withNids) {
		final String normalizedCode = issuerNumistaCode.trim();
		if (normalizedCode.isEmpty()) {
			return Mono.just(new IssuerLoadNTypesCountResponse(normalizedCode, null, false, List.of()));
		}

		return readExistingIssuerCount(normalizedCode)
				.flatMap(existingCountOptional -> {
					final Integer existingCount = existingCountOptional.orElse(null);
					final boolean shouldLoadFromNumista = existingCount == null || existingCount <= 0 || withNids;
					if (!shouldLoadFromNumista) {
						return Mono.just(new IssuerLoadNTypesCountResponse(
								normalizedCode, existingCount, false, List.of()));
					}

					return parseFromNumista(() -> parseIssuerFromNumista(user, normalizedCode, withNids))
							.flatMap(aggregation -> {
								final int resultingCount = aggregation.count();
								final List<String> resultingNids = aggregation.nids();

								final Mono<Void> saveMono = (existingCount == null || existingCount <= 0)
										? saveIssuerCountNTypesOnNumista(normalizedCode, resultingCount)
										: Mono.empty();

								return saveMono.thenReturn(new IssuerLoadNTypesCountResponse(
										normalizedCode, resultingCount, true, resultingNids));
							});
				});
	}

	private Mono<ParseAggregation> parseFromNumista(java.util.concurrent.Callable<ParseAggregation> parser) {
		return Mono.fromCallable(parser)
				.subscribeOn(Schedulers.boundedElastic());
	}

	private Mono<Optional<Integer>> readExistingCountryCount(String countryNumistaCode) {
		return readExistingCountOnNode("COUNTRY", countryNumistaCode);
	}

	private Mono<Optional<Integer>> readExistingIssuerCount(String issuerNumistaCode) {
		return readExistingCountOnNode("ISSUER", issuerNumistaCode);
	}

	private Mono<Optional<Integer>> readExistingCountOnNode(String label, String numistaCode) {
		final String cypher = """
				MATCH (n:%s {numistaCode: $code})
				RETURN n.countNTypesOnNumista AS countNTypesOnNumista
				""".formatted(label);

		return Mono.usingWhen(
				Mono.just(driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters("code", numistaCode)))
						.flatMap(result -> Flux.from(result.records()))
						.next()
						.flatMap(record -> record.get("countNTypesOnNumista").isNull()
								? Mono.empty()
								: Mono.just(record.get("countNTypesOnNumista").asInt())),
				ReactiveSession::close)
				.map(Optional::of)
				.defaultIfEmpty(Optional.empty());
	}

	private Mono<Void> saveCountryCountNTypesOnNumista(String countryNumistaCode, int count) {
		return saveCountNTypesOnNumista("COUNTRY", countryNumistaCode, count);
	}

	private Mono<Void> saveIssuerCountNTypesOnNumista(String issuerNumistaCode, int count) {
		return saveCountNTypesOnNumista("ISSUER", issuerNumistaCode, count);
	}

	private Mono<Void> saveCountNTypesOnNumista(String label, String numistaCode, int count) {
		final String cypher = """
				MATCH (n:%s {numistaCode: $code})
				SET n.countNTypesOnNumista = $count
				""".formatted(label);

		return Flux.usingWhen(
				Mono.just(driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters(
						"code", numistaCode,
						"count", count))).thenMany(Flux.empty()),
				ReactiveSession::close)
				.then();
	}

	private ParseAggregation parseCountryFromNumista(ColligendisUser user, String countryNumistaCode,
			boolean withNids) {
		if (withNids) {
			return parseCountryIssuersFromNumista(user, countryNumistaCode);
		}

		final CatalogueParseResult result = catalogueParser.parse(
				countryNumistaCode, CollectibleType.ALL, false, user);
		return new ParseAggregation(result.nTypesCount(), result.nids());
	}

	private ParseAggregation parseIssuerFromNumista(ColligendisUser user, String issuerCode, boolean withNids) {
		final CatalogueParseResult result = catalogueParser.parse(issuerCode, CollectibleType.ALL, withNids, user);
		if (!withNids || result.nids().isEmpty()) {
			return new ParseAggregation(result.nTypesCount(), result.nids());
		}

		linkNidsToIssuer(issuerCode, result.nids());
		return new ParseAggregation(result.nTypesCount(), result.nids());
	}

	private ParseAggregation parseCountryIssuersFromNumista(ColligendisUser user, String countryCode) {
		final List<String> issuerCodes = readRelatedIssuerCodes(countryCode).blockOptional().orElse(List.of());
		if (issuerCodes.isEmpty()) {
			return new ParseAggregation(0, List.of());
		}

		int totalCount = 0;
		LinkedHashSet<String> nids = new LinkedHashSet<>();
		for (String issuerCode : issuerCodes) {
			final CatalogueParseResult result = catalogueParser.parse(issuerCode, CollectibleType.ALL, true, user);
			totalCount += result.nTypesCount();
			nids.addAll(result.nids());
			linkNidsToIssuer(issuerCode, result.nids());
		}

		return new ParseAggregation(totalCount, new ArrayList<>(nids));
	}

	private Mono<List<String>> readRelatedIssuerCodes(String countryCode) {
		final String cypher = """
				MATCH (i:ISSUER)-[*]->(c:COUNTRY {numistaCode: $countryCode})
				WHERE i.numistaCode IS NOT NULL AND trim(i.numistaCode) <> ''
				RETURN DISTINCT i.numistaCode AS numistaCode
				ORDER BY toLower(i.numistaCode)
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher, Values.parameters("countryCode", countryCode)))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> record.get("numistaCode").asString()),
				ReactiveSession::close)
				.collectList();
	}

	private void linkNidsToIssuer(String issuerCode, List<String> nids) {
		if (nids == null || nids.isEmpty()) {
			return;
		}

		final BaseLogger logger = new BaseLogger();
		final var issuerResult = issuerService.findByNumistaCode(issuerCode, logger).block();
		if (issuerResult == null || issuerResult.getStatus() != FindExecutionStatus.FOUND
				|| issuerResult.getNode() == null) {
			log.warn("Issuer not found for numistaCode={}, skipped linking nids", issuerCode);
			return;
		}

		final Issuer issuer = issuerResult.getNode();
		final int linkedCount = nTypeService
				.linkNidsToIssuer(issuer, nids, colligendisUserService.getNumistaParserUserMono(), logger)
				.blockOptional()
				.orElse(0);
		if (linkedCount == 0 && !nids.isEmpty()) {
			log.warn("No ntypes linked to issuer={} (requested {} nids)", issuerCode, nids.size());
		} else {
			log.debug("Linked {} ntypes to issuer={}", linkedCount, issuerCode);
		}
	}

	private record ParseAggregation(int count, List<String> nids) {
	}
}
