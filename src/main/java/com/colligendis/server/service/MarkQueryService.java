package com.colligendis.server.service;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.colligendis.server.dto.MarkResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarkQueryService {

	private static final String MARK_BY_NID_CYPHER = """
			MATCH (m:MARK {nid: $nid})
			RETURN m.nid AS nid,
			       m.code AS code,
			       m.name AS name,
			       m.description AS description,
			       m.pictureLocalPath AS pictureLocalPath
			LIMIT 1
			""";

	private static final String MARKS_BY_VARIANT_NID_CYPHER = """
			MATCH (v:VARIANT {nid: $variantNid})-[:WITH_MARK]->(m:MARK)
			RETURN m.nid AS nid,
			       m.code AS code,
			       m.name AS name,
			       m.description AS description,
			       m.pictureLocalPath AS pictureLocalPath
			ORDER BY m.nid
			""";

	private final Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	public Mono<MarkResponse> findByNid(String nid) {
		final String normalizedNid = nid == null ? "" : nid.trim();
		if (normalizedNid.isEmpty()) {
			return Mono.empty();
		}
		return Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(MARK_BY_NID_CYPHER, Map.of("nid", normalizedNid)))
						.flatMap(result -> Flux.from(result.records()))
						.map(this::mapRecord)
						.next(),
				ReactiveSession::close)
				.doOnError(error -> log.error("Failed to load mark nid={}", normalizedNid, error));
	}

	public Mono<List<MarkResponse>> findByVariantNid(String variantNid) {
		final String normalizedVariantNid = variantNid == null ? "" : variantNid.trim();
		if (normalizedVariantNid.isEmpty()) {
			return Mono.just(List.of());
		}
		return Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(
						ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(MARKS_BY_VARIANT_NID_CYPHER, Map.of("variantNid", normalizedVariantNid)))
						.flatMap(result -> Flux.from(result.records()))
						.map(this::mapRecord)
						.collectList(),
				ReactiveSession::close)
				.doOnError(error -> log.error("Failed to load marks for variantNid={}", normalizedVariantNid, error));
	}

	private MarkResponse mapRecord(Record record) {
		return MarkResponse.fromMap(Map.of(
				"nid", stringField(record, "nid"),
				"code", stringField(record, "code"),
				"name", stringField(record, "name"),
				"description", stringField(record, "description"),
				"pictureLocalPath", stringField(record, "pictureLocalPath")));
	}

	private static String stringField(Record record, String key) {
		if (!record.containsKey(key) || record.get(key).isNull()) {
			return "";
		}
		return record.get(key).asString("");
	}
}
