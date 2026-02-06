package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.util.Either;

import reactor.core.publisher.Mono;

@Service
public class SeriesService extends AbstractService {

    public Mono<Either<DatabaseException, Series>> save(Series series, ColligendisUser colligendisUser) {
        return super.createNode(series, colligendisUser, Series.class);
    }

    public Mono<Either<DatabaseException, Series>> findByNid(String nid) {
        return super.findNodeByUniquePropertyValue("nid", nid, Series.LABEL, Series.class);
    }

    public Mono<Either<DatabaseException, Series>> findByNidWithSave(String nid, String name,
            ColligendisUser colligendisUser) {
        return findByNid(nid).flatMap(foundSeries -> foundSeries.fold(
                foundSeriesErr -> {
                    if (foundSeriesErr instanceof NotFoundError) {
                        return save(new Series(nid, name), colligendisUser);
                    }
                    return Mono.just(Either.left(foundSeriesErr));
                },
                series -> {
                    return Mono.just(Either.right(series));
                }));
    }
}
