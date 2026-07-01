// package com.colligendis.server.parser.numista;

// import org.jsoup.nodes.Document;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Component;
// import org.springframework.util.StringUtils;

// import com.colligendis.server.database.ColligendisUser;
// import com.colligendis.server.logger.LogExecutionTime;

// import lombok.extern.slf4j.Slf4j;
// import reactor.core.publisher.Mono;
// import reactor.core.scheduler.Schedulers;

// /**
// * Loads Numista HTML pages via Playwright (Cloudflare-aware). Cookie
// resolution
// * matches {@link
// com.colligendis.server.parser.numista.collection.NumistaCollectionClient}.
// */
// @Slf4j
// @Component
// public class NumistaPageLoader {

// private static final String COOKIE_NOT_CONFIGURED = "Numista cookie is not
// configured. Add it in Settings / Profile.";

// private final boolean useCookies;

// public NumistaPageLoader(@Value("${colligendis.numista.use-cookies:true}")
// boolean useCookies) {
// this.useCookies = useCookies;
// }

// /** Cookie from {@link ColligendisUser#getNumistaCookie()} stored in Neo4j.
// */
// public String resolveCookie(ColligendisUser user) {
// if (user != null && StringUtils.hasText(user.getNumistaCookie())) {
// return user.getNumistaCookie().strip();
// }
// return "";
// }

// @LogExecutionTime
// public Mono<Document> loadDocument(String url, ColligendisUser user) {
// return Mono.fromCallable(() -> loadDocumentBlocking(url, user))
// .subscribeOn(Schedulers.boundedElastic());
// }

// @LogExecutionTime
// public Mono<String> loadHtml(String url, ColligendisUser user) {
// return loadDocument(url, user).map(Document::html);
// }

// public Document loadDocumentBlocking(String url, ColligendisUser user) {
// String normalizedUrl = url == null ? "" : url.strip();
// if (normalizedUrl.isEmpty()) {
// throw new NumistaPageLoadException("URL is required", normalizedUrl);
// }
// if (user == null) {
// throw new NumistaPageLoadException("Authenticated user is required",
// normalizedUrl);
// }

// String cookie = resolveCookie(user);
// if (useCookies && !StringUtils.hasText(cookie)) {
// throw new NumistaPageLoadException(COOKIE_NOT_CONFIGURED, normalizedUrl);
// }

// log.debug("Loading Numista page {}", normalizedUrl);

// Document document = NumistaParseUtils.loadPageByURL(normalizedUrl, cookie);
// if (document == null) {
// log.warn("Failed to load Numista page: {}", normalizedUrl);
// throw new NumistaPageLoadException("Failed to load Numista page",
// normalizedUrl);
// }
// if (NumistaParseUtils.isBotChallengeDocument(document)) {
// log.warn("Numista page blocked by bot challenge: {}", normalizedUrl);
// throw new NumistaPageLoadException(
// "Numista page is not available (check your cookie or Cloudflare clearance)",
// normalizedUrl);
// }
// return document;
// }
// }
