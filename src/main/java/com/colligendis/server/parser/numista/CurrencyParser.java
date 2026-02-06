package com.colligendis.server.parser.numista;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.service.CurrencyService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.ParsingStatus;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriod;
import com.colligendis.server.parser.numista.year_parser.CirculationPeriods;
import com.colligendis.server.parser.numista.year_parser.YearPeriodParserService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class CurrencyParser {

    public static final CurrencyParser instance = new CurrencyParser();

    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    private static final String CURRENCIES_URL_PREFIX = "https://en.numista.com/catalogue/get_currencies.php?";

    private final PauseLock pauseLock = new PauseLock("CurrencyParser");
    private final NumistaHttpClient httpClient = new NumistaHttpClient();

    // Cached services - lazily initialized
    private CurrencyService currencyService;
    private NTypeService nTypeService;
    private YearPeriodParserService yearPeriodParserService;

    private CurrencyService getCurrencyService() {
        if (currencyService == null) {
            currencyService = N4JUtil.getInstance().numistaServices.currencyService;
        }
        return currencyService;
    }

    private NTypeService getNTypeService() {
        if (nTypeService == null) {
            nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
        }
        return nTypeService;
    }

    private YearPeriodParserService getYearPeriodParserService() {
        if (yearPeriodParserService == null) {
            yearPeriodParserService = N4JUtil.getInstance().commonServices.yearPeriodParserService;
        }
        return yearPeriodParserService;
    }

    public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseCurrency(page));

    private Mono<NumistaPage> parseCurrency(NumistaPage numistaPage) {
        if (log.isDebugEnabled()) {
            log.debug("nid: {}{}{} - Parsing Currency", ANSI_BLUE, numistaPage.nid, ANSI_RESET);
        }

        Map<String, String> devise = NumistaParseUtils.getAttributeWithTextSingleOption(
                numistaPage.page, "#devise", "value");

        if (devise == null) {
            if (log.isWarnEnabled()) {
                log.warn("nid: {}{}{} - Can't find Currency (devise) while parsing page",
                        ANSI_BLUE, numistaPage.nid, ANSI_RESET);
            }
            return Mono.just(numistaPage);
        }

        String currencyNid = devise.get("value");

        return pauseLock.awaitIfPaused()
                .then(getCurrencyService().findByNid(currencyNid))
                .flatMap(either -> either.fold(
                        error -> handleCurrencyNotFound(error, currencyNid, numistaPage),
                        Mono::just))
                .flatMap(currency -> linkCurrencyToNType(currency, numistaPage))
                .switchIfEmpty(Mono.just(numistaPage));
    }

    private Mono<Currency> handleCurrencyNotFound(
            DatabaseException error,
            String currencyNid,
            NumistaPage numistaPage) {

        if (!(error instanceof NotFoundError)) {
            if (log.isErrorEnabled()) {
                log.error("nid: {}{}{} - Error finding Currency by nid: {}{}{}, error: {}{}{}",
                        ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                        ANSI_BLUE, currencyNid, ANSI_RESET,
                        ANSI_RED, error.message(), ANSI_RESET);
            }
            return Mono.empty();
        }

        boolean acquiredLock = pauseLock.pause();

        if (!acquiredLock) {
            // Another thread is already loading currencies - wait and retry
            return pauseLock.awaitIfPaused()
                    .then(getCurrencyService().findByNid(currencyNid))
                    .flatMap(result -> result.fold(
                            err -> {
                                if (log.isErrorEnabled()) {
                                    log.error(
                                            "nid: {}{}{} - Can't find Currency by nid: {}{}{} after waiting, error: {}{}{}",
                                            ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                                            ANSI_BLUE, currencyNid, ANSI_RESET,
                                            ANSI_RED, err.message(), ANSI_RESET);
                                }
                                return Mono.empty();
                            },
                            Mono::just));
        }

        // This thread acquired the lock - load currencies
        return numistaPage.colligendisUserM
                .flatMap(user -> loadAndParseCurrencies(
                        numistaPage.issuer,
                        numistaPage.collectibleType.getCode(),
                        user)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> pauseLock.resume())
                        .then(getCurrencyService().findByNid(currencyNid))
                        .flatMap(result -> result.fold(
                                err -> {
                                    if (log.isErrorEnabled()) {
                                        log.error(
                                                "nid: {}{}{} - Can't find Currency by nid: {}{}{} after parsing, error: {}{}{}",
                                                ANSI_BLUE, numistaPage.nid, ANSI_RESET,
                                                ANSI_BLUE, currencyNid, ANSI_RESET,
                                                ANSI_RED, err.message(), ANSI_RESET);
                                    }
                                    return Mono.empty();
                                },
                                Mono::just)));
    }

    private Mono<NumistaPage> linkCurrencyToNType(Currency currency, NumistaPage numistaPage) {
        numistaPage.setCurrency(currency);
        numistaPage.parsingStatus = ParsingStatus.CHANGED;

        return getNTypeService()
                .setCurrency(numistaPage.nType, currency, numistaPage.colligendisUser)
                .doOnNext(result -> result.simpleFold(log))
                .thenReturn(numistaPage);
    }

    private Mono<Boolean> loadAndParseCurrencies(Issuer issuer, String collectibleTypeCode, ColligendisUser user) {
        String url = CURRENCIES_URL_PREFIX
                + "country=" + issuer.getNumistaCode()
                + "&prefill=&ct=" + collectibleTypeCode;

        return httpClient.loadPageByURLReactive(url)
                .switchIfEmpty(Mono.error(new RuntimeException("Can't load PHP Currencies from URL: " + url)))
                .flatMapMany(this::extractOptionElements)
                .index()
                .flatMap(tuple -> processCurrencyOption(tuple.getT1() + 1, tuple.getT2(), issuer, user))
                .then(Mono.fromRunnable(() -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Successfully processed all currencies for issuer {}", issuer.getName());
                    }
                }))
                .thenReturn(true)
                .onErrorResume(ex -> {
                    log.error("Error in loadAndParseCurrencies: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }

    private Mono<Currency> processCurrencyOption(long index, Element option, Issuer issuer, ColligendisUser user) {
        String nid = option.attr("value");
        String fullName = extractFullName(option.text());

        if (log.isDebugEnabled()) {
            log.debug("[{}/?] Processing Currency nid={} fullName={}", index, nid, fullName);
        }

        return getCurrencyService().findByNidWithSave(nid, fullName, user)
                .flatMap(either -> either.fold(
                        err -> {
                            log.error("Error finding currency nid {}: {}", nid, err.message());
                            return Mono.error(new IllegalStateException("Can't find or save currency " + nid));
                        },
                        Mono::just))
                .flatMap(currency -> updateCurrencyWithPeriods(currency, fullName, issuer, user))
                .flatMap(currency -> getCurrencyService().setIssuer(currency, issuer, user).then(Mono.just(currency)));
    }

    private Mono<Currency> updateCurrencyWithPeriods(Currency currency, String fullName, Issuer issuer,
            ColligendisUser user) {
        String cleanName = stripName(fullName);
        currency.setName(cleanName);

        return getYearPeriodParserService().parsePeriods(fullName, user)
                .flatMap(periods -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Parsing periods for currency: {}", fullName);
                    }
                    return saveCurrencyWithPeriods(currency, periods, issuer, user);
                })
                .doOnNext(cur -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Finished currency {} ({})", cur.getFullName(), cur.getUuid());
                    }
                });
    }

    private Mono<Currency> saveCurrencyWithPeriods(Currency currency, CirculationPeriods periods, Issuer issuer,
            ColligendisUser user) {

        return getCurrencyService().update(currency, user)
                .map(either -> either.simpleFold(log))
                .defaultIfEmpty(currency)
                .flatMap(updated -> {
                    Currency cur = updated != null ? updated : currency;
                    return setYearRelationships(cur, periods, issuer, user);
                });
    }

    private Mono<Currency> setYearRelationships(Currency currency, CirculationPeriods periods, Issuer issuer,
            ColligendisUser user) {
        List<Year> fromYears = periods.periods().stream()
                .map(CirculationPeriod::from)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<Year> tillYears = periods.periods().stream()
                .map(CirculationPeriod::till)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        Mono<Void> setFrom = fromYears.isEmpty()
                ? Mono.empty()
                : getCurrencyService().setCirculatedFromYears(currency, fromYears, user).then();

        Mono<Void> setTill = tillYears.isEmpty()
                ? Mono.empty()
                : getCurrencyService().setCirculatedTillYears(currency, tillYears, user).then();

        return setFrom.then(setTill).thenReturn(currency);
    }

    private Flux<Element> extractOptionElements(Document doc) {
        Elements optgroups = doc.select("optgroup");
        if (!optgroups.isEmpty()) {
            return Flux.error(new IllegalStateException("OPTGROUP found in currencies page"));
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

    private String extractFullName(String text) {
        // format: "123 – Mark (notgeld, 1914-1924)"
        int dashIdx = text.indexOf('–');
        return dashIdx >= 0 ? text.substring(dashIdx + 1).trim() : text.trim();
    }

    private String stripName(String fullName) {
        int parenIdx = fullName.indexOf('(');
        return parenIdx > 0 ? fullName.substring(0, parenIdx).trim() : fullName.trim();
    }
}
