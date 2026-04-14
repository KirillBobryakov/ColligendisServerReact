package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class IssuingEntityService extends AbstractService {

	public Mono<ExecutionResult<IssuingEntity, CreateNodeExecutionStatus>> create(IssuingEntity issuingEntity,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(issuingEntity, colligendisUser, IssuingEntity.class,
						baseLogger));
	}

	public Mono<ExecutionResult<IssuingEntity, UpdateExecutionStatus>> update(IssuingEntity issuingEntity,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(issuingEntity, colligendisUser,
						IssuingEntity.class,
						baseLogger));
	}

	public Mono<ExecutionResult<IssuingEntity, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, IssuingEntity.LABEL,
				IssuingEntity.class, baseLogger);
	}

	public Mono<ExecutionResult<IssuingEntity, ? extends ExecutionStatuses>> findByNidWithCreate(String nid,
			String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							IssuingEntity issuingEntity = executionResult.getNode();
							if (!issuingEntity.getName().equals(name)) {
								issuingEntity.setName(name);
								return update(issuingEntity, colligendisUserMono, baseLogger);
							} else {
								return Mono.just(executionResult);
							}
						case NOT_FOUND:
							return create(new IssuingEntity(nid, name), colligendisUserMono, baseLogger);
						default:
							return Mono.just(executionResult);
					}
				});
	}

	public Flux<IssuingEntity> getAllIssuingEntities(Issuer issuer, BaseLogger baseLogger) {
		return super.getAllSourceNodesWithRelationshipType(issuer, IssuingEntity.ISSUES_WHEN_BEEN, IssuingEntity.class,
				baseLogger)
				.map(ExecutionResult::getNode);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setIssuer(Issuer issuer,
			List<IssuingEntity> issuingEntities,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueIncomingRelationships(issuer, issuingEntities,
						IssuingEntity.class,
						IssuingEntity.ISSUES_WHEN_BEEN, colligendisUser, baseLogger));
	}

}
