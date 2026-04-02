package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionResults;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CollectibleTypeService extends AbstractService {

	public Mono<ExecutionResult<CollectibleType>> create(CollectibleType collectibleType,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(collectibleType, colligendisUser, CollectibleType.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("CollectibleType was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("CollectibleType was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create CollectibleType: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<CollectibleType>> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, CollectibleType.LABEL, CollectibleType.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("CollectibleType was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one CollectibleType was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find CollectibleType: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<String> getCollectibleTypeCodeByUuid(String uuid, BaseLogger baseLogger) {
		return findByUuid(uuid, baseLogger)
				.map(executionResult -> executionResult.getNode().getCode());
	}

	public Mono<ExecutionResult<CollectibleType>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, CollectibleType.LABEL, CollectibleType.class,
				baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("CollectibleType was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("CollectibleType was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one CollectibleType was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find CollectibleType: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> linkParentChild(CollectibleType parent,
			CollectibleType child, BaseLogger baseLogger) {
		return super.createSingleRelationship(parent, child, CollectibleType.HAS_COLLECTIBLE_TYPE_CHILD, null,
				baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to link Parent Child: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});

	}

	private Mono<ExecutionResult<CollectibleType>> findTopCollectibleTypeRecursive(
			CollectibleType collectibleType, BaseLogger baseLogger) {
		return super.getAllSourceNodesWithRelationshipType(collectibleType, CollectibleType.HAS_COLLECTIBLE_TYPE_CHILD,
				CollectibleType.class, baseLogger)
				.next()
				.flatMap(executionResult -> {
					if (executionResult.getError() != null) {
						return Mono.just(ExecutionResult.<CollectibleType>builder()
								.status(ExecutionStatus.ERROR)
								.error(executionResult.getError())
								.build());
					}
					CollectibleType parent = executionResult.getNode();
					if (parent == null) {
						return ExecutionResults.<CollectibleType>INPUT_PARAMETERS_ERROR_MONO(
								"findTopCollectibleTypeRecursive",
								"Parent collectible type node is null");
					}
					return findTopCollectibleTypeRecursive(parent, baseLogger);
				})
				.switchIfEmpty(Mono.defer(() -> Mono.just(ExecutionResult.<CollectibleType>builder()
						.node(collectibleType)
						.status(ExecutionStatus.NODE_IS_FOUND)
						.build())));
	}

	/**
	 * Finds the top-level (root) collectible type by traversing parent
	 * relationships.
	 */
	public Mono<ExecutionResult<CollectibleType>> findTopCollectibleType(CollectibleType collectibleType,
			BaseLogger baseLogger) {
		if (collectibleType == null || collectibleType.getUuid() == null) {
			baseLogger.traceRed("CollectibleType or uuid is null");
			return ExecutionResults.<CollectibleType>INPUT_PARAMETERS_ERROR_MONO("findTopCollectibleType",
					"CollectibleType or uuid is null");
		}
		return findTopCollectibleTypeRecursive(collectibleType, baseLogger);
	}

}
