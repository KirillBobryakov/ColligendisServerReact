package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class SeriesService extends AbstractService {

	public Mono<ExecutionResult<Series, CreateNodeExecutionStatus>> create(Series series,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(series, colligendisUser, Series.class, baseLogger));
	}

	public Mono<ExecutionResult<Series, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Series.LABEL, Series.class, baseLogger);
	}

	public Mono<ExecutionResult<Series, ? extends ExecutionStatuses>> findByNidWithCreate(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new Series(nid, name), colligendisUserMono, baseLogger);
						default:
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}
}
