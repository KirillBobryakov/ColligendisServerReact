package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.CommemoratedEvent;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class CommemoratedEventService extends AbstractService {

	public Mono<ExecutionResult<CommemoratedEvent, CreateNodeExecutionStatus>> create(
			CommemoratedEvent commemoratedEvent,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(commemoratedEvent, colligendisUser,
						CommemoratedEvent.class, baseLogger));
	}

	public Mono<ExecutionResult<CommemoratedEvent, FindExecutionStatus>> findByName(String name,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("name", name, CommemoratedEvent.LABEL, CommemoratedEvent.class,
				baseLogger);
	}

	public Mono<ExecutionResult<CommemoratedEvent, ? extends ExecutionStatuses>> findByNameWithCreate(String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByName(name, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new CommemoratedEvent(name), colligendisUserMono, baseLogger);
						default:
							return Mono.just(executionResult);
					}
				});
	}

}
