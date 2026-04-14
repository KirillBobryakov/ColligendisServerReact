package com.colligendis.server.database.common.service;

import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class YearService extends AbstractService {

	public Mono<ExecutionResult<Year, FindExecutionStatus>> findByValueAndCalendar(Integer value,
			Mono<Calendar> calendarMono,
			BaseLogger baseLogger) {
		return calendarMono.flatMap(calendar -> super.findUniqueNodeByPropertyValueAndTargetNode("value", value,
				Year.LABEL, Year.class, calendar, Year.TO_NUMBER_IN, baseLogger));
	}

	public Mono<ExecutionResult<Year, CreateNodeExecutionStatus>> create(Year year, Mono<Calendar> calendarMono,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByValueAndCalendar(year.getValue(), calendarMono, baseLogger)
				.flatMap(findExecutionResult -> {
					switch (findExecutionResult.getStatus()) {
						case FOUND:
							return Mono.just(ExecutionResult.<Year, CreateNodeExecutionStatus>builder()
									.node(findExecutionResult.getNode())
									.status(CreateNodeExecutionStatus.NODE_ALREADY_EXISTS)
									.build());
						case NOT_FOUND:
							return colligendisUserMono.flatMap(
									colligendisUser -> super.createNode(year, colligendisUser, Year.class, baseLogger));
						default:
							return Mono.just(ExecutionResult.<Year, CreateNodeExecutionStatus>builder()
									.status(CreateNodeExecutionStatus.INTERNAL_ERROR)
									.build());
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCalendar(Year year,
			Mono<Calendar> calendarMono,
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
					if (FindExecutionStatus.FOUND.equals(er.getStatus()) && er.getNode() != null) {
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
					switch (findEr.getStatus()) {
						case FOUND:
							return Mono.just(findEr.getNode());
						case NOT_FOUND:
							return create(new Year(value), calendarMono, colligendisUserMono, baseLogger)
									.flatMap(createEr -> {
										switch (createEr.getStatus()) {
											case WAS_CREATED:
												return setCalendar(createEr.getNode(), calendarMono,
														colligendisUserMono, baseLogger)
														.flatMap(setExResult -> {
															switch (setExResult.getStatus()) {
																case WAS_CREATED:
																	baseLogger.trace("Calendar was set: {}",
																			setExResult.getNode());
																	return Mono.just(createEr.getNode());
																default:
																	return Mono.empty();
															}
														});
											default:
												baseLogger.traceRed("Failed to create Year: {}", createEr.getStatus());
												return Mono.empty();
										}
									});
						default:
							baseLogger.traceRed("Failed to find Year: {}", findEr.getStatus());
							return Mono.empty();
					}
				});

	}

}
