package com.colligendis.server.database.numista.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class IssuingEntityService extends AbstractService {

	public Mono<ExecutionResult<IssuingEntity>> create(IssuingEntity issuingEntity,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(issuingEntity, colligendisUser, IssuingEntity.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create IssuingEntity: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<IssuingEntity>> update(IssuingEntity issuingEntity,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(issuingEntity, colligendisUser,
						IssuingEntity.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_UPDATED)) {
						baseLogger.trace("IssuingEntity updated: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("IssuingEntity not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOTHING_TO_UPDATE)) {
						baseLogger.traceOrange("IssuingEntity has no properties to update: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to update IssuingEntity: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<IssuingEntity>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, IssuingEntity.LABEL,
				IssuingEntity.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("IssuingEntity found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceOrange("IssuingEntity not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one IssuingEntity found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find IssuingEntity: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<IssuingEntity>> findByNidWithSave(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						return create(new IssuingEntity(nid, name), colligendisUserMono, baseLogger);
					} else {
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				})
				.flatMap(entityExecutionResult -> {
					if (entityExecutionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)
							|| entityExecutionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {

						IssuingEntity entity = entityExecutionResult.getNode();
						if (!entity.getName().equals(name)) {
							entity.setName(name);
							return update(entity, colligendisUserMono, baseLogger);
						}
						return Mono.just(entityExecutionResult);
					} else {
						baseLogger.traceRed("Failed to find or create IssuingEntity: {}",
								entityExecutionResult.getStatus());
						entityExecutionResult.logError(baseLogger);
						return Mono.just(entityExecutionResult);
					}
				});
	}

	public Flux<IssuingEntity> getAllIssuingEntities(Issuer issuer, BaseLogger baseLogger) {
		return super.getAllSourceNodesWithRelationshipType(issuer, IssuingEntity.ISSUES_WHEN_BEEN, IssuingEntity.class,
				baseLogger)
				.map(ExecutionResult::getNode);
	}

	public Mono<ExecutionResult<AbstractNode>> setIssuer(Issuer issuer, List<IssuingEntity> issuingEntities,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueIncomingRelationships(issuer, issuingEntities,
						IssuingEntity.class,
						IssuingEntity.ISSUES_WHEN_BEEN, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set Issuer: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

}
