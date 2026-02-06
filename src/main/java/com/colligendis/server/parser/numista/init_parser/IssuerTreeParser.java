package com.colligendis.server.parser.numista.init_parser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.security.access.method.P;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.Subject;
import com.colligendis.server.database.numista.service.CountryService;
import com.colligendis.server.database.numista.service.IssuerService;
import com.colligendis.server.database.numista.service.SectionService;
import com.colligendis.server.database.numista.service.SubjectService;
import com.colligendis.server.parser.numista.NumistaParseUtils;
import com.colligendis.server.util.Either;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class IssuerTreeParser {
    public static final String ISSUERS_URL = "https://en.numista.com/catalogue/search_issuers.php?&p=1&e=0";

    private final IssuerService issuerService;
    private final CountryService countryService;
    private final SubjectService subjectService;

    public IssuerTreeParser() {
        this.issuerService = N4JUtil.getInstance().numistaServices.issuerService;
        this.countryService = N4JUtil.getInstance().numistaServices.countryService;
        this.subjectService = N4JUtil.getInstance().numistaServices.subjectService;
    }

    public void parseAndSave() {

        List<IssuerPageResponse.IssuerEntry> issuerJsonList = new ArrayList<>();
        for (int page = 1; page <= 37; page++) {
            String filePath = String
                    .format("/Users/kirillbobryakov/ColligendisServerReact/numista/issuers/issuers_page_%d.json", page);
            try {
                String jsonContent = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath),
                        java.nio.charset.StandardCharsets.UTF_8);

                // Parse the JSON array from the file
                ObjectMapper mapper = new ObjectMapper();
                IssuerPageResponse issuerArray = mapper.readValue(jsonContent, IssuerPageResponse.class);
                issuerJsonList.addAll(issuerArray.getResults());
            } catch (Exception e) {
                log.error("Failed to load or parse IssuerJsons from {}", filePath, e);
            }
        }

        HashMap<Integer, AbstractNode> nodes = new HashMap<>();

        for (int i = 0; i < issuerJsonList.size(); i++) {
            log.info("Issuer: level " + issuerJsonList.get(i).getLevel() + " | section "
                    + issuerJsonList.get(i).getSection() + " | " +
                    issuerJsonList.get(i).getText()
                    + " | " + issuerJsonList.get(i).getId());

            IssuerPageResponse.IssuerEntry currentIssuerJson = issuerJsonList.get(i);
            IssuerPageResponse.IssuerEntry nextIssuerJson = null;
            if (i < issuerJsonList.size() - 1) {
                nextIssuerJson = issuerJsonList.get(i + 1);
            }

            if (currentIssuerJson.getLevel() == 1) {
                Country country = new Country();
                country.setNumistaCode(currentIssuerJson.getId());
                country.setName(currentIssuerJson.getText());

                Mono<Either<DatabaseException, Country>> currentCountryMono = countryService
                        .findByNumistaCode(currentIssuerJson.getId()).flatMap(
                                (countryResult) -> countryResult.fold(
                                        error -> {
                                            if (error instanceof NotFoundError) {
                                                return countryService.save(country, null);
                                            }
                                            return Mono.just(Either.left(error));
                                        }, (c) -> {
                                            return Mono.just(Either.right(c));
                                        }));

                nodes.put(1, currentCountryMono.block().fold(error -> {
                    log.error("Failed to save country: {}", error.message());
                    return null;
                }, (c) -> c));

                if (nextIssuerJson != null && nextIssuerJson.getLevel() == currentIssuerJson.getLevel()) {
                    Issuer issuer = new Issuer();
                    issuer.setNumistaCode(currentIssuerJson.getId());
                    issuer.setName(currentIssuerJson.getText());

                    Mono<Either<DatabaseException, Issuer>> issuerMono = issuerService
                            .findByNumistaCode(currentIssuerJson.getId()).flatMap(
                                    (issuerResult) -> issuerResult.fold(
                                            error -> {
                                                if (error instanceof NotFoundError) {
                                                    return issuerService.save(issuer, null);
                                                }
                                                return Mono.just(Either.left(error));
                                            }, (iss) -> {
                                                return Mono.just(Either.right(iss));
                                            }));

                    final Issuer finalIssuer = issuerMono.block().fold(error -> {
                        log.error("Failed to save issuer: {}", error.message());
                        return null;
                    }, (iss) -> iss);

                    issuerService.relateToCountry(finalIssuer, (Country) nodes.get(1)).block().fold(error -> {
                        log.error("Failed to relate issuer to country: {}", error.message());
                        return false;
                    }, (b) -> b);
                }
            } else if (currentIssuerJson.getLevel() > 1) {
                if (nextIssuerJson != null && nextIssuerJson.getLevel() > currentIssuerJson.getLevel()) {
                    Subject subject = new Subject();
                    subject.setNumistaCode(currentIssuerJson.getId());
                    subject.setName(currentIssuerJson.getText());

                    Mono<Either<DatabaseException, Subject>> subjectMono = subjectService
                            .findByNumistaCode(currentIssuerJson.getId()).flatMap(
                                    (subjectResult) -> subjectResult.fold(
                                            error -> {
                                                if (error instanceof NotFoundError) {
                                                    return subjectService.save(subject, null);
                                                }
                                                return Mono.just(Either.left(error));
                                            }, (s) -> {
                                                return Mono.just(Either.right(s));
                                            }));

                    nodes.put(currentIssuerJson.getLevel(), subjectMono.block().fold(error -> {
                        log.error("Failed to save subject: {}", error.message());
                        return null;
                    }, (s) -> s));
                    if (currentIssuerJson.getLevel() == 2) {
                        subjectService.relateToCountry((Subject) nodes.get(2),
                                (Country) nodes.get(1)).block().fold(error -> {
                                    log.error("Failed to relate subject to country: {}", error.message());
                                    return false;
                                }, (b) -> b);
                    } else {
                        subjectService.relateToParentSubject((Subject) nodes.get(currentIssuerJson.getLevel()),
                                (Subject) nodes.get(currentIssuerJson.getLevel() - 1)).block().fold(error -> {
                                    log.error("Failed to relate subject to country: {}", error.message());
                                    return false;
                                }, (b) -> b);
                    }
                } else if (nextIssuerJson == null || nextIssuerJson.getLevel() <= currentIssuerJson.getLevel()) {
                    Issuer issuer = new Issuer();
                    issuer.setNumistaCode(currentIssuerJson.getId());
                    issuer.setName(currentIssuerJson.getText());

                    Mono<Either<DatabaseException, Issuer>> issuerMono = issuerService
                            .findByNumistaCode(currentIssuerJson.getId()).flatMap(
                                    (issuerResult) -> issuerResult.fold(
                                            error -> {
                                                if (error instanceof NotFoundError) {
                                                    return issuerService.save(issuer, null);
                                                }
                                                return Mono.just(Either.left(error));
                                            }, (iss) -> {
                                                return Mono.just(Either.right(iss));
                                            }));

                    final Issuer finalIssuer = issuerMono.block().fold(error -> {
                        log.error("Failed to save issuer: {}", error.message());
                        return null;
                    }, (iss) -> iss);

                    if (currentIssuerJson.getLevel() == 2) {
                        issuerService.relateToCountry(finalIssuer, (Country) nodes.get(1)).block().fold(error -> {
                            log.error("Failed to relate issuer to country: {}", error.message());
                            return false;
                        }, (b) -> b);
                    } else {
                        issuerService.relateToSubject(finalIssuer,
                                (Subject) nodes.get(currentIssuerJson.getLevel() - 1)).block().fold(error -> {
                                    log.error("Failed to relate subject to country: {}", error.message());
                                    return false;
                                }, (b) -> b);
                    }

                }
            }

        }

    }

    private void loadAllPages() {
        // Load all JSON from ISSUERS_URL from p=1 to p=37 and save to
        // ColligendisServerReact/numista/issuers
        for (int page = 1; page <= 37; page++) {
            String url = String.format("https://en.numista.com/catalogue/search_issuers.php?&p=%d&e=0", page);
            String json = NumistaParseUtils.fetchJson(url, true);
            if (json == null) {
                log.error("Failed to fetch JSON for page {}", page);
                continue;
            }
            try {
                // You may need to adjust the path to your local project structure as
                // appropriate
                Path dir = Paths
                        .get("/Users/kirillbobryakov/ColligendisServerReact/numista/issuers");
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                Path filePath = dir.resolve("issuers_page_" + page + ".json");
                Files.writeString(filePath, json, StandardCharsets.UTF_8);
                log.info("Saved JSON for page {} to {}", page, filePath.toString());
            } catch (Exception e) {
                log.error("Failed to save JSON for page " + page, e);
            }
        }
    }

    private Document loadDocument() {
        try {
            return Jsoup.connect(ISSUERS_URL)
                    .userAgent(NumistaParseUtils.USER_AGENT)
                    .method(org.jsoup.Connection.Method.GET)
                    .get();
        } catch (Exception e) {
            log.error("Error loading issuers page: {}", ISSUERS_URL, e);
            return null;
        }
    }

}
