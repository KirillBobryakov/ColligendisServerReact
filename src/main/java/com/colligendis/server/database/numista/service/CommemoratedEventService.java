package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.CommemoratedEvent;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class CommemoratedEventService extends AbstractService {

    public Mono<Either<DatabaseException, CommemoratedEvent>> save(CommemoratedEvent commemoratedEvent,
            ColligendisUser user) {
        return super.createNode(commemoratedEvent, user, CommemoratedEvent.class);
    }

    public Mono<Either<DatabaseException, CommemoratedEvent>> findByName(String name) {
        return super.findNodeByUniquePropertyValue("name", name, CommemoratedEvent.LABEL, CommemoratedEvent.class);
    }

    public Mono<Either<DatabaseException, CommemoratedEvent>> findByNameWithSave(String name,
            ColligendisUser colligendisUser) {
        return findByName(name).flatMap(foundCommemoratedEvent -> foundCommemoratedEvent.fold(
                findByNameErr -> {
                    if (findByNameErr instanceof NotFoundError) {
                        return Mono.defer(() -> save(new CommemoratedEvent(name), colligendisUser)
                                .flatMap(savedCommemoratedEvent -> savedCommemoratedEvent.fold(
                                        savedErr -> {
                                            return Mono.just(Either.left(savedErr));
                                        },
                                        saved -> {
                                            return Mono.just(Either.right(saved));
                                        })));
                    }
                    return Mono.just(Either.left(findByNameErr));
                },
                commemoratedEvent -> {
                    return Mono.just(Either.right(commemoratedEvent));
                }));
    }

}
