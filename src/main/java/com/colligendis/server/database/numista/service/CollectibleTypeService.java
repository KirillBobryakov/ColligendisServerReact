package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.util.Either;
import reactor.core.publisher.Mono;

@Service
public class CollectibleTypeService extends AbstractService {

    public Mono<Either<DatabaseException, CollectibleType>> save(CollectibleType collectibleType, AbstractUser user) {
        return super.createNode(collectibleType, user, CollectibleType.class);
    }

    public Mono<Either<DatabaseException, CollectibleType>> findByUuid(String uuid) {
        return super.findNodeByUuid(uuid, CollectibleType.LABEL, CollectibleType.class);
    }

    public Mono<Either<DatabaseException, CollectibleType>> findByCode(String code) {
        return super.findNodeByUniquePropertyValue("code", code, CollectibleType.LABEL, CollectibleType.class);
    }

    public Mono<Either<DatabaseException, CollectibleType>> saveIfAbsent(String code, String name, AbstractUser user) {
        return findByCode(code)
                .switchIfEmpty(Mono.just(Either.right(null)))
                .flatMap(found -> {
                    if (found.isRight() && found.right() != null) {
                        return Mono.just(Either.right(found.right()));
                    }
                    CollectibleType node = new CollectibleType();
                    node.setCode(code);
                    node.setName(name);
                    return save(node, user);
                });
    }

    public Mono<Either<DatabaseException, Boolean>> linkParentChild(CollectibleType parent,
            CollectibleType child) {
        return super.createSingleRelationship(parent, child, CollectibleType.HAS_COLLECTIBLE_TYPE_CHILD, null);
    }

}
