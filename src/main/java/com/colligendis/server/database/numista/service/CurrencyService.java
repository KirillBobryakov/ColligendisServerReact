package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.util.Either;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CurrencyService extends AbstractService {

	public Mono<Either<DatabaseException, Currency>> save(Currency currency, ColligendisUser colligendisUser) {
		return super.createNode(currency, colligendisUser, Currency.class);
	}

	public Mono<Either<DatabaseException, Currency>> findByUuid(String uuid) {
		return super.findNodeByUuid(uuid, Currency.LABEL, Currency.class);
	}

	public Mono<Either<DatabaseException, Currency>> findByNid(String nid) {
		return super.findNodeByUniquePropertyValue("nid", nid, Currency.LABEL, Currency.class);
	}

	public Mono<Either<DatabaseException, Currency>> findByNidWithSave(String nid, String fullName,
			ColligendisUser colligendisUser) {
		return findByNid(nid).flatMap(foundCurrency -> {
			return foundCurrency.fold(
					error -> {
						if (error instanceof NotFoundError) {
							return save(new Currency(nid, fullName), colligendisUser);
						}
						return Mono.just(Either.left(error));
					},
					currency -> {
						if (!currency.getFullName().equals(fullName)) {
							currency.setFullName(fullName);
							return updateAllNodeProperties(currency, colligendisUser, Currency.class);
						}
						return Mono.just(Either.right(currency));
					});
		});
	}

	public Mono<Either<DatabaseException, Currency>> update(Currency currency,
			ColligendisUser colligendisUser) {
		return super.updateAllNodeProperties(currency, colligendisUser, Currency.class)
				.flatMap(e -> e.fold(
						err -> {
							log.error("Error updating currency: {}", err.message());
							return Mono.just(Either.left(err));
						},
						updatedCurrency -> Mono.just(Either.right(updatedCurrency))));
	}

	public Mono<Either<DatabaseException, Boolean>> setCirculatedFromYears(Currency currency, List<Year> years,
			ColligendisUser colligendisUser) {
		return super.createUniqueOutgoingRelationships(currency, years, Year.class, Currency.CIRCULATED_FROM,
				colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setCirculatedTillYears(Currency currency, List<Year> years,
			ColligendisUser colligendisUser) {
		return super.createUniqueOutgoingRelationships(currency, years, Year.class, Currency.CIRCULATED_TILL,
				colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setIssuer(Currency currency, Issuer issuer,
			ColligendisUser colligendisUser) {
		return super.createUniqueTargetedRelationship(currency, issuer, Currency.CIRCULATE_WHEN_BEEN, colligendisUser);
	}

	// public Mono<Void> setName(String nodeUuid, String name) {
	// return setProperty(nodeUuid, "name", name);
	// }

	// public Mono<Void> setCode(String nodeUuid, String code) {
	// return setProperty(nodeUuid, "code", code);
	// }

	// public Mono<Currency> clone(String nodeUuid) {
	// return cloneNode(nodeUuid);
	// }

}
