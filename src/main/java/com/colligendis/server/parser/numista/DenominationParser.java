package com.colligendis.server.parser.numista;

import java.util.Map;
import java.util.function.Function;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.service.DenominationService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.ParsingStatus;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.util.FractionParser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DenominationParser {

    public static final DenominationParser instance = new DenominationParser();

    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    private final PauseLock pauseLock = new PauseLock("DenominationParser");
    private final NumistaHttpClient httpClient = new NumistaHttpClient();

    private DenominationService denominationService;

    private DenominationService getDenominationService() {
        if (denominationService == null) {
            denominationService = N4JUtil.getInstance().numistaServices.denominationService;
        }
        return denominationService;
    }

    private NTypeService nTypeService;

    private NTypeService getNTypeService() {
        if (nTypeService == null) {
            nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
        }
        return nTypeService;
    }

    public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseDenomination(page));

    private Mono<NumistaPage> parseDenomination(NumistaPage numistaPage) {
        Map<String, String> denominationAttr = NumistaParseUtils.getAttributeWithTextSingleOption(numistaPage.page,
                "#denomination",
                "value");

        if (denominationAttr == null) {
            log.debug("Can't find Denomination while parsing page with nid: {}", numistaPage.nid);
            numistaPage.parsingStatus = ParsingStatus.NOT_CHANGED;
            return Mono.just(numistaPage);
        }

        String denominationNid = denominationAttr.get("value");

        return pauseLock.awaitIfPaused()
                .then(getDenominationService().findByNid(denominationNid))
                .flatMap(either -> either.fold(
                        error -> handleDenominationNotFound(error, denominationNid, numistaPage),
                        Mono::just))
                .flatMap(denomination -> linkDenominationToNType(denomination, numistaPage))
                .switchIfEmpty(Mono.just(numistaPage));
    }

    private Mono<Denomination> handleDenominationNotFound(DatabaseException error, String denominationNid,
            NumistaPage numistaPage) {

        if (!(error instanceof NotFoundError)) {
            if (log.isErrorEnabled()) {
                log.error("nid: {}{}{} - Error finding Denomination by nid: {}{}{}, error: {}{}{}",
                        ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                        ANSI_BLUE, denominationNid, ANSI_RESET,
                        ANSI_RED, error.message(), ANSI_RESET);
            }
            return Mono.empty();
        }
        boolean acquiredLock = pauseLock.pause();

        if (!acquiredLock) {
            // Another thread is already loading - wait and retry
            return pauseLock.awaitIfPaused()
                    .then(getDenominationService().findByNid(denominationNid))
                    .flatMap(result -> result.fold(
                            err -> {
                                if (log.isErrorEnabled()) {
                                    log.error(
                                            "nid: {}{}{} - Can't find Denomination by nid: {}{}{} after waiting, error: {}{}{}",
                                            ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                                            ANSI_BLUE, denominationNid, ANSI_RESET,
                                            ANSI_RED, err.message(), ANSI_RESET);
                                }
                                return Mono.empty();
                            },
                            Mono::just));
        }

        // This thread acquired the lock - load denomination
        return numistaPage.colligendisUserM
                .flatMap(user -> loadAndParseDenomination(numistaPage.currency, user)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> pauseLock.resume())
                        .then(getDenominationService().findByNid(denominationNid))
                        .flatMap(result -> result.fold(
                                err -> {
                                    if (log.isErrorEnabled()) {
                                        log.error(
                                                "nid: {}{}{} - Can't find Denomination by nid: {}{}{} after parsing, error: {}{}{}",
                                                ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                                                ANSI_BLUE, denominationNid, ANSI_RESET,
                                                ANSI_RED, err.message(), ANSI_RESET);
                                    }
                                    return Mono.empty();
                                },
                                Mono::just)));
    }

    private Mono<NumistaPage> linkDenominationToNType(Denomination denomination, NumistaPage numistaPage) {
        numistaPage.setDenomination(denomination);
        numistaPage.parsingStatus = ParsingStatus.CHANGED;

        return getNTypeService()
                .setDenomination(numistaPage.nType, denomination, numistaPage.colligendisUser)
                .doOnNext(result -> result.simpleFold(log))
                .thenReturn(numistaPage);
    }

    public static final String DENOMINATIONS_BY_CURRENCY_PREFIX = "https://en.numista.com/catalogue/get_denominations.php?";

    private Mono<Boolean> loadAndParseDenomination(Currency currency, ColligendisUser user) {

        String url = DENOMINATIONS_BY_CURRENCY_PREFIX
                + "currency=" + currency.getNid();

        return httpClient.loadPageByURLReactive(url)
                .switchIfEmpty(Mono.error(new RuntimeException("Can't load PHP Denominations from URL: " + url)))
                .flatMapMany(this::extractOptionElements)
                .index()
                .flatMap(tuple -> processDenominationOption(tuple.getT1() + 1, tuple.getT2(), currency, user))
                .then(Mono.fromRunnable(() -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Successfully processed all denominations for currency {}", currency.getNid());
                    }
                }))
                .thenReturn(true)
                .onErrorResume(ex -> {
                    log.error("Error in loadAndParseDenomination: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }

    private Mono<Denomination> processDenominationOption(long index, Element option, Currency currency,
            ColligendisUser user) {
        String nid = option.attr("value");
        String fullName = option.text();
        String denName = fullName.contains("(") ? fullName.substring(0, fullName.lastIndexOf('(') - 1)
                : fullName;

        if (log.isDebugEnabled()) {
            log.debug("[{}/?] Processing Currency nid={} fullName={}", index, nid, fullName);
        }
        Float denNumericValue = null;
        if (fullName.contains("(")) {
            String denNumericValueStr = fullName
                    .substring(fullName.lastIndexOf('(') + 1, fullName.lastIndexOf(')')).replace(" ", "")
                    .replace(" ", "");
            denNumericValue = FractionParser.parseToFloat(denNumericValueStr);
        }

        return getDenominationService()
                .findByNidWithSave(nid, denName, fullName,
                        denNumericValue, user)
                .flatMap(either -> either.fold(
                        err -> {
                            log.error("Error finding denomination by nid {}: {}", nid, err.message());
                            return Mono
                                    .error(new IllegalStateException("Can't find or save denomination by nid " + nid));
                        },
                        Mono::just))
                .flatMap(denomination -> getDenominationService().setCurrency(denomination, currency, user)
                        .then(Mono.just(denomination)));
    }

    private Flux<Element> extractOptionElements(Document doc) {
        Elements optgroups = doc.select("optgroup");
        if (!optgroups.isEmpty()) {
            return Flux.error(new IllegalStateException("OPTGROUP found in denominations page"));
        }

        Elements options = doc.select("option");
        if (options.isEmpty()) {
            return Flux.error(new IllegalStateException("No <option> tags found in currencies page"));
        }

        if (log.isDebugEnabled()) {
            log.debug("Found {} currency options", options.size());
        }
        return Flux.fromIterable(options);
    }

}
