package com.colligendis.server.database;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class ColligendisUserService extends AbstractService {

    public Mono<Either<DatabaseException, ColligendisUser>> save(ColligendisUser colligendisUser) {
        return super.createNode(colligendisUser, null, ColligendisUser.class);
    }

    public Mono<Either<DatabaseException, ColligendisUser>> findByUuid(String uuid) {
        return super.findNodeByUuid(uuid, ColligendisUser.LABEL, ColligendisUser.class);
    }

    public Mono<Either<DatabaseException, ColligendisUser>> findByUsername(String username) {
        return super.findNodeByUniquePropertyValue("username", username, ColligendisUser.LABEL,
                ColligendisUser.class);
    }

}
