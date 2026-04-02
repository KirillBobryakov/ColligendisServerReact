package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class SeriesService extends AbstractService {

	public Mono<ExecutionResult<Series>> create(Series series, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(series, colligendisUser, Series.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("Series was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("Series was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create Series: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Series>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Series.LABEL, Series.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("Series was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Series was not found: {}", executionResult.getStatus());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one Series was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find Series: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Series>> findByNidWithSave(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						return create(new Series(nid, name), colligendisUserMono, baseLogger);
					} else {
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}
}
