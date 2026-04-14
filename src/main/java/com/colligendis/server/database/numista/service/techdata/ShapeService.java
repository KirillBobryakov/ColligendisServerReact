package com.colligendis.server.database.numista.service.techdata;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.techdata.Shape;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class ShapeService extends AbstractService {

	public Mono<ExecutionResult<Shape, CreateNodeExecutionStatus>> create(Shape shape,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(shape, colligendisUser, Shape.class, baseLogger));
	}

	public Mono<ExecutionResult<Shape, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Shape.LABEL, Shape.class, baseLogger);
	}

	public Mono<ExecutionResult<Shape, ? extends ExecutionStatuses>> findByNidOrCreate(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new Shape(nid, name), colligendisUserMono, baseLogger);
						default:
							baseLogger.traceRed("Failed to find Shape: {}", executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}

}