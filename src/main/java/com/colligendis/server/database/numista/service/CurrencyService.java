package com.colligendis.server.database.numista.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CurrencyService extends AbstractService {

	public Mono<ExecutionResult<Currency, CreateNodeExecutionStatus>> create(Currency currency,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(currency, colligendisUser, Currency.class, baseLogger));
	}

	public Mono<ExecutionResult<Currency, FindExecutionStatus>> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, Currency.LABEL, Currency.class, baseLogger);
	}

	public Mono<ExecutionResult<Currency, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Currency.LABEL, Currency.class, baseLogger);
	}

	public Mono<ExecutionResult<Currency, ? extends ExecutionStatuses>> findByNidWithCreate(String nid, String fullName,
			String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new Currency(nid, fullName, name), colligendisUserMono, baseLogger);
						default:
							return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Currency, UpdateExecutionStatus>> update(Currency currency,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(currency, colligendisUser, Currency.class,
						baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCirculatedFromYears(
			Currency currency, List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(currency, years, Year.class,
						Currency.CIRCULATED_FROM,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCirculatedTillYears(
			Currency currency, List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(currency, years, Year.class,
						Currency.CIRCULATED_TILL,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setIssuer(Currency currency,
			Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createSingleRelationship(currency, issuer,
						Currency.CIRCULATE_WHEN_BEEN,
						colligendisUser, baseLogger));
	}

}
