package com.colligendis.server.database.common.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.util.Either;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
public class CalendarService extends AbstractService {

    public static Mono<Either<DatabaseException, Calendar>> GREGORIAN;

    @PostConstruct
    public void init() {
        GREGORIAN = Mono.defer(() -> findByCode(Calendar.GREGORIAN_CODE)).cache();
    }

    public Mono<Either<DatabaseException, Calendar>> save(Calendar calendar, AbstractUser user) {
        return super.createNode(calendar, user, Calendar.class);
    }

    public Mono<Either<DatabaseException, Calendar>> findByCode(String code) {
        return super.findNodeByUniquePropertyValue("code", code, Calendar.LABEL, Calendar.class);
    }
}
