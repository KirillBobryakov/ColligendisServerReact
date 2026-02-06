package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.Ruler;
import com.colligendis.server.database.numista.model.RulerGroup;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RulerService extends AbstractService {

    public Mono<Either<DatabaseException, Ruler>> findByNid(String nid) {
        return super.findNodeByUniquePropertyValue("nid", nid, Ruler.LABEL, Ruler.class);
    }

    public Mono<Either<DatabaseException, Ruler>> findByNidWithSave(String nid, String name,
            ColligendisUser colligendisUser) {
        return findByNid(nid).flatMap(foundRuler -> {
            if (foundRuler.isLeft()) {
                DatabaseException error = foundRuler.left();
                if (error instanceof NotFoundError) {
                    return Mono.defer(() -> save(new Ruler(nid, name), colligendisUser)
                            .flatMap(savedRuler -> savedRuler.fold(
                                    err -> {
                                        return Mono.just(Either.left(err));
                                    },
                                    saved -> {
                                        return Mono.just(Either.right(saved));
                                    })));
                }
                return Mono.just(Either.left(error));
            }
            if (!foundRuler.right().getName().equals(name)) {
                foundRuler.right().setName(name);
                return Mono.defer(() -> update(foundRuler.right(), colligendisUser)
                        .flatMap(updatedRuler -> updatedRuler.fold(
                                err -> {
                                    return Mono.just(Either.left(err));
                                },
                                updated -> {
                                    return Mono.just(Either.right(updated));
                                })));
            }
            return Mono.just(foundRuler);
        });
    }

    public Mono<Either<DatabaseException, Ruler>> save(Ruler ruler, AbstractUser user) {
        return super.createNode(ruler, user, Ruler.class);
    }

    public Flux<Either<DatabaseException, Ruler>> findAllByIssuer(Issuer issuer) {
        return super.getAllSourceNodesWithRelationshipType(issuer, Ruler.RULES_WHEN_BEEN, Ruler.class);
    }

    public Mono<Either<DatabaseException, Ruler>> update(Ruler ruler, ColligendisUser colligendisUser) {
        return super.updateAllNodeProperties(ruler, colligendisUser, Ruler.class);
    }

    public Mono<Either<DatabaseException, Boolean>> setRulerGroup(Ruler ruler, RulerGroup rulerGroup,
            ColligendisUser colligendisUser) {
        return super.createUniqueTargetedRelationship(ruler, rulerGroup, Ruler.GROUP_BY, colligendisUser);
    }

    public Mono<Either<DatabaseException, Boolean>> detachIssuerForRuler(Ruler ruler,
            ColligendisUser colligendisUser) {
        return super.deleteAllOutgoingRelationshipsWithRelationshipType(ruler, Ruler.RULES_WHEN_BEEN, colligendisUser);
    }

    public Mono<Either<DatabaseException, Boolean>> detachRulesFromYears(AbstractNode ruler,
            ColligendisUser colligendisUser) {
        return super.deleteAllOutgoingRelationshipsWithRelationshipType(ruler, Ruler.RULES_FROM, colligendisUser);
    }

    public Mono<Either<DatabaseException, Boolean>> setRulersFromYears(Ruler ruler, List<Year> years,
            ColligendisUser colligendisUser) {
        return super.createUniqueOutgoingRelationships(ruler, years, Year.class, Ruler.RULES_FROM, colligendisUser);
    }

    public Mono<Either<DatabaseException, Boolean>> detachRulesTillYears(AbstractNode ruler,
            ColligendisUser colligendisUser) {
        return super.deleteAllOutgoingRelationshipsWithRelationshipType(ruler, Ruler.RULES_TILL, colligendisUser);
    }

    public Mono<Either<DatabaseException, Boolean>> setRulersTillYears(Ruler ruler, List<Year> years,
            ColligendisUser colligendisUser) {
        return super.createUniqueOutgoingRelationships(ruler, years, Year.class, Ruler.RULES_TILL, colligendisUser);
    }

    public Mono<Either<DatabaseException, Boolean>> setIssuer(Ruler ruler, Issuer issuer,
            ColligendisUser colligendisUser) {
        return super.createUniqueTargetedRelationship(ruler, issuer, Ruler.RULES_WHEN_BEEN, colligendisUser);
    }

    public Mono<Either<DatabaseException, Boolean>> setIssuer(List<Ruler> rulers, Issuer issuer,
            ColligendisUser colligendisUser) {
        return super.createUniqueIncomingRelationships(issuer, rulers, Ruler.class, Ruler.RULES_WHEN_BEEN,
                colligendisUser);
    }

}
