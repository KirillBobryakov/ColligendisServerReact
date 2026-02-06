package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class CountryService extends AbstractService {

    public Mono<Either<DatabaseException, Country>> save(Country country, AbstractUser user) {
        return super.createNode(country, user, Country.class);
    }

    public Mono<Either<DatabaseException, Country>> findByNumistaCode(String numistaCode) {
        return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, Country.LABEL, Country.class);
    }

}
