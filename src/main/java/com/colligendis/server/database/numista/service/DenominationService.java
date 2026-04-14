package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DenominationService extends AbstractService {

	public Mono<ExecutionResult<Denomination, CreateNodeExecutionStatus>> create(Denomination denomination,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(denomination, colligendisUser, Denomination.class,
						baseLogger));
	}

	public Mono<ExecutionResult<Denomination, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Denomination.LABEL, Denomination.class, baseLogger);
	}

	public Mono<ExecutionResult<Denomination, ? extends ExecutionStatuses>> findByNidWithCreate(String nid, String name,
			String fullName,
			Float numericValue, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new Denomination(nid, name, fullName, numericValue), colligendisUserMono,
									baseLogger);
						default:
							return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Denomination, UpdateExecutionStatus>> update(Denomination denomination,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(denomination, colligendisUser,
						Denomination.class, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCurrency(Denomination denomination,
			Currency currency,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(denomination, currency,
						Denomination.UNDER_CURRENCY,
						colligendisUser, baseLogger));
	}
}
