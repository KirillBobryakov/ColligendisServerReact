package com.colligendis.server.database.common.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.logger.BaseLogger;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
public class CalendarService extends AbstractService {

	public static Mono<Calendar> GREGORIAN;

	@PostConstruct
	public void init() {
		GREGORIAN = Mono.defer(() -> findByCode(Calendar.GREGORIAN_CODE, new BaseLogger())
				.flatMap(er -> {
					if (ExecutionStatus.NODE_IS_FOUND.equals(er.getStatus()) && er.getNode() != null) {
						return Mono.just(er.getNode());
					}
					String detail = er.getError() != null ? er.getError().message() : String.valueOf(er.getStatus());
					return Mono.<Calendar>error(new IllegalStateException(
							"Gregorian calendar (code " + Calendar.GREGORIAN_CODE + ") not available: " + detail));
				})).cache();
	}

	public Mono<ExecutionResult<Calendar>> create(Calendar calendar, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(calendar, colligendisUser, Calendar.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("Calendar was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("Calendar was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create Calendar: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Calendar>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, Calendar.LABEL, Calendar.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("Calendar was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Calendar was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one Calendar was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find Calendar: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}
}
