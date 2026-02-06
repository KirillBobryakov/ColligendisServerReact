package com.colligendis.server.parser.numista;

import java.util.Objects;
import java.util.function.Function;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.util.Either;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class TitleParser {
    public static final TitleParser instance = new TitleParser();

    public Function<NumistaPage, Mono<NumistaPage>> parse = numistaPage -> {
        return Mono.defer(() -> {
            log.info("nid: {} - Parsing title by #designation attribute value",
                    "\u001B[34m" + numistaPage.nid + "\u001B[0m");

            NTypeService nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;

            // Extract title
            String title = NumistaParseUtils.getAttribute(numistaPage.page.selectFirst("#designation"), "value");
            if (title == null || title.isEmpty()) {
                log.error("Title is empty for nid: {}", numistaPage.nid);
                return Mono.empty();
            }

            return loadOrCreateNType(nTypeService, numistaPage.nid, title, numistaPage.colligendisUserM)
                    .flatMap(nTypeEither -> updateTitleIfChanged(nTypeService, nTypeEither, title,
                            numistaPage.colligendisUserM))
                    .map(updatedEither -> {
                        numistaPage.nType = updatedEither.simpleFold(log);
                        return numistaPage;
                    });

        });
    };

    private Mono<Either<DatabaseException, NType>> loadOrCreateNType(
            NTypeService service, String nid, String title, Mono<ColligendisUser> userMono) {
        return service.findByNid(nid)
                .flatMap(result -> result.fold(
                        error -> {
                            if (error instanceof NotFoundError) {
                                log.info("nid: {} - Creating new NType with title: {}", nid, title);
                                NType newType = new NType();
                                newType.setNid(nid);
                                newType.setTitle(title);
                                return service.saveWithColligendisUserMono(newType, userMono);
                            }
                            return Mono.just(Either.left(error));
                        },
                        foundNType -> Mono.just(Either.right(foundNType))));
    }

    private Mono<Either<DatabaseException, NType>> updateTitleIfChanged(
            NTypeService service, Either<DatabaseException, NType> either, String newTitle,
            Mono<ColligendisUser> userMono) {
        return either.fold(
                error -> Mono.just(Either.left(error)),
                existing -> {
                    if (!Objects.equals(existing.getTitle(), newTitle)) {
                        log.warn("NType title changed: {} -> {}", "\u001B[34m" + existing.getTitle() + "\u001B[0m",
                                "\u001B[34m" + newTitle + "\u001B[0m");
                        existing.setTitle(newTitle);
                        return service.update(existing, userMono);
                    }
                    return Mono.just(Either.right(existing));
                });
    }
}
