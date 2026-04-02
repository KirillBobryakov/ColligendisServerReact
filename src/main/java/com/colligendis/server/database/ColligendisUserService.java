package com.colligendis.server.database;

import org.springframework.stereotype.Service;

import com.colligendis.server.logger.BaseLogger;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ColligendisUserService extends AbstractService {

	private static final String NUMISTA_PARSER_USER = "NUMISTA_PARSER";

	private Mono<ColligendisUser> numistaParserUserMono;

	@PostConstruct
	public void init() {
		this.numistaParserUserMono = findByUsername(NUMISTA_PARSER_USER, new BaseLogger()).cache();
	}

	public Mono<ColligendisUser> getNumistaParserUserMono() {
		if (numistaParserUserMono == null) {
			throw new IllegalStateException(
					"numistaParserUserMono not initialized - ensure ColligendisUserService @PostConstruct has run");
		}
		return numistaParserUserMono;
	}

	public Mono<ColligendisUser> create(ColligendisUser colligendisUser, BaseLogger baseLogger) {
		return super.createNode(colligendisUser, null, ColligendisUser.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("Colligendis user was created: {}", executionResult.getNode());
						return Mono.just(executionResult.getNode());
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("Colligendis user was not created: {}", executionResult.getNode());
						return Mono.empty();
					} else {
						baseLogger.traceRed("Failed to create colligendis user: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.empty();
					}
				});
	}

	public Mono<ColligendisUser> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, ColligendisUser.LABEL, ColligendisUser.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("Colligendis user was found: {}", executionResult.getNode());
						return Mono.just(executionResult.getNode());
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceOrange("Colligendis user was not found: {}", executionResult.getNode());
						return Mono.empty();
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one colligendis user was found: {}", executionResult.getNode());
						return Mono.empty();
					} else {
						baseLogger.traceRed("Failed to find colligendis user: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.empty();
					}
				});
	}

	public Mono<ColligendisUser> findByUsername(String username, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("username", username, ColligendisUser.LABEL,
				ColligendisUser.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("Colligendis user was found: {}", executionResult.getNode());
						return Mono.just(executionResult.getNode());
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceOrange("Colligendis user was not found: {}", executionResult.getNode());
						return Mono.empty();
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one colligendis user was found: {}", executionResult.getNode());
						return Mono.empty();
					} else {
						baseLogger.traceRed("Failed to find colligendis user: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.empty();
					}
				});
	}

}
