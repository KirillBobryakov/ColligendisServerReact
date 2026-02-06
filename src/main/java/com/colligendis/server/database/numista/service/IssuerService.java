package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.Subject;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class IssuerService extends AbstractService {

    public Mono<Either<DatabaseException, Issuer>> save(Issuer issuer, AbstractUser user) {
        return super.createNode(issuer, user, Issuer.class);
    }

    public Mono<Either<DatabaseException, Issuer>> findByNumistaCode(String numistaCode) {
        return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, Issuer.LABEL, Issuer.class);
    }

    public Mono<Either<DatabaseException, Boolean>> relateToCountry(Issuer issuer, Country country) {
        return super.createSingleRelationship(issuer, country, Issuer.RELATE_TO_COUNTRY, null);
    }

    public Mono<Either<DatabaseException, Boolean>> relateToSubject(Issuer issuer, Subject subject) {
        return super.createSingleRelationship(issuer, subject, Issuer.RELATE_TO_SUBJECT, null);
    }

}
