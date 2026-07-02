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

	public Mono<ExecutionResult<Year, FindExecutionStatus>> findByDateYearAndCalendar(Integer dateYear,
			Mono<Calendar> calendarMono,
			BaseLogger baseLogger) {
		return calendarMono.flatMap(calendar -> super.findUniqueNodeByPropertyValueAndTargetNode("dateYear", dateYear,
				Year.LABEL, Year.class, calendar, Year.TO_NUMBER_IN, baseLogger));
	}

	public Mono<ExecutionResult<Year, CreateNodeExecutionStatus>> create(Year year, Mono<Calendar> calendarMono,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByDateYearAndCalendar(year.getDateYear(), calendarMono, baseLogger)
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
											"YearService.setCalendar: Error while creating connection from Year with dateYear: {} to Calendar with code: {} and name: {}. Error: {}",
											year.getDateYear(), calendar.getCode(), calendar.getName(),
											er.getError());
								}
							});
				});
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> linkToGregorianYear(Year calendarYear,
			Year gregorianYear, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createSingleRelationship(calendarYear, gregorianYear,
						Year.MATCH_UP_TO_GREGORIAN, colligendisUser, baseLogger));
	}

	public Mono<Year> findGregorianYearByDateYear(Integer dateYear) {
		BaseLogger baseLogger = new BaseLogger();
		return findByDateYearAndCalendar(dateYear, CalendarService.GREGORIAN, baseLogger)
				.flatMap(er -> {
					if (FindExecutionStatus.FOUND.equals(er.getStatus()) && er.getNode() != null) {
						return Mono.just(er.getNode());
					}
					return Mono.empty();
				});
	}

	public Mono<Year> findYearByDateYearWithCreate(Integer dateYear, Mono<Calendar> calendarMono,
			Mono<ColligendisUser> colligendisUserMono) {
		BaseLogger baseLogger = new BaseLogger();
		return calendarMono.flatMap(calendar -> findByDateYearAndCalendar(dateYear, Mono.just(calendar), baseLogger)
				.flatMap(findEr -> {
					switch (findEr.getStatus()) {
						case FOUND:
							return Mono.just(findEr.getNode());
						case NOT_FOUND:
							return create(new Year(dateYear), Mono.just(calendar), colligendisUserMono, baseLogger)
									.flatMap(createEr -> {
										switch (createEr.getStatus()) {
											case WAS_CREATED:
												return setCalendar(createEr.getNode(), Mono.just(calendar),
														colligendisUserMono, baseLogger)
														.flatMap(setExResult -> {
															switch (setExResult.getStatus()) {
																case WAS_CREATED:
																	baseLogger.trace("Calendar was set: {}",
																			setExResult.getNode());
																	return linkNewYearToGregorianIfNeeded(
																			createEr.getNode(), dateYear, calendar,
																			colligendisUserMono, baseLogger);
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
				}));
	}

	private Mono<Year> linkNewYearToGregorianIfNeeded(Year calendarYear, Integer dateYear, Calendar calendar,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		if (Calendar.GREGORIAN_CODE.equals(calendar.getCode())) {
			return Mono.just(calendarYear);
		}
		Integer shift = calendar.getToGregorianShift();
		if (shift == null) {
			baseLogger.warning(
					"YearService: skip MATCH_UP_TO_GREGORIAN for dateYear {} in calendar {} — no toGregorianShift",
					dateYear, calendar.getCode());
			return Mono.just(calendarYear);
		}
		int gregorianDateYear = dateYear + shift;
		return findYearByDateYearWithCreate(gregorianDateYear, CalendarService.GREGORIAN, colligendisUserMono)
				.flatMap(gregorianYear -> linkToGregorianYear(calendarYear, gregorianYear, colligendisUserMono,
						baseLogger)
						.doOnNext(er -> {
							if (er.getError() != null) {
								baseLogger.error(
										"YearService: failed to link year {} ({}) to Gregorian year {}: {}",
										dateYear, calendar.getCode(), gregorianDateYear, er.getError());
							}
						})
						.thenReturn(calendarYear));
	}

}
