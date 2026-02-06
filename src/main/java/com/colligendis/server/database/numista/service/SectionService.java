package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.Section;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class SectionService extends AbstractService {

    public Mono<Either<DatabaseException, Section>> save(Section section, AbstractUser user) {
        return super.createNode(section, user, Section.class);
    }

    public Mono<Either<DatabaseException, Section>> findByNid(String nid) {
        return super.findNodeByUniquePropertyValue("nid", nid, Section.LABEL, Section.class);
    }
}
