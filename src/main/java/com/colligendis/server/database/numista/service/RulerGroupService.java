package com.colligendis.server.database.numista.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.RulerGroup;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class RulerGroupService extends AbstractService {

    public Mono<Either<DatabaseException, RulerGroup>> findByName(String name) {
        return super.findNodeByUniquePropertyValue("name", name, RulerGroup.LABEL, RulerGroup.class);
    }

    public Mono<Either<DatabaseException, RulerGroup>> findByNidWithSave(String nid, String name,
            ColligendisUser colligendisUser) {
        return super.<RulerGroup>findNodeByUniquePropertyValue("nid", nid, RulerGroup.LABEL, RulerGroup.class)
                .flatMap(foundResult -> {
                    if (foundResult.isLeft()) {
                        DatabaseException error = foundResult.left();
                        if (error instanceof NotFoundError) {
                            return Mono.defer(
                                    () -> super.createNode(new RulerGroup(nid, name), colligendisUser, RulerGroup.class)
                                            .flatMap(savedResult -> savedResult.fold(
                                                    err -> {
                                                        return Mono.just(Either.left(err));
                                                    },
                                                    saved -> {
                                                        return Mono.just(Either.right(saved));
                                                    })));
                        }
                        return Mono.just(Either.left(error));
                    }
                    if (!Objects.equals(foundResult.right().getName(), name)) {
                        foundResult.right().setName(name);
                        return Mono.defer(
                                () -> super.updateAllNodeProperties(foundResult.right(), colligendisUser,
                                        RulerGroup.class)
                                        .flatMap(updatedResult -> updatedResult.fold(
                                                err -> {
                                                    return Mono.just(Either.left(err));
                                                },
                                                updated -> {
                                                    return Mono.just(Either.right(updated));
                                                })));
                    }
                    return Mono.just(foundResult);
                });
    }

}
