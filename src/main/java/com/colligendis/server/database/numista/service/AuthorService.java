package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Author;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class AuthorService extends AbstractService {

	public Mono<ExecutionResult<Author, CreateNodeExecutionStatus>> create(Author author,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(author, colligendisUser, Author.class, baseLogger));
	}

	public Mono<ExecutionResult<Author, FindExecutionStatus>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, Author.LABEL, Author.class, baseLogger);
	}

	public Mono<ExecutionResult<Author, ? extends ExecutionStatuses>> findByCodeWithSave(String code, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByCode(code, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new Author(code, name), colligendisUserMono, baseLogger);
						default:
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}
}
