package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CollectibleTypeService extends AbstractService {

	public Mono<ExecutionResult<CollectibleType, CreateNodeExecutionStatus>> create(CollectibleType collectibleType,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(collectibleType, colligendisUser, CollectibleType.class,
						baseLogger));
	}

	public Mono<ExecutionResult<CollectibleType, FindExecutionStatus>> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, CollectibleType.LABEL, CollectibleType.class, baseLogger);
	}

	public Mono<String> getCollectibleTypeCodeByUuid(String uuid, BaseLogger baseLogger) {
		return findByUuid(uuid, baseLogger)
				.map(executionResult -> executionResult.getNode().getCode());
	}

	public Mono<ExecutionResult<CollectibleType, FindExecutionStatus>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, CollectibleType.LABEL, CollectibleType.class,
				baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> linkParentChild(
			CollectibleType parent,
			CollectibleType child, BaseLogger baseLogger) {
		return super.createSingleRelationship(parent, child, CollectibleType.HAS_COLLECTIBLE_TYPE_CHILD, null,
				baseLogger);

	}

	public Mono<ExecutionResult<CollectibleType, FindExecutionStatus>> findTopCollectibleTypeRecursive(
			CollectibleType collectibleType, BaseLogger baseLogger) {
		return super.getAllSourceNodesWithRelationshipType(collectibleType, CollectibleType.HAS_COLLECTIBLE_TYPE_CHILD,
				CollectibleType.class, baseLogger)
				.next()
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case NOT_FOUND:
							return Mono.just(ExecutionResult.<CollectibleType, FindExecutionStatus>builder()
									.node(collectibleType)
									.status(FindExecutionStatus.FOUND)
									.build());
						case FOUND:
							return findTopCollectibleTypeRecursive(executionResult.getNode(), baseLogger);
						default:
							return Mono.just(executionResult);
					}
				})
				.switchIfEmpty(Mono.just(ExecutionResult.<CollectibleType, FindExecutionStatus>builder()
						.node(collectibleType)
						.status(FindExecutionStatus.FOUND)
						.build()));
	}

}
