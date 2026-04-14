package com.colligendis.server.database.numista.service.techdata;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.techdata.CompositionType;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CompositionTypeService extends AbstractService {

	public Mono<ExecutionResult<CompositionType, CreateNodeExecutionStatus>> create(CompositionType compositionType,
			ColligendisUser colligendisUser, BaseLogger baseLogger) {
		return super.createNode(compositionType, colligendisUser, CompositionType.class, baseLogger);
	}

	public Mono<ExecutionResult<CompositionType, FindExecutionStatus>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, CompositionType.LABEL, CompositionType.class,
				baseLogger);
	}

	public Mono<ExecutionResult<CompositionType, ? extends ExecutionStatuses>> findByCodeOrCreate(String code,
			String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		return colligendisUserMono.flatMap(colligendisUser -> findByCode(code, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new CompositionType(code, name), colligendisUser, baseLogger);
						default:
							baseLogger.traceRed("findByCodeOrCreate: unexpected find status: {}",
									executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				}));
	}

}
