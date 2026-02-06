package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Subject;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class SubjectService extends AbstractService {

    public Mono<Either<DatabaseException, Subject>> save(Subject subject, AbstractUser user) {
        return super.createNode(subject, user, Subject.class);
    }

    public Mono<Either<DatabaseException, Subject>> findByNumistaCode(String numistaCode) {
        return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, Subject.LABEL, Subject.class);
    }

    public Mono<Either<DatabaseException, Boolean>> relateToCountry(Subject subject, Country country) {
        return super.createSingleRelationship(subject, country, Subject.RELATE_TO_COUNTRY, null);
    }

    public Mono<Either<DatabaseException, Boolean>> relateToParentSubject(Subject subject, Subject parentSubject) {
        return super.createSingleRelationship(subject, parentSubject, Subject.PARENT_SUBJECT, null);
    }

}
