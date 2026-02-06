package com.colligendis.server.database.common.service;

import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.common.model.Year;

import reactor.core.publisher.Mono;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.util.Either;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.AbstractUser;
import com.colligendis.server.database.ColligendisUser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class YearService extends AbstractService {

    public Mono<Either<DatabaseException, Year>> save(Year year, AbstractUser user) {
        return super.createNode(year, user, Year.class);
    }

    public Mono<Either<DatabaseException, Boolean>> setCalendar(Year year, Calendar calendar,
            ColligendisUser colligendisUser) {
        return super.createSingleRelationship(year, calendar, Year.TO_NUMBER_IN, colligendisUser);
    }

    public Mono<Either<DatabaseException, Year>> findGregorianYearByValue(Integer value, AbstractUser user) {
        return CalendarService.GREGORIAN.flatMap(gregorian -> {
            if (gregorian.isLeft()) {
                return Mono.just(Either.left(gregorian.left()));
            }
            return findByValueAndCalendar(value, gregorian.right(), user);
        });
    }

    public Mono<Either<DatabaseException, Year>> findByValueAndCalendar(Integer value, Calendar calendar,
            AbstractUser user) {
        return super.findNodeByPropertyValueAndTargetNode("value", value, Year.LABEL, Year.class,
                calendar, Year.TO_NUMBER_IN)
                .flatMap(foundResult -> {
                    if (foundResult.isLeft()) {
                        DatabaseException error = foundResult.left();
                        if (error instanceof NotFoundError) {
                            Year newYear = new Year(value);
                            return Mono.defer(() -> save(newYear, user)
                                    .flatMap(savedResult -> savedResult.fold(
                                            err -> {
                                                log.error("Error saving year: {}", err.message());
                                                return Mono.just(Either.left(err));
                                            },
                                            savedYear -> setCalendar(savedYear, calendar, null)
                                                    .map(setResult -> setResult.fold(
                                                            err -> {
                                                                log.error("Error setting calendar for year: {}",
                                                                        err.message());
                                                                return Either.left(err);
                                                            },
                                                            success -> Either.right(savedYear))))));
                        }
                        return Mono.just(Either.left(error));
                    }
                    return Mono.just(foundResult);
                });
    }
}
