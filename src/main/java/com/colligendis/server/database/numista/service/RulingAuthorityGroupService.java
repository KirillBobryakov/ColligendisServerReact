package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.RulingAuthorityGroup;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class RulingAuthorityGroupService extends AbstractService {

	public Mono<ExecutionResult<RulingAuthorityGroup, CreateNodeExecutionStatus>> create(
			RulingAuthorityGroup rulingAuthorityGroup,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(rulingAuthorityGroup, colligendisUser,
						RulingAuthorityGroup.class, baseLogger));
	}

	public Mono<ExecutionResult<RulingAuthorityGroup, UpdateExecutionStatus>> update(
			RulingAuthorityGroup rulingAuthorityGroup,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(rulingAuthorityGroup, colligendisUser,
						RulingAuthorityGroup.class, baseLogger));
	}

	public Mono<ExecutionResult<RulingAuthorityGroup, FindExecutionStatus>> findByName(String name,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("name", name, RulingAuthorityGroup.LABEL, RulingAuthorityGroup.class,
				baseLogger);
	}

	public Mono<ExecutionResult<RulingAuthorityGroup, FindExecutionStatus>> findByNid(String nid,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, RulingAuthorityGroup.LABEL, RulingAuthorityGroup.class,
				baseLogger);
	}

	public Mono<ExecutionResult<RulingAuthorityGroup, ? extends ExecutionStatuses>> findByNidWithCreate(String nid,
			String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new RulingAuthorityGroup(nid, name), colligendisUserMono, baseLogger);
						default:
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}

}
