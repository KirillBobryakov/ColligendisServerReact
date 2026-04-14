package com.colligendis.server.database.common.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
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
					if (FindExecutionStatus.FOUND.equals(er.getStatus()) && er.getNode() != null) {
						return Mono.just(er.getNode());
					}
					String detail = er.getError() != null ? er.getError().message() : String.valueOf(er.getStatus());
					return Mono.<Calendar>error(new IllegalStateException(
							"Gregorian calendar (code " + Calendar.GREGORIAN_CODE + ") not available: " + detail));
				})).cache();
	}

	public Mono<ExecutionResult<Calendar, CreateNodeExecutionStatus>> create(Calendar calendar,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(calendar, colligendisUser, Calendar.class, baseLogger))
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case WAS_CREATED:
							baseLogger.trace("Calendar was created: {}", executionResult.getNode());
							return Mono.just(executionResult);
						case NOT_CREATED:
							baseLogger.traceRed("Calendar was not created: {}", executionResult.getNode());
							return Mono.just(executionResult);
						default:
							baseLogger.traceRed("Failed to create Calendar: {}", executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Calendar, FindExecutionStatus>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, Calendar.LABEL, Calendar.class, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							baseLogger.trace("Calendar was found: {}", executionResult.getNode());
							return Mono.just(executionResult);
						case NOT_FOUND:
							baseLogger.traceRed("Calendar was not found: {}", executionResult.getNode());
							return Mono.just(executionResult);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed("More than one Calendar was found: {}", executionResult.getNode());
							return Mono.just(executionResult);
						default:
							baseLogger.traceRed("Failed to find Calendar: {}", executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}
}
