package com.colligendis.server.database.common.service;

import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class YearService extends AbstractService {

	public Mono<ExecutionResult<Year>> findByValueAndCalendar(Integer value, Mono<Calendar> calendarMono,
			BaseLogger baseLogger) {
		return calendarMono.flatMap(calendar -> super.findUniqueNodeByPropertyValueAndTargetNode("value", value,
				Year.LABEL, Year.class, calendar, Year.TO_NUMBER_IN, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("Year was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Year was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one Year was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find Year: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Year>> create(Year year, Mono<Calendar> calendarMono,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByValueAndCalendar(year.getValue(), calendarMono, baseLogger)
				.flatMap(findExecutionResult -> {
					if (findExecutionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(findExecutionResult);
					} else if (findExecutionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						return colligendisUserMono
								.flatMap(colligendisUser -> super.createNode(year, colligendisUser, Year.class,
										baseLogger))
								.flatMap(createExecutionResult -> {
									if (createExecutionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
										baseLogger.trace("Year was created: {}", createExecutionResult.getNode());
										// After creation, set the Calendar relationship
										return setCalendar((Year) createExecutionResult.getNode(), calendarMono,
												colligendisUserMono, baseLogger)
												.thenReturn(createExecutionResult);
									} else {
										baseLogger.traceRed("Failed to create Year: {}",
												createExecutionResult.getStatus());
										createExecutionResult.logError(baseLogger);
										return Mono.just(createExecutionResult);
									}
								});
					} else {
						baseLogger.traceRed("Failed to find Year: {}", findExecutionResult.getStatus());
						findExecutionResult.logError(baseLogger);
						return Mono.just(findExecutionResult);
					}
				});
		// .switchIfEmpty(
		// colligendisUserMono.flatMap(user -> {
		// Year newYear = new Year(year.getValue());
		// return this.createNode(newYear, user, Year.class)
		// .flatMap(either -> either.reactiveFoldOrElse(Mono::empty))
		// .flatMap(createdYear -> setCalendar(createdYear, calendarMono,
		// colligendisUserMono)
		// .thenReturn(createdYear));
		// }));
	}

	public Mono<ExecutionResult<AbstractNode>> setCalendar(Year year, Mono<Calendar> calendarMono,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return calendarMono.zipWhen(calendar -> colligendisUserMono)
				.flatMap(tuple -> {
					Calendar calendar = tuple.getT1();
					ColligendisUser colligendisUser = tuple.getT2();
					return super.createSingleRelationship(year, calendar, Year.TO_NUMBER_IN, colligendisUser,
							baseLogger)
							.doOnNext(er -> {
								if (er.getError() != null) {
									log.error(
											"YearService.setCalendar: Error while creating connection from Year with value: {} to Calendar with code: {} and name: {}. Error: {}",
											year.getValue(), calendar.getCode(), calendar.getName(),
											er.getError());
								}
							});
				});
	}

	public Mono<Year> findGregorianYearByValue(Integer value) {
		BaseLogger baseLogger = new BaseLogger();
		return findByValueAndCalendar(value, CalendarService.GREGORIAN, baseLogger)
				.flatMap(er -> {
					if (ExecutionStatus.NODE_IS_FOUND.equals(er.getStatus()) && er.getNode() != null) {
						return Mono.just(er.getNode());
					}
					return Mono.empty();
				});
	}

	public Mono<Year> findYearByValueWithCreate(Integer value, Mono<Calendar> calendarMono,
			Mono<ColligendisUser> colligendisUserMono) {
		BaseLogger baseLogger = new BaseLogger();
		return findByValueAndCalendar(value, calendarMono, baseLogger)
				.flatMap(findEr -> {
					if (ExecutionStatus.NODE_IS_FOUND.equals(findEr.getStatus()) && findEr.getNode() != null) {
						return Mono.just(findEr.getNode());
					}
					if (ExecutionStatus.NODE_IS_NOT_FOUND.equals(findEr.getStatus())) {
						return create(new Year(value), calendarMono, colligendisUserMono, baseLogger)
								.flatMap(createEr -> {
									if (!ExecutionStatus.NODE_WAS_CREATED.equals(createEr.getStatus())
											|| createEr.getNode() == null) {
										return Mono.empty();
									}
									Year created = createEr.getNode();
									return setCalendar(created, calendarMono, colligendisUserMono, baseLogger)
											.thenReturn(created);
								});
					}
					return Mono.empty();
				});
	}

}
