package com.colligendis.server.controller;

import java.util.List;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/public/subjects")
@RequiredArgsConstructor
public class SubjectController {

	private final Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	private static final String SUBJECT_LIVE_FILTER = """
			  AND s.uuid IS NOT NULL
			  AND NONE(l IN labels(s) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
			""";

	@GetMapping
	public Mono<List<SubjectResponse>> getAllSubjects() {
		final String cypher = """
				MATCH (s:SUBJECT)
				WHERE s.name IS NOT NULL AND trim(s.name) <> ''
				""" + SUBJECT_LIVE_FILTER + """
				RETURN s.numistaCode AS numistaCode, s.name AS name
				ORDER BY toLower(s.name)
				""";

		log.info("Request to get all subjects");
		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class,
						SessionConfig.builder().withDatabase(neo4jDatabase).build())),
				session -> Flux.from(session.run(cypher))
						.flatMap(result -> Flux.from(result.records()))
						.map(record -> new SubjectResponse(
								record.get("numistaCode").isNull() ? "" : record.get("numistaCode").asString(),
								record.get("name").isNull() ? "" : record.get("name").asString())),
				ReactiveSession::close)
				.distinct(dto -> "%s|%s".formatted(dto.numistaCode(), dto.name()))
				.collectList()
				.doOnSuccess(subjects -> log.info("Subjects list returned successfully, count={}", subjects.size()));
	}

	public record SubjectResponse(String numistaCode, String name) {
	}

}
