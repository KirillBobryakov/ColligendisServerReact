package com.colligendis.server.parser.numista;

import java.util.Map;
import java.util.function.Function;

import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.service.IssuerService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.ParsingStatus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class IssuerParser {

    public static final IssuerParser instance = new IssuerParser();

    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RESET = "\u001B[0m";

    // Cached services - lazily initialized
    private NTypeService nTypeService;
    private IssuerService issuerService;

    private NTypeService getNTypeService() {
        if (nTypeService == null) {
            nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
        }
        return nTypeService;
    }

    private IssuerService getIssuerService() {
        if (issuerService == null) {
            issuerService = N4JUtil.getInstance().numistaServices.issuerService;
        }
        return issuerService;
    }

    public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseIssuer(page));

    private Mono<NumistaPage> parseIssuer(NumistaPage numistaPage) {
        if (log.isInfoEnabled()) {
            log.info("nid: {}{}{} - Parsing issuer by #emetteur attribute value",
                    ANSI_BLUE, numistaPage.nid, ANSI_RESET);
        }

        Map<String, String> emetteur = NumistaParseUtils.getAttributeWithTextSingleOption(
                numistaPage.page, "#emetteur", "value");

        if (emetteur == null) {
            log.error("nid: {} - Can't find Issuer on the page", numistaPage.nid);
            numistaPage.parsingStatus = ParsingStatus.FAILED;
            return Mono.empty();
        }

        String code = emetteur.get("value");
        String name = emetteur.get("text");

        return getIssuerService().findByNumistaCode(code)
                .flatMap(either -> either.fold(
                        error -> handleIssuerNotFound(error, code, numistaPage),
                        issuer -> linkIssuerToNType(issuer, name, numistaPage)));
    }

    private Mono<NumistaPage> handleIssuerNotFound(
            com.colligendis.server.database.exception.DatabaseException error,
            String code,
            NumistaPage numistaPage) {

        if (error instanceof NotFoundError) {
            log.error("nid: {} - Issuer not found by code: {}", numistaPage.nid, code);
        } else {
            log.error("nid: {} - Failed to find issuer: {}", numistaPage.nid, error.message());
        }
        return Mono.just(numistaPage);
    }

    private Mono<NumistaPage> linkIssuerToNType(Issuer issuer, String expectedName, NumistaPage numistaPage) {
        // Warn on name mismatch (might indicate stale data)
        if (!issuer.getName().equals(expectedName)) {
            log.warn("nid: {} - Issuer name mismatch: db='{}' vs page='{}'",
                    numistaPage.nid, issuer.getName(), expectedName);
        }

        return getNTypeService()
                .setIssuerMono(
                        Mono.just(numistaPage.nType),
                        Mono.just(issuer),
                        numistaPage.colligendisUserM)
                .flatMap(result -> result.fold(
                        error -> {
                            log.error("nid: {} - Failed to set issuer: {}", numistaPage.nid, error.message());
                            return Mono.just(numistaPage);
                        },
                        success -> {
                            logIssuerSetResult(success, issuer, numistaPage);
                            numistaPage.setIssuer(issuer);
                            return Mono.just(numistaPage);
                        }));
    }

    private void logIssuerSetResult(boolean isNewLink, Issuer issuer, NumistaPage numistaPage) {
        if (!log.isInfoEnabled()) {
            return;
        }

        if (isNewLink) {
            log.info("nid: {}{}{} - Issuer '{}' set successfully",
                    ANSI_BLUE, numistaPage.nid, ANSI_RESET, issuer.getName());
        } else {
            log.info("nid: {}{}{} - Issuer '{}' already set",
                    ANSI_BLUE, numistaPage.nid, ANSI_RESET, issuer.getName());
        }
    }
}
