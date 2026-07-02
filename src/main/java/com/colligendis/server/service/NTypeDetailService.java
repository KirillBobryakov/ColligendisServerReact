package com.colligendis.server.service;

import com.colligendis.server.controller.CatalogueController.CalendarResponse;
import com.colligendis.server.controller.CatalogueController.CollectibleTypeResponse;
import com.colligendis.server.controller.CatalogueController.CurrencyResponse;
import com.colligendis.server.controller.CatalogueController.DenominationResponse;
import com.colligendis.server.controller.CountryController.CountryResponse;
import com.colligendis.server.controller.IssuerController.IssuerResponse;
import com.colligendis.server.controller.NTypeDetailController.CatalogueReferenceResponse;
import com.colligendis.server.controller.NTypeDetailController.NamedEntityResponse;
import com.colligendis.server.controller.NTypeDetailController.NTypeDetailResponse;
import com.colligendis.server.controller.NTypeDetailController.NTypePartDetailResponse;
import com.colligendis.server.controller.NTypeDetailController.SignatureDetailResponse;
import com.colligendis.server.controller.NTypeDetailController.VariantDetailResponse;
import com.colligendis.server.database.numista.cypher.VariantYearCypher;
import com.colligendis.server.dto.MarkResponse;
import com.colligendis.server.util.LocalImageUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class NTypeDetailService {

	private static final String DETAIL_CYPHER = """
			MATCH (n:NTYPE {nid: $nid})
			OPTIONAL MATCH (n)-[:HAS_COLLECTIBLE_TYPE]->(ct:COLLECTIBLE_TYPE)
			OPTIONAL MATCH (n)-[:ISSUED_BY]->(issuer:ISSUER)
			OPTIONAL MATCH (issuer)-[*]->(country:COUNTRY)
			OPTIONAL MATCH (n)-[:HAS_CURRENCY]->(currency:CURRENCY)
			OPTIONAL MATCH (n)-[:DENOMINATED_IN]->(denomination:DENOMINATION)
			OPTIONAL MATCH (n)-[:COMMEMORATE_FOR]->(event:COMMEMORATED_EVENT)
			OPTIONAL MATCH (n)-[:WITH_SERIES]->(series:SERIES)
			OPTIONAL MATCH (n)-[:HAS_COMPOSITION]->(composition:COMPOSITION)
			OPTIONAL MATCH (composition)-[:HAS_COMPOSITION_TYPE]->(compositionType:COMPOSITION_TYPE)
			OPTIONAL MATCH (n)-[:HAS_SHAPE]->(shape:SHAPE)
			OPTIONAL MATCH (n)-[:HAS_OBVERSE]->(obverse:NTYPE_PART)
			OPTIONAL MATCH (n)-[:HAS_REVERSE]->(reverse:NTYPE_PART)
			OPTIONAL MATCH (n)-[:HAS_EDGE]->(edge:NTYPE_PART)
			OPTIONAL MATCH (n)-[:HAS_WATERMARK]->(watermark:NTYPE_PART)
			OPTIONAL MATCH (n)-[:DURING_OF_RULER]->(ruler:RULING_AUTHORITY)
			OPTIONAL MATCH (n)-[:ISSUED_BY_ISSUING_ENTITY]->(entity:ISSUING_ENTITY)
			OPTIONAL MATCH (n)-[:HAS_TECHNIQUES]->(technique:TECHNIQUE)
			OPTIONAL MATCH (n)-[:HAS_CATALOGUE_REFERENCES]->(catRef:CATALOGUE_REFERENCE)
			OPTIONAL MATCH (n)-[:PRINTED_BY]->(printer:PRINTER)
			OPTIONAL MATCH (n)-[:HAS_SPECIFIED_MINT]->(mint:SPECIFIED_MINT)
			OPTIONAL MATCH (n)-[:HAS_VARIANT]->(variant:VARIANT)
			WHERE variant IS NULL OR coalesce(variant.deletedOnNumista, false) = false
			WITH n, ct, issuer, country, currency, denomination, event, series, composition, compositionType, shape,
			     obverse, reverse, edge, watermark,
			     collect(DISTINCT CASE WHEN ruler IS NULL THEN NULL ELSE {
			       nid: ruler.nid,
			       name: ruler.name,
			       rulerType: ruler.rulerType
			     } END) AS rulingAuthorities,
			     collect(DISTINCT CASE WHEN entity IS NULL THEN NULL ELSE {
			       nid: entity.nid,
			       name: entity.name
			     } END) AS issuingEntities,
			     collect(DISTINCT CASE WHEN technique IS NULL THEN NULL ELSE technique.name END) AS techniques,
			     collect(DISTINCT CASE WHEN catRef IS NULL THEN NULL ELSE {
			       number: catRef.number,
			       catalogue: head([(catRef)-[:REFERENCE_FROM]->(cat:CATALOGUE) | coalesce(cat.code, cat.title, '')])
			     } END) AS catalogueReferences,
			     collect(DISTINCT CASE WHEN printer IS NULL THEN NULL ELSE {
			       nid: printer.nid,
			       name: printer.name
			     } END) AS printers,
			     collect(DISTINCT CASE WHEN mint IS NULL THEN NULL ELSE mint.identifier END) AS specifiedMints,
			     collect(DISTINCT CASE WHEN variant IS NULL THEN NULL ELSE {
			       nid: variant.nid,
			       mintage: variant.mintage,
			       dated: variant.dated,
			       fromGregorianYear: %s,
			       tillGregorianYear: %s,
			       dateGregorianYear: %s,
			       dateYear: %s,
			       matchUpToGregorianYear: %s,
			       calendar: %s,
			       comment: variant.comment,
			       mintLetter: variant.mintLetter,
			       catalogueReferences: [(variant)-[:HAS_CATALOGUE_REFERENCES]->(vcr:CATALOGUE_REFERENCE) | {
			         number: vcr.number,
			         catalogue: head([(vcr)-[:REFERENCE_FROM]->(cat:CATALOGUE) | coalesce(cat.code, cat.title, '')])
			       }],
			       signatures: [(variant)-[:WITH_SIGNATURE]->(sig:SIGNATURE) | {
			         nid: sig.nid,
			         name: sig.name,
			         pictureLocalPath: sig.pictureLocalPath
			       }],
			       marks: [(variant)-[:WITH_MARK]->(m:MARK) | {
			         nid: m.nid,
			         code: m.code,
			         name: m.name,
			         description: m.description,
			         pictureLocalPath: m.pictureLocalPath
			       }]
			     } END) AS variants
			RETURN
			  n.nid AS nid,
			  n.title AS title,
			  n.yearIssueDate AS yearIssueDate,
			  n.monthIssueDate AS monthIssueDate,
			  n.dayIssueDate AS dayIssueDate,
			  n.demonetized AS demonetized,
			  n.demonetizationYear AS demonetizationYear,
			  n.demonetizationMonth AS demonetizationMonth,
			  n.demonetizationDay AS demonetizationDay,
			  n.shapeAdditionalDetails AS shapeAdditionalDetails,
			  n.weight AS weight,
			  n.size AS size,
			  n.size2 AS size2,
			  n.thickness AS thickness,
			  n.techniqueAdditionalDetails AS techniqueAdditionalDetails,
			  n.alignment AS alignment,
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
			  event.name AS commemoratedEventName,
			  series.nid AS seriesNid,
			  series.name AS seriesName,
			  composition.compositionAdditionalDetails AS compositionAdditionalDetails,
			  compositionType.name AS compositionTypeName,
			  shape.nid AS shapeNid,
			  shape.name AS shapeName,
			  obverse.partType AS obversePartType,
			  obverse.description AS obverseDescription,
			  obverse.lettering AS obverseLettering,
			  obverse.unabridgedLegend AS obverseUnabridgedLegend,
			  obverse.letteringTranslation AS obverseLetteringTranslation,
			  obverse.picture AS obversePicture,
			  obverse.pictureLocalPath AS obversePictureLocalPath,
			  reverse.partType AS reversePartType,
			  reverse.description AS reverseDescription,
			  reverse.lettering AS reverseLettering,
			  reverse.unabridgedLegend AS reverseUnabridgedLegend,
			  reverse.letteringTranslation AS reverseLetteringTranslation,
			  reverse.picture AS reversePicture,
			  reverse.pictureLocalPath AS reversePictureLocalPath,
			  edge.partType AS edgePartType,
			  edge.description AS edgeDescription,
			  edge.lettering AS edgeLettering,
			  edge.unabridgedLegend AS edgeUnabridgedLegend,
			  edge.letteringTranslation AS edgeLetteringTranslation,
			  edge.picture AS edgePicture,
			  edge.pictureLocalPath AS edgePictureLocalPath,
			  watermark.partType AS watermarkPartType,
			  watermark.description AS watermarkDescription,
			  watermark.lettering AS watermarkLettering,
			  watermark.unabridgedLegend AS watermarkUnabridgedLegend,
			  watermark.letteringTranslation AS watermarkLetteringTranslation,
			  watermark.picture AS watermarkPicture,
			  watermark.pictureLocalPath AS watermarkPictureLocalPath,
			  rulingAuthorities,
			  issuingEntities,
			  techniques,
			  catalogueReferences,
			  printers,
			  specifiedMints,
			  variants
			""".formatted(
			VariantYearCypher.fromGregorianYearFor("variant"),
			VariantYearCypher.tillGregorianYearFor("variant"),
			VariantYearCypher.dateGregorianYearFor("variant"),
			VariantYearCypher.dateYearFor("variant"),
			VariantYearCypher.matchUpToGregorianYearFor("variant"),
			VariantYearCypher.calendarFor("variant"));

	private final Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	public Mono<NTypeDetailResponse> findByNid(String nid) {
		final String normalizedNid = nid == null ? "" : nid.trim();
		if (normalizedNid.isEmpty()) {
			return Mono.empty();
		}

		return Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(DETAIL_CYPHER, Map.of("nid", normalizedNid)))
						.flatMap(result -> Flux.from(result.records()))
						.map(this::mapRecord)
						.next(),
				ReactiveSession::close)
				.doOnError(error -> log.error("Failed to load NType detail for nid={}", normalizedNid, error));
	}

	private NTypeDetailResponse mapRecord(Record record) {
		return new NTypeDetailResponse(
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
				stringField(record, "commemoratedEventName"),
				namedEntityField(record, "seriesNid", "seriesName"),
				stringField(record, "yearIssueDate"),
				stringField(record, "monthIssueDate"),
				stringField(record, "dayIssueDate"),
				stringField(record, "demonetized"),
				stringField(record, "demonetizationYear"),
				stringField(record, "demonetizationMonth"),
				stringField(record, "demonetizationDay"),
				stringField(record, "compositionAdditionalDetails"),
				stringField(record, "compositionTypeName"),
				namedEntityField(record, "shapeNid", "shapeName"),
				stringField(record, "shapeAdditionalDetails"),
				doubleFieldOrNull(record, "weight"),
				doubleFieldOrNull(record, "size"),
				doubleFieldOrNull(record, "size2"),
				doubleFieldOrNull(record, "thickness"),
				stringListField(record, "techniques"),
				stringField(record, "techniqueAdditionalDetails"),
				stringField(record, "alignment"),
				mapPart(record, "obverse"),
				mapPart(record, "reverse"),
				mapPart(record, "edge"),
				mapPart(record, "watermark"),
				namedEntityListField(record, "rulingAuthorities"),
				namedEntityListField(record, "issuingEntities"),
				catalogueReferenceListField(record, "catalogueReferences"),
				namedEntityListField(record, "printers"),
				stringListField(record, "specifiedMints"),
				variantsField(record, "variants"));
	}

	private NTypePartDetailResponse mapPart(Record record, String prefix) {
		final String partType = stringField(record, prefix + "PartType");
		if (partType.isEmpty()
				&& stringField(record, prefix + "Description").isEmpty()
				&& stringField(record, prefix + "Lettering").isEmpty()) {
			return null;
		}
		final String pictureLocalPath = stringField(record, prefix + "PictureLocalPath");
		final String imageUrl = !pictureLocalPath.isEmpty() ? LocalImageUrls.toClientUrl(pictureLocalPath) : "";
		return new NTypePartDetailResponse(
				partType,
				stringField(record, prefix + "Description"),
				stringField(record, prefix + "Lettering"),
				stringField(record, prefix + "UnabridgedLegend"),
				stringField(record, prefix + "LetteringTranslation"),
				imageUrl);
	}

	private static List<VariantDetailResponse> variantsField(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return List.of();
		}
		return record.get(key).asList(value -> {
			if (value == null || value.isNull()) {
				return null;
			}
			Map<String, Object> map = value.asMap();
			return new VariantDetailResponse(
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
					stringFromMap(map, "mintLetter"),
					catalogueReferencesFromMap(map, "catalogueReferences"),
					signaturesFromMap(map, "signatures"),
					marksFromMap(map, "marks"));
		}).stream().filter(Objects::nonNull).toList();
	}

	@SuppressWarnings("unchecked")
	private static List<SignatureDetailResponse> signaturesFromMap(Map<String, Object> map, String key) {
		Object raw = map.get(key);
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
				.filter(item -> item instanceof Map)
				.map(item -> (Map<String, Object>) item)
				.map(sigMap -> {
					final String pictureLocalPath = stringFromMap(sigMap, "pictureLocalPath");
					return new SignatureDetailResponse(
							stringFromMap(sigMap, "nid"),
							stringFromMap(sigMap, "name"),
							LocalImageUrls.toClientUrl(pictureLocalPath));
				})
				.filter(sig -> !sig.nid().isEmpty() || !sig.name().isEmpty())
				.toList();
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

	@SuppressWarnings("unchecked")
	private static List<CatalogueReferenceResponse> catalogueReferencesFromMap(Map<String, Object> map, String key) {
		Object raw = map.get(key);
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
				.filter(item -> item instanceof Map)
				.map(item -> (Map<String, Object>) item)
				.map(refMap -> new CatalogueReferenceResponse(
						stringFromMap(refMap, "catalogue"),
						stringFromMap(refMap, "number")))
				.filter(ref -> !ref.number().isEmpty() || !ref.catalogue().isEmpty())
				.toList();
	}

	private static List<NamedEntityResponse> namedEntityListField(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return List.of();
		}
		return record.get(key).asList(value -> {
			if (value == null || value.isNull()) {
				return null;
			}
			Map<String, Object> map = value.asMap();
			final String nid = stringFromMap(map, "nid");
			final String name = stringFromMap(map, "name");
			if (nid.isEmpty() && name.isEmpty()) {
				return null;
			}
			return new NamedEntityResponse(nid, name, stringFromMap(map, "rulerType"));
		}).stream().filter(Objects::nonNull).toList();
	}

	private static List<CatalogueReferenceResponse> catalogueReferenceListField(
			Record record,
			String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return List.of();
		}
		return record.get(key).asList(value -> {
			if (value == null || value.isNull()) {
				return null;
			}
			final Map<String, Object> map = value.asMap();
			final String number = stringFromMap(map, "number");
			final String catalogue = stringFromMap(map, "catalogue");
			if (number.isEmpty() && catalogue.isEmpty()) {
				return null;
			}
			return new CatalogueReferenceResponse(catalogue, number);
		}).stream().filter(Objects::nonNull).toList();
	}

	private static NamedEntityResponse namedEntityField(
			Record record,
			String nidKey,
			String nameKey) {
		final String nid = stringField(record, nidKey);
		final String name = stringField(record, nameKey);
		if (nid.isEmpty() && name.isEmpty()) {
			return null;
		}
		return new NamedEntityResponse(nid, name, null);
	}

	private static List<String> stringListField(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return List.of();
		}
		return record.get(key).asList(value -> value == null || value.isNull() ? null : value.asString())
				.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.distinct()
				.toList();
	}

	private static String stringField(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return "";
		}
		return record.get(key).asString("");
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

	private static String stringFromMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? "" : value.toString().trim();
	}

	private static Double doubleFieldOrNull(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return null;
		}
		final var v = record.get(key);
		if (v.type().name().equals("INTEGER")) {
			return (double) v.asInt();
		}
		return v.asDouble();
	}
}
