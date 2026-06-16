package com.colligendis.server.controller;

import com.colligendis.server.controller.CountryController.CountryResponse;
import com.colligendis.server.controller.IssuerController.IssuerResponse;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.service.NumistaCollectionItemService;
import com.colligendis.server.dto.NumistaCollectionItemResponse;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser.CatalogueParseResult;
import com.colligendis.server.util.LocalImageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/public/catalogue")
@RequiredArgsConstructor
public class CatalogueController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	private final CatalogueParser catalogueParser;
	private final ColligendisUserService colligendisUserService;
	private final NumistaCollectionItemService collectionItemService;
	private final BaseLogger baseLogger = new BaseLogger();

	@GetMapping("/parse")
	public Mono<ResponseEntity<CatalogueParseResult>> parseByIssuerNumistaCode(
			@RequestParam String issuerNumistaCode,
			@RequestParam(name = "collectibleTypeCode", required = false, defaultValue = CollectibleType.COINS_CODE) String collectibleTypeCode) {
		log.info("Request to parse catalogue by issuerNumistaCode={}, collectibleTypeCode={}",
				issuerNumistaCode, collectibleTypeCode);
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.map(user -> catalogueParser.parse(issuerNumistaCode, collectibleTypeCode, false, user))
				.map(ResponseEntity::ok)
				.onErrorResume(this::parseErrorResponse);
	}

	private <T> Mono<ResponseEntity<T>> parseErrorResponse(Throwable error) {
		if (error instanceof ResponseStatusException statusException) {
			log.warn("Catalogue parse request failed: {}", statusException.getReason());
			return Mono.just(ResponseEntity.status(statusException.getStatusCode()).build());
		}
		log.error("Catalogue parse request failed: {}", error.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
	}

	@GetMapping("/items")
	public Mono<CatalogueSearchPageResponse> getCatalogueItems(
			@RequestParam(name = "search", required = false) String search,
			@RequestParam(name = "countryNumistaCode", required = false) String countryNumistaCode,
			@RequestParam(name = "issuerNumistaCode", required = false) String issuerNumistaCode,
			@RequestParam(name = "currencyNid", required = false) String currencyNid,
			@RequestParam(name = "denominationNid", required = false) String denominationNid,
			@RequestParam(name = "denominationNumericValue", required = false) Double denominationNumericValue,
			@RequestParam(name = "startYear", required = false) Integer startYear,
			@RequestParam(name = "endYear", required = false) Integer endYear,
			@RequestParam(name = "minDenomination", required = false) Double minDenomination,
			@RequestParam(name = "maxDenomination", required = false) Double maxDenomination,
			@RequestParam(name = "types", required = false) List<String> types,
			@RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
			@RequestParam(name = "limit", required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int limit) {
		final List<String> collectibleTypeCodes = mapTypeNamesToCodes(types);
		final int pageLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
		final int skipRows = Math.max(offset, 0);

		final Map<String, Object> params = new HashMap<>();
		params.put("search", normalize(search));
		params.put("issuerNumistaCode", normalize(issuerNumistaCode));
		params.put("countryNumistaCode", normalize(countryNumistaCode));
		params.put("currencyNid", normalize(currencyNid));
		params.put("denominationNid", normalize(denominationNid));
		params.put("denominationNumericValue", denominationNumericValue);
		params.put("startYear", startYear);
		params.put("endYear", endYear);
		params.put("minDenomination", minDenomination);
		params.put("maxDenomination", maxDenomination);
		params.put("typeCodes", collectibleTypeCodes);
		params.put("typesEmpty", collectibleTypeCodes.isEmpty());
		params.put("skipRows", skipRows);
		params.put("pageLimit", pageLimit);

		if ((countryNumistaCode == null || countryNumistaCode.isBlank())
				&& (issuerNumistaCode == null || issuerNumistaCode.isBlank())) {
			return Mono.just(new CatalogueSearchPageResponse(List.of(), 0L));
		}

		log.info(
				"Request to search catalogue with filters: search='{}', issuer='{}', country='{}', currencyNid='{}', types={}, offset={}, limit={}",
				search, issuerNumistaCode, countryNumistaCode, currencyNid, collectibleTypeCodes, skipRows, pageLimit);

		if (issuerNumistaCode != null && !issuerNumistaCode.isBlank()) {
			return runPagedSearch(issuerSearchCountCypher(), issuerSearchDataCypher(), params)
					.flatMap(this::enrichWithCollectionItemsWhenAuthenticated);
		}
		if (countryNumistaCode != null && !countryNumistaCode.isBlank()) {
			return runPagedSearch(countrySearchCountCypher(), countrySearchDataCypher(), params)
					.flatMap(this::enrichWithCollectionItemsWhenAuthenticated);
		}
		return Mono.just(new CatalogueSearchPageResponse(List.of(), 0L));
	}

	private Mono<CatalogueSearchPageResponse> enrichWithCollectionItemsWhenAuthenticated(
			CatalogueSearchPageResponse page) {
		if (page.items() == null || page.items().isEmpty()) {
			return Mono.just(page);
		}
		return colligendisUserService.optionalAuthenticatedUser(baseLogger)
				.flatMap(user -> loadCollectionItemsForPage(user, page))
				.defaultIfEmpty(page);
	}

	private Mono<CatalogueSearchPageResponse> loadCollectionItemsForPage(
			ColligendisUser user,
			CatalogueSearchPageResponse page) {
		List<String> ntypeNids = page.items().stream()
				.map(CatalogueItemResponse::nid)
				.filter(nid -> nid != null && !nid.isBlank())
				.distinct()
				.toList();
		if (ntypeNids.isEmpty()) {
			return Mono.just(page);
		}
		return collectionItemService.findByUserAndNtypeNids(user, ntypeNids, baseLogger)
				.map(NumistaCollectionItemResponse::from)
				.collectList()
				.map(collectionItems -> new CatalogueSearchPageResponse(
						page.items(), page.totalCount(), collectionItems));
	}

	private Mono<CatalogueSearchPageResponse> runPagedSearch(
			String countCypher,
			String dataCypher,
			Map<String, Object> params) {
		Mono<Long> totalMono = Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(countCypher, params))
						.flatMap(result -> {
							log.info("Result: {}", result.records());
							return Flux.from(result.records());
						})
						.map(record -> {
							log.info("Total: {}", record.get("total"));
							return record.get("total").isNull() ? 0L : record.get("total").asLong();
						})
						.next()
						.defaultIfEmpty(0L),
				ReactiveSession::close);

		Mono<List<CatalogueItemResponse>> itemsMono = Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(dataCypher, params))
						.flatMap(result -> {
							log.info("Result: {}", result.records());
							return Flux.from(result.records());
						})
						.map(this::mapCatalogueRecord)
						.collectList(),
				ReactiveSession::close);

		return Mono.zip(totalMono, itemsMono)
				.map(tuple -> new CatalogueSearchPageResponse(tuple.getT2(), tuple.getT1()))
				.doOnError(error -> log.error(
						"Catalogue paged search failed. countCypher={}, dataCypher={}, params={}",
						countCypher, dataCypher, params, error));
	}

	private String countrySearchCountCypher() {
		return """
				MATCH (countryNode:COUNTRY)
				WHERE toLower(coalesce(countryNode.numistaCode, '')) = $countryCode
				MATCH (n:NTYPE)-[*]->(countryNode)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(collectibleType:COLLECTIBLE_TYPE)
				WHERE ($typesEmpty OR (collectibleType IS NOT NULL AND collectibleType.code IN $typeCodes))
				RETURN count(DISTINCT n) AS total
				""";
	}

	private String countrySearchDataCypher() {
		return """
				MATCH (countryNode:COUNTRY)
				WHERE toLower(coalesce(countryNode.numistaCode, '')) = $countryCode
				MATCH (n:NTYPE)-[*]->(countryNode)
				OPTIONAL MATCH (n)-[:ISSUED_BY]->(issuer:ISSUER)
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(collectibleType:COLLECTIBLE_TYPE)
				OPTIONAL MATCH (n)-[:HAS_OBVERSE]->(obverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_REVERSE]->(reverse:NTYPE_PART)
				WHERE ($typesEmpty OR (collectibleType IS NOT NULL AND collectibleType.code IN $typeCodes))
				RETURN DISTINCT
				  n.nid AS nid,
				  n.title AS title,
				  countryNode.name AS country,
				  coalesce(issuer.name, '') AS issuer,
				  currency.name AS currency,
				  coalesce(denomination.nid, '') AS denominationNid,
				  coalesce(denomination.name, '') AS denominationName,
				  denomination.numericValue AS denominationNumericValue,
				  coalesce(collectibleType.code, '') AS collectibleTypeCode,
				  obverse.pictureLocalPath AS frontImageLocalPath,
				  reverse.pictureLocalPath AS backImageLocalPath
				ORDER BY toLower(coalesce(country, '')), toLower(coalesce(issuer, '')), n.nid
				SKIP toInteger($skipRows) LIMIT toInteger($pageLimit)
				""";
	}

	private String issuerSearchYearFilterClause() {
		return """
				AND (
				  ($startYear IS NULL AND $endYear IS NULL)
				  OR EXISTS {
				    MATCH (n)-[:HAS_VARIANT]->(v:VARIANT)
				    WHERE coalesce(v.deletedOnNumista, false) = false
				    AND (
				      (v.dateGregorianYear IS NOT NULL
				        AND ($startYear IS NULL OR v.dateGregorianYear >= $startYear)
				        AND ($endYear IS NULL OR v.dateGregorianYear <= $endYear))
				      OR
				      (v.fromGregorianYear IS NOT NULL
				        AND ($endYear IS NULL OR v.fromGregorianYear <= $endYear)
				        AND ($startYear IS NULL OR v.tillGregorianYear IS NULL OR v.tillGregorianYear >= $startYear))
				    )
				  }
				)
				""";
	}

	private String issuerSearchCountCypher() {
		return """
				MATCH (n:NTYPE)-[:ISSUED_BY]->(issuer:ISSUER {numistaCode: $issuerNumistaCode})

				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(:COLLECTIBLE_TYPE)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)


				WITH n, issuer, currency, denomination, ct
				WHERE (coalesce($currencyNid, "") = "" OR currency.nid = $currencyNid)
				AND (coalesce($denominationNid, "") = "" OR denomination.nid = $denominationNid)
				AND (coalesce($denominationNumericValue, 0) = 0 OR denomination.numericValue = $denominationNumericValue)
				AND (coalesce($typeCodes, []) = [] OR ct.code IN $typeCodes)
				""" + issuerSearchYearFilterClause() + """

				RETURN count(DISTINCT n) AS total
				""";
	}

	private String issuerSearchDataCypher() {
		return """
				MATCH (n:NTYPE)-[:ISSUED_BY]->(issuer:ISSUER {numistaCode: $issuerNumistaCode})


				OPTIONAL MATCH (issuer)-[*]->(country:COUNTRY)
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)

				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(baseCt:COLLECTIBLE_TYPE)
				OPTIONAL MATCH (baseCt)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)

				WHERE NOT EXISTS {
				    (ct)<-[:HAS_COLLECTIBLE_TYPE_CHILD]-()
				}

				WITH n, country, issuer, currency, denomination, ct
				WHERE (coalesce($currencyNid, "") = "" OR currency.nid = $currencyNid)
				AND (coalesce($denominationNid, "") = "" OR denomination.nid = $denominationNid)
				AND (coalesce($denominationNumericValue, 0) = 0 OR denomination.numericValue = $denominationNumericValue)
				AND (coalesce($typeCodes, []) = [] OR ct.code IN $typeCodes)
				""" + issuerSearchYearFilterClause() + """

				OPTIONAL MATCH (n)-[:HAS_OBVERSE]->(obverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_REVERSE]->(reverse:NTYPE_PART)

				OPTIONAL MATCH (n)-[:HAS_VARIANT]->(variant:VARIANT)
				WHERE coalesce(variant.deletedOnNumista, false) = false

				WITH n, country, issuer, currency, denomination, ct, obverse, reverse,
				  [v IN collect(DISTINCT variant) WHERE v IS NOT NULL | {
				    nid: v.nid,
				    mintage: v.mintage,
				    dated: v.dated,
				    fromGregorianYear: v.fromGregorianYear,
				    tillGregorianYear: v.tillGregorianYear,
				    dateGregorianYear: v.dateGregorianYear,
				    comment: v.comment
				  }] AS variants

				RETURN DISTINCT
				  n.nid AS nid,
				  n.title AS title,

				  country.numistaCode AS countryNumistaCode,
				  country.name AS countryName,

				  issuer.numistaCode AS issuerNumistaCode,
				  issuer.name AS issuerName,

				  currency.nid AS currencyNid,
				  currency.name AS currencyName,
				  currency.fullName AS currencyFullName,

				  denomination.nid AS denominationNid,
				  denomination.name AS denominationName,
				  denomination.numericValue AS denominationNumericValue,

				  ct.code AS collectibleTypeCode,
				  ct.name AS collectibleTypeName,

				  obverse.pictureLocalPath AS frontImageLocalPath,
				  reverse.pictureLocalPath AS backImageLocalPath,

				  variants AS variants

				ORDER BY n.nid
				SKIP toInteger($skipRows) LIMIT toInteger($pageLimit)
				""";
	}

	private CatalogueItemResponse mapCatalogueRecord(Record record) {
		return new CatalogueItemResponse(
				stringField(record, "nid"),
				stringField(record, "title"),
				new CountryResponse(stringField(record, "countryNumistaCode"), stringField(record, "countryName")),
				new IssuerResponse(stringField(record, "issuerNumistaCode"), stringField(record, "issuerName")),
				new CurrencyResponse(stringField(record, "currencyNid"), stringField(record, "currencyName"),
						stringField(record, "currencyFullName")),
				new DenominationResponse(stringField(record, "denominationNid"),
						stringField(record, "denominationName"),
						doubleFieldOrNull(record, "denominationNumericValue")),
				new CollectibleTypeResponse(stringField(record, "collectibleTypeCode"),
						stringField(record, "collectibleTypeName")),
				toCatalogueImageUrl(record, "frontImageLocalPath"),
				toCatalogueImageUrl(record, "backImageLocalPath"),
				variantsField(record, "variants"));
	}

	private String toCatalogueImageUrl(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return "";
		}
		return LocalImageUrls.toClientUrl(record.get(key).asString(""), true);
	}

	private static List<VariantResponse> variantsField(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return List.of();
		}
		return record.get(key).asList(value -> {
			if (value == null || value.isNull()) {
				return null;
			}
			Map<String, Object> map = value.asMap();
			return new VariantResponse(
					stringFromMap(map, "nid"),
					integerFromMap(map, "mintage"),
					booleanFromMap(map, "dated"),
					integerFromMap(map, "fromGregorianYear"),
					integerFromMap(map, "tillGregorianYear"),
					integerFromMap(map, "dateGregorianYear"),
					stringFromMap(map, "comment"));
		}).stream().filter(Objects::nonNull).toList();
	}

	private static String stringFromMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? "" : value.toString();
	}

	private static Integer integerFromMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		return null;
	}

	private static Boolean booleanFromMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean bool) {
			return bool;
		}
		return null;
	}

	private static String stringField(Record record, String key) {
		if (record.get(key).isNull()) {
			return "";
		}
		return record.get(key).asString();
	}

	private static Integer integerFieldOrNull(Record record, String key) {
		if (record.get(key).isNull()) {
			return null;
		}
		return record.get(key).asInt();
	}

	private static Double doubleFieldOrNull(Record record, String key) {
		if (record.get(key).isNull()) {
			return null;
		}
		final var v = record.get(key);
		if (v.type().name().equals("INTEGER")) {
			return (double) v.asInt();
		}
		return v.asDouble();
	}

	private List<String> mapTypeNamesToCodes(List<String> typeNames) {
		if (typeNames == null || typeNames.isEmpty()) {
			return List.of();
		}
		return typeNames.stream()
				.map(type -> type == null ? "" : type.trim().toLowerCase(Locale.ROOT))
				.map(type -> switch (type) {
					case "coin" -> CollectibleType.COINS_CODE;
					case "banknote" -> CollectibleType.BANKNOTES_CODE;
					case "medal" -> CollectibleType.MEDALS_CODE;
					case "token" -> CollectibleType.TOKENS_CODE;
					case "paperexonumia", "paper_exonumia", "paper-exonumia" -> CollectibleType.PAPER_EXONUMIA_CODE;
					default -> null;
				})
				.filter(code -> code != null && !code.isBlank())
				.distinct()
				.toList();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	public record CurrencyResponse(String nid, String name, String fullName) {
	}

	public record DenominationResponse(String nid, String name, Double numericValue) {
	}

	public record CollectibleTypeResponse(String code, String name) {
	}

	public record VariantResponse(
			String nid,
			Integer mintage,
			Boolean dated,
			Integer fromGregorianYear,
			Integer tillGregorianYear,
			Integer dateGregorianYear,
			String comment) {
	}

	public record CatalogueItemResponse(
			String nid,
			String title,
			CountryResponse country,
			IssuerResponse issuer,
			CurrencyResponse currency,
			DenominationResponse denomination,
			CollectibleTypeResponse collectibleType,
			String frontImageUrl,
			String backImageUrl,
			List<VariantResponse> variants) {
	}

	public record CatalogueSearchPageResponse(
			List<CatalogueItemResponse> items,
			long totalCount,
			List<NumistaCollectionItemResponse> collectionItems) {

		public CatalogueSearchPageResponse(List<CatalogueItemResponse> items, long totalCount) {
			this(items, totalCount, List.of());
		}
	}
}
