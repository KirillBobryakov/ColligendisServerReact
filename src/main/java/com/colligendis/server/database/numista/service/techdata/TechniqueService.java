package com.colligendis.server.database.numista.service.techdata;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.techdata.Technique;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class TechniqueService extends AbstractService {

	public Mono<ExecutionResult<Technique, CreateNodeExecutionStatus>> create(Technique technique,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(technique, colligendisUser, Technique.class, baseLogger));
	}

	public Mono<ExecutionResult<Technique, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Technique.LABEL, Technique.class, baseLogger);
	}

	public Mono<ExecutionResult<Technique, ? extends ExecutionStatuses>> findByNidOrCreate(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new Technique(nid, name), colligendisUserMono, baseLogger);
						default:
							baseLogger.traceRed("Failed to find Technique: {}", executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}
}
