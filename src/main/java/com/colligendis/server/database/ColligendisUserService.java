package com.colligendis.server.database;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ColligendisUserService extends AbstractService {

	private static final String NUMISTA_PARSER_USER = "NUMISTA_PARSER";

	private Mono<ColligendisUser> numistaParserUserMono;

	@PostConstruct
	public void init() {
		this.numistaParserUserMono = findByUsername(NUMISTA_PARSER_USER, new BaseLogger())
				.map(executionResult -> executionResult.getNode()).cache();
	}

	public Mono<ColligendisUser> getNumistaParserUserMono() {
		if (numistaParserUserMono == null) {
			throw new IllegalStateException(
					"numistaParserUserMono not initialized - ensure ColligendisUserService @PostConstruct has run");
		}
		return numistaParserUserMono;
	}

	public Mono<ExecutionResult<ColligendisUser, CreateNodeExecutionStatus>> create(ColligendisUser colligendisUser,
			BaseLogger baseLogger) {
		return super.createNode(colligendisUser, null, ColligendisUser.class, baseLogger)
				.flatMap(executionResult -> Mono.just(executionResult));
	}

	public Mono<ExecutionResult<ColligendisUser, FindExecutionStatus>> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, ColligendisUser.LABEL, ColligendisUser.class, baseLogger);
	}

	public Mono<ExecutionResult<ColligendisUser, FindExecutionStatus>> findByUsername(String username,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("username", username, ColligendisUser.LABEL,
				ColligendisUser.class, baseLogger)
				.flatMap(executionResult -> Mono.just(executionResult));
	}

}
