package com.colligendis.server.parser.numista;

import java.util.function.Function;

import java.time.Duration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.FileNotFoundException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
public class PageLoader {
    public static Boolean useCookies = true;
    protected static String COOKIE = "_pk_ses.10.0242=1; _pk_ref.10.0242=%5B%22%22%2C%22%22%2C1762191420%2C%22https%3A%2F%2Fwww.google.com%2F%22%5D; test_cookies=1; PHPSESSID=4tlrb75op7meo0jj50331demhf; _sharedID=ca6bb764-6890-4fdf-9d94-222e1e66266c; _sharedID_cst=2SzgLJUseQ%3D%3D; carte=piece; saisie_rapide=n; search_subtypes=all; pieces_par_page=200; issuer_sort=d; cf_clearance=KNlKQpcinFgvCqybJxRu3gJ6CwQF86ExLIGFv.HIHtk-1759610496-1.2.1.1-ZIkhKIJyRywXBhOw8kg8YUklv.wmZiHx.NCsNBy.RhXJC4s_gd8XTFhE5ZJgq9j_VHIOAHAHV.P2ZcH7aCzHJ_MHMGc5x27wzP65r_ZMatDXheLQIUX6jm9sY3ruestVsloIgyXdFydDCS22iAz1lllue7W2Q_uy44vDxXavB03D_pGTzeGkhhl9eyIvL6MgVzaXtivBkTA5sqSoDiIs6GKa3lnn5lmdfne8gDyq.rc; access_token=P%3EbLPZEY%24%21t.%3F9jIWgGg%28sG%5DrBF1S%28b%21%231.x7g%21%3E; pseudo=kbobryakov; _pk_id.10.0242=a36509097c0e55fd.1751222019.; search_order=v; tb=y; tc=y; tn=y; tp=y; tt=y;";
    public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15";

    public static Function<NumistaPage, Mono<NumistaPage>> loadPageByURL() {
        return numistaPage -> Mono.defer(() -> {
            log.info("nid: {} - Loading page by URL: {}", numistaPage.nid, numistaPage.url);
            return loadDocument(numistaPage.url);
        })
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(1))
                                .maxBackoff(Duration.ofSeconds(3))
                                .filter(ex -> !(ex instanceof FileNotFoundException)) // не ретраем 404
                )
                .doOnError(e -> log.error("Failed to load after retries: {}", numistaPage.url, e))
                .onErrorResume(e -> Mono.empty())
                .map(doc -> {
                    numistaPage.page = doc;
                    return numistaPage;
                });
    }

    public static Mono<Document> loadDocument(String url) {
        return Mono.fromCallable(() -> {
            log.debug("Loading {}", url);

            Connection con = Jsoup.connect(url)
                    .ignoreHttpErrors(true)
                    .userAgent(USER_AGENT);

            if (useCookies) {
                con.header("Cookie", COOKIE);
            }

            Connection.Response response = con.execute();

            if (response.statusCode() == 404) {
                log.warn("404 Not Found: {}", url);
                throw new FileNotFoundException(url);
            }

            return response.parse();

        }).subscribeOn(Schedulers.boundedElastic());

    }
}
