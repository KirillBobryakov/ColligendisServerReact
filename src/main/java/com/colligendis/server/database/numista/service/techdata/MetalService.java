package com.colligendis.server.database.numista.service.techdata;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.techdata.Metal;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class MetalService extends AbstractService {

	public Mono<ExecutionResult<Metal, CreateNodeExecutionStatus>> create(Metal metal,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(metal, colligendisUser, Metal.class, baseLogger));
	}

	public Mono<ExecutionResult<Metal, FindExecutionStatus>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, Metal.LABEL, Metal.class, baseLogger);
	}

	public Mono<ExecutionResult<Metal, ? extends ExecutionStatuses>> findByNidOrNameOrCreate(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		Mono<ExecutionResult<Metal, FindExecutionStatus>> metalMono = Mono.empty();

		if (nid == null || nid.isEmpty()) {
			metalMono = super.findNodeByUniquePropertyValue("name", name, Metal.LABEL, Metal.class, baseLogger);
		} else {
			metalMono = super.findNodeByUniquePropertyValue("nid", nid, Metal.LABEL, Metal.class, baseLogger);
		}

		return metalMono
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new Metal(nid, name), colligendisUserMono, baseLogger);
						default:
							baseLogger.traceRed("Failed to find Metal: {}", executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}

}