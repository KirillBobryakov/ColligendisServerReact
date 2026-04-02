package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.RulingAuthorityGroup;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class RulingAuthorityGroupService extends AbstractService {

	public Mono<ExecutionResult<RulingAuthorityGroup>> create(RulingAuthorityGroup rulingAuthorityGroup,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(rulingAuthorityGroup, colligendisUser,
						RulingAuthorityGroup.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("RulingAuthorityGroup was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("RulingAuthorityGroup was not created: {}", executionResult.getStatus());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create RulingAuthorityGroup: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<RulingAuthorityGroup>> update(RulingAuthorityGroup rulingAuthorityGroup,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(rulingAuthorityGroup, colligendisUser,
						RulingAuthorityGroup.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_UPDATED)) {
						baseLogger.trace("RulingAuthorityGroup was updated: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("RulingAuthorityGroup was not updated: {}", executionResult.getStatus());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOTHING_TO_UPDATE)) {
						baseLogger.traceRed("RulingAuthorityGroup has no properties to update: {}",
								executionResult.getStatus());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to update RulingAuthorityGroup: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<RulingAuthorityGroup>> findByName(String name, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("name", name, RulingAuthorityGroup.LABEL, RulingAuthorityGroup.class,
				baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("RulingAuthorityGroup was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("RulingAuthorityGroup was not found: {}", executionResult.getStatus());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one RulingAuthorityGroup was found: {}",
								executionResult.getStatus());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find RulingAuthorityGroup: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<RulingAuthorityGroup>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, RulingAuthorityGroup.LABEL, RulingAuthorityGroup.class,
				baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("RulingAuthorityGroup was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("RulingAuthorityGroup was not found: {}", executionResult.getStatus());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one RulingAuthorityGroup was found: {}",
								executionResult.getStatus());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find RulingAuthorityGroup: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<RulingAuthorityGroup>> findByNidWithSave(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						return create(new RulingAuthorityGroup(nid, name), colligendisUserMono, baseLogger);
					} else {
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

}
