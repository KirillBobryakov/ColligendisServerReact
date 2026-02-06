package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.util.Either;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class IssuingEntityService extends AbstractService {

    public Mono<Either<DatabaseException, IssuingEntity>> save(IssuingEntity issuingEntity, AbstractUser user) {
        return super.createNode(issuingEntity, user, IssuingEntity.class);
    }

    public Mono<Either<DatabaseException, IssuingEntity>> findByNumistaCode(String numistaCode) {
        return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, IssuingEntity.LABEL,
                IssuingEntity.class);
    }

    public Mono<Either<DatabaseException, IssuingEntity>> findByNumistaCodeWithSave(String numistaCode, String name,
            ColligendisUser colligendisUser) {
        log.debug("=== findByNumistaCodeWithSave START === numistaCode: {}, name: {}, user: {}",
                numistaCode, name, colligendisUser);

        return findByNumistaCode(numistaCode)
                .doOnNext(result -> log.debug("findByNumistaCode result: isLeft={}", result.isLeft()))
                .flatMap(foundIssuingEntity -> {
                    if (foundIssuingEntity.isLeft()) {
                        log.debug("Entity not found, checking error type: {}",
                                foundIssuingEntity.left().getClass().getSimpleName());
                        if (foundIssuingEntity.left() instanceof NotFoundError) {
                            log.debug("Creating new IssuingEntity with code: {} and name: {}", numistaCode, name);
                            Mono<Either<DatabaseException, IssuingEntity>> saveMono = save(
                                    new IssuingEntity(numistaCode, name), colligendisUser);
                            return saveMono.doOnNext(saveResult -> log.debug("Save result: isLeft={}, value={}",
                                    saveResult.isLeft(),
                                    saveResult.isRight() ? saveResult.right() : saveResult.left()));
                        }
                        log.warn("Error is not NotFoundError, returning error: {}",
                                foundIssuingEntity.left().message());
                        return Mono.just(Either.<DatabaseException, IssuingEntity>left(foundIssuingEntity.left()));
                    }
                    log.debug("Entity found: {}", foundIssuingEntity.right());
                    if (!foundIssuingEntity.right().getName().equals(name)) {
                        log.debug("Updating entity name from '{}' to '{}'", foundIssuingEntity.right().getName(), name);
                        foundIssuingEntity.right().setName(name);
                        return updateAllNodeProperties(foundIssuingEntity.right(), colligendisUser,
                                IssuingEntity.class);
                    }
                    log.debug("Entity already has correct name, returning existing entity");
                    return Mono.just(foundIssuingEntity);
                })
                .doOnSuccess(result -> log.debug("=== findByNumistaCodeWithSave SUCCESS === Result: {}", result))
                .doOnError(error -> log.error("=== findByNumistaCodeWithSave ERROR === {}", error.getMessage(), error));
    }

    public Flux<Either<DatabaseException, IssuingEntity>> getIssuingEntities(Issuer issuer) {
        return super.getAllSourceNodesWithRelationshipType(issuer, IssuingEntity.ISSUES_WHEN_BEEN, IssuingEntity.class);
    }

    public Mono<Either<DatabaseException, Boolean>> setIssuer(Issuer issuer, List<IssuingEntity> issuingEntities,
            ColligendisUser colligendisUser) {
        return super.createUniqueIncomingRelationships(issuer, issuingEntities, IssuingEntity.class,
                IssuingEntity.ISSUES_WHEN_BEEN, colligendisUser);
    }

}
