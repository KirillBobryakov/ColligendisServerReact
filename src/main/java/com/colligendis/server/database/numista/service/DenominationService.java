package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class DenominationService extends AbstractService {

    public Mono<Either<DatabaseException, Denomination>> save(Denomination denomination, AbstractUser user) {
        return super.createNode(denomination, user, Denomination.class);
    }

    public Mono<Either<DatabaseException, Denomination>> findByNid(String nid) {
        return super.findNodeByUniquePropertyValue("nid", nid, Denomination.LABEL, Denomination.class);
    }

    public Mono<Either<DatabaseException, Denomination>> findByNidWithSave(String nid, String name, String fullName,
            Float numericValue, ColligendisUser colligendisUser) {
        return findByNid(nid).flatMap(foundDenomination -> foundDenomination.fold(
                findbyNidErr -> {
                    if (findbyNidErr instanceof NotFoundError) {
                        return Mono
                                .defer(() -> save(new Denomination(nid, name, fullName, numericValue), colligendisUser)
                                        .flatMap(savedDenomination -> savedDenomination.fold(
                                                savedErr -> {
                                                    return Mono.just(Either.left(savedErr));
                                                },
                                                saved -> {
                                                    return Mono.just(Either.right(saved));
                                                })));
                    }
                    return Mono.just(Either.left(findbyNidErr));
                },
                denomination -> {
                    return Mono.just(Either.right(denomination));
                }));
    }

    public Mono<Either<DatabaseException, Denomination>> update(Denomination denomination, AbstractUser user) {
        return super.updateAllNodeProperties(denomination, user, Denomination.class);
    }

    public Mono<Either<DatabaseException, Boolean>> setCurrency(Denomination denomination, Currency currency,
            ColligendisUser colligendisUser) {
        return super.createUniqueTargetedRelationship(denomination, currency, Denomination.UNDER_CURRENCY,
                colligendisUser);
    }
}
