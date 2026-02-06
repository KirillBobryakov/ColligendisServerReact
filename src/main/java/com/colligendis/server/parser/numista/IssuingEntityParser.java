package com.colligendis.server.parser.numista;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.numista.service.IssuingEntityService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.PauseLock;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class IssuingEntityParser {

    public static final IssuingEntityParser instance = new IssuingEntityParser();

    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    private static final String ISSUING_ENTITIES_URL_PREFIX = "https://en.numista.com/catalogue/get_issuing_entities.php?prefill=&country=";

    private final PauseLock pauseLock = new PauseLock("IssuingEntityParser");

    // Cached services - lazily initialized
    private IssuingEntityService issuingEntityService;
    private NTypeService nTypeService;

    private IssuingEntityService getIssuingEntityService() {
        if (issuingEntityService == null) {
            issuingEntityService = N4JUtil.getInstance().numistaServices.issuingEntityService;
        }
        return issuingEntityService;
    }

    private NTypeService getNTypeService() {
        if (nTypeService == null) {
            nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
        }
        return nTypeService;
    }

    public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseIssuingEntities(page));

    private Mono<NumistaPage> parseIssuingEntities(NumistaPage numistaPage) {
        if (log.isInfoEnabled()) {
            log.info("nid: {}{}{} - Parsing issuing entities", ANSI_BLUE, numistaPage.nid, ANSI_RESET);
        }

        List<String> codes = extractIssuingEntityCodes(numistaPage.page);

        if (codes.isEmpty()) {
            return Mono.just(numistaPage);
        }

        return Flux.fromIterable(codes)
                .flatMap(code -> processIssuingEntityCode(code, numistaPage))
                .collectList()
                .flatMap(entities -> linkEntitiesToNType(entities, numistaPage))
                .thenReturn(numistaPage);
    }

    private List<String> extractIssuingEntityCodes(Document page) {
        Elements scripts = page.select("script");

        for (Element script : scripts) {
            if (script.childNodes().isEmpty()) {
                continue;
            }

            List<String> codes = Arrays.stream(script.childNodes().get(0).toString().split("\n"))
                    .filter(line -> line.contains("$.get(\"../get_issuing_entities.php\""))
                    .map(this::extractPrefillValue)
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (!codes.isEmpty()) {
                return codes;
            }
        }
        return List.of();
    }

    private String extractPrefillValue(String line) {
        int start = line.indexOf("prefill:") + 10;
        int end = line.indexOf("\"})");
        return (start > 10 && end > start) ? line.substring(start, end) : "";
    }

    private Mono<IssuingEntity> processIssuingEntityCode(String code, NumistaPage numistaPage) {
        return pauseLock.awaitIfPaused()
                .then(getIssuingEntityService().findByNumistaCode(code))
                .flatMap(either -> either.fold(
                        error -> handleEntityNotFound(error, code, numistaPage),
                        Mono::just));
    }

    private Mono<IssuingEntity> handleEntityNotFound(DatabaseException error, String code, NumistaPage numistaPage) {
        if (!(error instanceof NotFoundError)) {
            if (log.isErrorEnabled()) {
                log.error("nid: {}{}{} - Error finding issuing entity by code: {}{}{}, error: {}{}{}",
                        ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                        ANSI_BLUE, code, ANSI_RESET,
                        ANSI_RED, error.message(), ANSI_RESET);
            }
            return Mono.empty();
        }

        boolean acquiredLock = pauseLock.pause();

        if (!acquiredLock) {
            // Another thread is already loading - wait and retry
            return pauseLock.awaitIfPaused()
                    .then(getIssuingEntityService().findByNumistaCode(code))
                    .flatMap(result -> result.fold(
                            err -> {
                                if (log.isErrorEnabled()) {
                                    log.error(
                                            "nid: {}{}{} - Can't find IssuingEntity by code: {}{}{} after waiting, error: {}{}{}",
                                            ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                                            ANSI_BLUE, code, ANSI_RESET,
                                            ANSI_RED, err.message(), ANSI_RESET);
                                }
                                return Mono.empty();
                            },
                            Mono::just));
        }

        // This thread acquired the lock - load entities
        return numistaPage.colligendisUserM
                .flatMap(user -> loadAndParseIssuingEntities(numistaPage.issuer, user)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> pauseLock.resume())
                        .then(getIssuingEntityService().findByNumistaCode(code))
                        .flatMap(result -> result.fold(
                                err -> {
                                    if (log.isErrorEnabled()) {
                                        log.error(
                                                "nid: {}{}{} - Can't find IssuingEntity by code: {}{}{} after parsing, error: {}{}{}",
                                                ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                                                ANSI_BLUE, code, ANSI_RESET,
                                                ANSI_RED, err.message(), ANSI_RESET);
                                    }
                                    return Mono.empty();
                                },
                                Mono::just)));
    }

    private Mono<Void> linkEntitiesToNType(List<IssuingEntity> entities, NumistaPage numistaPage) {
        if (numistaPage.nType == null || entities.isEmpty()) {
            return Mono.empty();
        }

        return getNTypeService()
                .setIssuingEntitiesMono(
                        Mono.just(numistaPage.nType),
                        Mono.just(entities),
                        numistaPage.colligendisUserM)
                .doOnNext(result -> result.simpleFold(log))
                .then();
    }

    private Mono<Boolean> loadAndParseIssuingEntities(Issuer issuer, ColligendisUser user) {
        if (issuer == null) {
            log.error("loadAndParseIssuingEntities: Issuer is null");
            return Mono.just(false);
        }

        String url = ISSUING_ENTITIES_URL_PREFIX + issuer.getNumistaCode();

        return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
                .flatMap(doc -> {
                    if (doc == null) {
                        log.error("Can't load PHP IssuingEntities from URL: {}", url);
                        return Mono.just(false);
                    }

                    if (!doc.select("optgroup").isEmpty()) {
                        log.error("Found OPTGROUP in IssuingEntities for issuer: {}", issuer.getNumistaCode());
                        return Mono.just(false);
                    }

                    Elements options = doc.select("option");
                    return processOptions(options, issuer, user);
                });
    }

    private Mono<Boolean> processOptions(Elements options, Issuer issuer, ColligendisUser user) {
        return Flux.fromIterable(options)
                .flatMap(option -> {
                    String code = option.attr("value");
                    String name = option.text();
                    return getIssuingEntityService().findByNumistaCodeWithSave(code, name, user)
                            .map(either -> either.simpleFold(log));
                })
                .filter(Objects::nonNull)
                .collectList()
                .flatMap(entities -> getIssuingEntityService().setIssuer(issuer, entities, user)
                        .doOnNext(either -> either.simpleFold(log))
                        .thenReturn(true))
                .defaultIfEmpty(true);
    }
}
