package com.colligendis.server.database.numista.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CurrencyService extends AbstractService {

	public Mono<ExecutionResult<Currency>> create(Currency currency, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(currency, colligendisUser, Currency.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("Currency was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("Currency was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create Currency: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Currency>> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, Currency.LABEL, Currency.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Currency was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one Currency was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find Currency: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Currency>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Currency.LABEL, Currency.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Currency was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one Currency was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find Currency: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Currency>> findByNidWithSave(String nid, String fullName, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						return create(new Currency(nid, fullName, name), colligendisUserMono, baseLogger);
					} else {
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Currency>> update(Currency currency,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(currency, colligendisUser, Currency.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_UPDATED)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Currency was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOTHING_TO_UPDATE)) {
						baseLogger.traceOrange("Currency has no properties to update: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to update Currency: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setCirculatedFromYears(Currency currency, List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(currency, years, Year.class,
						Currency.CIRCULATED_FROM,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set Circulated From Years: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setCirculatedTillYears(Currency currency, List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(currency, years, Year.class,
						Currency.CIRCULATED_TILL,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set Circulated Till Years: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setIssuer(Currency currency, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createSingleRelationship(currency, issuer,
						Currency.CIRCULATE_WHEN_BEEN,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set Issuer: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

}
