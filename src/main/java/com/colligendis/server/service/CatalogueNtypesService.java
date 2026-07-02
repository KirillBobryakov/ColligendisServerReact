package com.colligendis.server.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.colligendis.server.controller.CatalogueController.CalendarResponse;
import com.colligendis.server.controller.CatalogueController.CatalogueItemResponse;
import com.colligendis.server.controller.CatalogueController.CatalogueSearchPageResponse;
import com.colligendis.server.controller.CatalogueController.CollectibleTypeResponse;
import com.colligendis.server.controller.CatalogueController.CurrencyResponse;
import com.colligendis.server.controller.CatalogueController.DenominationResponse;
import com.colligendis.server.controller.CatalogueController.VariantResponse;
import com.colligendis.server.controller.CountryController.CountryResponse;
import com.colligendis.server.controller.IssuerController.IssuerResponse;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.cypher.VariantYearCypher;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.service.NumistaCollectionItemService;
import com.colligendis.server.dto.MarkResponse;
import com.colligendis.server.dto.NumistaCollectionItemResponse;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.util.CatalogueSortType;
import com.colligendis.server.util.CatalogueSortType.Sort;
import com.colligendis.server.util.DenominationNumericFilter;
import com.colligendis.server.util.LocalImageUrls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueNtypesService {

	private static final int DEFAULT_PAGE_SIZE = 200;
	private static final int MAX_PAGE_SIZE = 200;

	private final Driver driver;
	private final ColligendisUserService colligendisUserService;
	private final NumistaCollectionItemService collectionItemService;
	private final BaseLogger baseLogger = new BaseLogger();

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	public record CatalogueNtypesSearchRequest(
			String search,
			String nid,
			String countryNumistaCode,
			String issuerNumistaCode,
			String currencyNid,
			String denominationNid,
			Double denominationNumericValue,
			Integer startYear,
			Integer endYear,
			Double minDenomination,
			Double maxDenomination,
			List<String> types,
			Sort sort,
			boolean myCollectionOnly,
			int offset,
			int limit) {
	}

	public Mono<CatalogueSearchPageResponse> search(CatalogueNtypesSearchRequest request) {
		final String nid = normalizeNid(request.nid());
		if (!nid.isBlank()) {
			return searchByNid(nid, request.offset(), request.limit());
		}

		final List<String> collectibleTypeCodes = mapTypeNamesToCodes(request.types());
		final int pageLimit = Math.min(Math.max(request.limit(), 1), MAX_PAGE_SIZE);
		final int skipRows = Math.max(request.offset(), 0);
		final Sort catalogueSort = request.sort() != null ? request.sort() : Sort.COUNTRY;

		final Map<String, Object> params = new HashMap<>();
		params.put("search", normalize(request.search()));
		params.put("issuerNumistaCode", normalize(request.issuerNumistaCode()));
		params.put("countryNumistaCode", normalize(request.countryNumistaCode()));
		params.put("currencyNid", normalize(request.currencyNid()));
		params.put("denominationNid", normalize(request.denominationNid()));
		params.put("denominationNumericValue", request.denominationNumericValue());
		params.put("denominationNumericText", DenominationNumericFilter.numericText(request.denominationNumericValue()));
		params.put("startYear", request.startYear());
		params.put("endYear", request.endYear());
		params.put("minDenomination", request.minDenomination());
		params.put("maxDenomination", request.maxDenomination());
		params.put("typeCodes", collectibleTypeCodes);
		params.put("typesEmpty", collectibleTypeCodes.isEmpty());
		params.put("skipRows", skipRows);
		params.put("pageLimit", pageLimit);

		if (request.myCollectionOnly()) {
			return colligendisUserService.requireAuthenticatedUser(baseLogger)
					.flatMap(user -> {
						params.put("userUuid", user.getUuid());
						log.info(
								"Request to search my-collection ntypes with filters: search='{}', issuer='{}', country='{}', types={}, sortType={}, offset={}, limit={}",
								request.search(), request.issuerNumistaCode(), request.countryNumistaCode(),
								collectibleTypeCodes, catalogueSort, skipRows, pageLimit);
						return runPagedSearch(myCollectionCountCypher(), myCollectionDataCypher(catalogueSort), params)
								.flatMap(page -> loadCollectionItemsForPage(user, page));
					});
		}

		if ((request.countryNumistaCode() == null || request.countryNumistaCode().isBlank())
				&& (request.issuerNumistaCode() == null || request.issuerNumistaCode().isBlank())) {
			return Mono.just(new CatalogueSearchPageResponse(List.of(), 0L));
		}

		log.info(
				"Request to search catalogue ntypes with filters: search='{}', issuer='{}', country='{}', currencyNid='{}', types={}, sortType={}, offset={}, limit={}",
				request.search(), request.issuerNumistaCode(), request.countryNumistaCode(), request.currencyNid(),
				collectibleTypeCodes, catalogueSort, skipRows, pageLimit);

		Mono<CatalogueSearchPageResponse> searchMono;
		if (request.issuerNumistaCode() != null && !request.issuerNumistaCode().isBlank()) {
			searchMono = runPagedSearch(issuerSearchCountCypher(), issuerSearchDataCypher(catalogueSort), params);
		} else {
			searchMono = runPagedSearch(countrySearchCountCypher(), countrySearchDataCypher(catalogueSort), params);
		}
		return searchMono.flatMap(this::enrichWithCollectionItemsWhenAuthenticated);
	}

	public static CatalogueNtypesSearchRequest fromQueryParams(
			String search,
			String nid,
			String countryNumistaCode,
			String issuerNumistaCode,
			String currencyNid,
			String denominationNid,
			Double denominationNumericValue,
			Integer startYear,
			Integer endYear,
			Double minDenomination,
			Double maxDenomination,
			List<String> types,
			String sortType,
			boolean myCollectionOnly,
			int offset,
			int limit) {
		final int resolvedLimit = limit > 0 ? limit : DEFAULT_PAGE_SIZE;
		return new CatalogueNtypesSearchRequest(
				search,
				nid,
				countryNumistaCode,
				issuerNumistaCode,
				currencyNid,
				denominationNid,
				denominationNumericValue,
				startYear,
				endYear,
				minDenomination,
				maxDenomination,
				types,
				Sort.fromParam(sortType),
				myCollectionOnly,
				offset,
				resolvedLimit);
	}

	private Mono<CatalogueSearchPageResponse> searchByNid(String nid, int offset, int limit) {
		final int pageLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
		final int skipRows = Math.max(offset, 0);
		final Map<String, Object> params = new HashMap<>();
		params.put("nid", nid);
		params.put("skipRows", skipRows);
		params.put("pageLimit", pageLimit);

		log.info("Request to search catalogue ntypes by nid={}, offset={}, limit={}", nid, skipRows, pageLimit);
		return runPagedSearch(nidSearchCountCypher(), nidSearchDataCypher(), params)
				.flatMap(this::enrichWithCollectionItemsWhenAuthenticated);
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
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> record.get("total").isNull() ? 0L : record.get("total").asLong())
						.next()
						.defaultIfEmpty(0L),
				ReactiveSession::close);

		Mono<List<CatalogueItemResponse>> itemsMono = Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(dataCypher, params))
						.flatMap(result -> Flux.from(result.records()))
						.map(this::mapCatalogueRecord)
						.collectList(),
				ReactiveSession::close);

		return Mono.zip(totalMono, itemsMono)
				.map(tuple -> new CatalogueSearchPageResponse(tuple.getT2(), tuple.getT1()))
				.doOnError(error -> log.error(
						"Catalogue ntypes search failed. countCypher={}, dataCypher={}, params={}",
						countCypher, dataCypher, params, error));
	}

	private static String variantsCollectExpression() {
		return """
				  [v IN collect(DISTINCT variant) WHERE v IS NOT NULL | {
				    nid: v.nid,
				    mintage: v.mintage,
				    dated: v.dated,
				    fromGregorianYear: %s,
				    tillGregorianYear: %s,
				    dateGregorianYear: %s,
				    dateYear: %s,
				    matchUpToGregorianYear: %s,
				    calendar: %s,
				    comment: v.comment,
				    marks: [(v)-[:WITH_MARK]->(m:MARK) | {
				      nid: m.nid,
				      code: m.code,
				      name: m.name,
				      description: m.description,
				      pictureLocalPath: m.pictureLocalPath
				    }]
				  }] AS variants
				""".formatted(
				VariantYearCypher.fromGregorianYearFor("v"),
				VariantYearCypher.tillGregorianYearFor("v"),
				VariantYearCypher.dateGregorianYearFor("v"),
				VariantYearCypher.dateYearFor("v"),
				VariantYearCypher.matchUpToGregorianYearFor("v"),
				VariantYearCypher.calendarFor("v"));
	}

	private String nidSearchCountCypher() {
		return """
				MATCH (n:NTYPE {nid: $nid})
				RETURN count(n) AS total
				""";
	}

	private String nidSearchDataCypher() {
		return """
				MATCH (n:NTYPE {nid: $nid})
				OPTIONAL MATCH (n)-[:ISSUED_BY]->(issuer:ISSUER)
				OPTIONAL MATCH (issuer)-[*]->(country:COUNTRY)
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(baseCt:COLLECTIBLE_TYPE)
				OPTIONAL MATCH (baseCt)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)
				WHERE NOT EXISTS {
				    (ct)<-[:HAS_COLLECTIBLE_TYPE_CHILD]-()
				}
				OPTIONAL MATCH (n)-[:HAS_OBVERSE]->(obverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_REVERSE]->(reverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_VARIANT]->(variant:VARIANT)
				WHERE coalesce(variant.deletedOnNumista, false) = false
				WITH n, country, issuer, currency, denomination, ct, obverse, reverse,
				""" + variantsCollectExpression() + """
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

	private String countrySearchCountCypher() {
		return """
				MATCH (country:COUNTRY)
				WHERE toLower(coalesce(country.numistaCode, '')) = $countryNumistaCode
				MATCH (n:NTYPE)-[:ISSUED_BY]->(issuer:ISSUER)
				MATCH (issuer)-[*]->(country)
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(:COLLECTIBLE_TYPE)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)
				WITH n, country, issuer, currency, denomination, ct
				WHERE (coalesce($currencyNid, "") = "" OR currency.nid = $currencyNid)
				""" + DenominationNumericFilter.cypherClause() + """
				AND (coalesce($typeCodes, []) = [] OR ct.code IN $typeCodes)
				""" + issuerSearchYearFilterClause() + """
				RETURN count(DISTINCT n) AS total
				""";
	}

	private String countrySearchDataCypher(Sort sort) {
		return """
				MATCH (country:COUNTRY)
				WHERE toLower(coalesce(country.numistaCode, '')) = $countryNumistaCode
				MATCH (n:NTYPE)-[:ISSUED_BY]->(issuer:ISSUER)
				MATCH (issuer)-[*]->(country)
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(baseCt:COLLECTIBLE_TYPE)
				OPTIONAL MATCH (baseCt)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)
				WHERE NOT EXISTS {
				    (ct)<-[:HAS_COLLECTIBLE_TYPE_CHILD]-()
				}
				WITH n, country, issuer, currency, denomination, ct
				WHERE (coalesce($currencyNid, "") = "" OR currency.nid = $currencyNid)
				""" + DenominationNumericFilter.cypherClause() + """
				AND (coalesce($typeCodes, []) = [] OR ct.code IN $typeCodes)
				""" + issuerSearchYearFilterClause() + """
				OPTIONAL MATCH (n)-[:HAS_OBVERSE]->(obverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_REVERSE]->(reverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_VARIANT]->(variant:VARIANT)
				WHERE coalesce(variant.deletedOnNumista, false) = false
				WITH n, country, issuer, currency, denomination, ct, obverse, reverse,
				""" + variantsCollectExpression() + """
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
				"""
				+ CatalogueSortType.orderByClause(sort) + """
				SKIP toInteger($skipRows) LIMIT toInteger($pageLimit)
				""";
	}

	private String issuerSearchYearFilterClause() {
		return VariantYearCypher.issuerSearchYearFilterClause("v");
	}

	private String issuerSearchCountCypher() {
		return """
				MATCH (n:NTYPE)-[:ISSUED_BY]->(issuer:ISSUER {numistaCode: $issuerNumistaCode})
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(:COLLECTIBLE_TYPE)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)
				WITH n, issuer, currency, denomination, ct
				WHERE (coalesce($currencyNid, "") = "" OR currency.nid = $currencyNid)
				""" + DenominationNumericFilter.cypherClause() + """
				AND (coalesce($typeCodes, []) = [] OR ct.code IN $typeCodes)
				""" + issuerSearchYearFilterClause() + """
				RETURN count(DISTINCT n) AS total
				""";
	}

	private String issuerSearchDataCypher(Sort sort) {
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
				""" + DenominationNumericFilter.cypherClause() + """
				AND (coalesce($typeCodes, []) = [] OR ct.code IN $typeCodes)
				""" + issuerSearchYearFilterClause() + """
				OPTIONAL MATCH (n)-[:HAS_OBVERSE]->(obverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_REVERSE]->(reverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_VARIANT]->(variant:VARIANT)
				WHERE coalesce(variant.deletedOnNumista, false) = false
				WITH n, country, issuer, currency, denomination, ct, obverse, reverse,
				""" + variantsCollectExpression() + """
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
				"""
				+ CatalogueSortType.orderByClause(sort) + """
				SKIP toInteger($skipRows) LIMIT toInteger($pageLimit)
				""";
	}

	private String myCollectionFilterWhereClause() {
		return """
				AND (coalesce($countryNumistaCode, "") = "" OR toLower(coalesce(country.numistaCode, '')) = $countryNumistaCode)
				AND (coalesce($issuerNumistaCode, "") = "" OR issuer.numistaCode = $issuerNumistaCode)
				AND (coalesce($currencyNid, "") = "" OR currency.nid = $currencyNid)
				""" + DenominationNumericFilter.cypherClause() + """
				AND ($typesEmpty OR (ct IS NOT NULL AND ct.code IN $typeCodes))
				AND (coalesce($search, "") = "" OR toLower(coalesce(n.title, '')) CONTAINS $search)
				""";
	}

	private String myCollectionCountCypher() {
		return """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_IN_COLLECTION]->(:NUMISTA_COLLECTION_ITEM)-[:FOR_NTYPE]->(n:NTYPE)
				OPTIONAL MATCH (n)-[:ISSUED_BY]->(issuer:ISSUER)
				OPTIONAL MATCH (issuer)-[*]->(country:COUNTRY)
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(baseCt:COLLECTIBLE_TYPE)
				OPTIONAL MATCH (baseCt)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)
				WITH n, issuer, country, currency, denomination, ct
				WHERE NOT EXISTS { (ct)<-[:HAS_COLLECTIBLE_TYPE_CHILD]-() }
				"""
				+ myCollectionFilterWhereClause() + """
				RETURN count(DISTINCT n) AS total
				""";
	}

	private String myCollectionDataCypher(Sort sort) {
		return """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_IN_COLLECTION]->(:NUMISTA_COLLECTION_ITEM)-[:FOR_NTYPE]->(n:NTYPE)
				OPTIONAL MATCH (n)-[:ISSUED_BY]->(issuer:ISSUER)
				OPTIONAL MATCH (issuer)-[*]->(country:COUNTRY)
				OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
				OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
				OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(baseCt:COLLECTIBLE_TYPE)
				OPTIONAL MATCH (baseCt)<-[:HAS_COLLECTIBLE_TYPE_CHILD*0..]-(ct:COLLECTIBLE_TYPE)
				WITH n, issuer, country, currency, denomination, ct
				WHERE NOT EXISTS { (ct)<-[:HAS_COLLECTIBLE_TYPE_CHILD]-() }
				"""
				+ myCollectionFilterWhereClause() + """
				OPTIONAL MATCH (n)-[:HAS_OBVERSE]->(obverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_REVERSE]->(reverse:NTYPE_PART)
				OPTIONAL MATCH (n)-[:HAS_VARIANT]->(variant:VARIANT)
				WHERE coalesce(variant.deletedOnNumista, false) = false
				WITH DISTINCT n, country, issuer, currency, denomination, ct, obverse, reverse,
				""" + variantsCollectExpression() + """
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
				"""
				+ CatalogueSortType.orderByClause(sort) + """
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
					integerFromMap(map, "dateYear"),
					integerFromMap(map, "matchUpToGregorianYear"),
					calendarFromMap(map, "calendar"),
					stringFromMap(map, "comment"),
					marksFromMap(map, "marks"));
		}).stream().filter(Objects::nonNull).toList();
	}

	@SuppressWarnings("unchecked")
	private static List<MarkResponse> marksFromMap(Map<String, Object> map, String key) {
		Object raw = map.get(key);
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
				.filter(item -> item instanceof Map)
				.map(item -> MarkResponse.fromMap((Map<String, Object>) item))
				.filter(mark -> !mark.nid().isEmpty() || !mark.code().isEmpty() || !mark.name().isEmpty())
				.toList();
	}

	private static String stringFromMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? "" : value.toString();
	}

	private static CalendarResponse calendarFromMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (!(value instanceof Map<?, ?> calendarMap)) {
			return null;
		}
		final String code = calendarMap.get("code") == null ? "" : calendarMap.get("code").toString().trim();
		final String name = calendarMap.get("name") == null ? "" : calendarMap.get("name").toString().trim();
		if (code.isEmpty() && name.isEmpty()) {
			return null;
		}
		return new CalendarResponse(code, name);
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

	private String normalizeNid(String value) {
		return value == null ? "" : value.trim();
	}
}
