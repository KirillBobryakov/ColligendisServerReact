package com.colligendis.server.parser.numista;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.colligendis.server.logger.LogExecutionTime;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NumistaHttpClient {

	public static Boolean useCookies = true;
	protected static String COOKIE = "_pk_ses.10.0242=1; _pk_ref.10.0242=%5B%22%22%2C%22%22%2C1762191420%2C%22https%3A%2F%2Fwww.google.com%2F%22%5D; test_cookies=1; PHPSESSID=4tlrb75op7meo0jj50331demhf; _sharedID=ca6bb764-6890-4fdf-9d94-222e1e66266c; _sharedID_cst=2SzgLJUseQ%3D%3D; carte=piece; saisie_rapide=n; search_subtypes=all; pieces_par_page=200; issuer_sort=d; cf_clearance=KNlKQpcinFgvCqybJxRu3gJ6CwQF86ExLIGFv.HIHtk-1759610496-1.2.1.1-ZIkhKIJyRywXBhOw8kg8YUklv.wmZiHx.NCsNBy.RhXJC4s_gd8XTFhE5ZJgq9j_VHIOAHAHV.P2ZcH7aCzHJ_MHMGc5x27wzP65r_ZMatDXheLQIUX6jm9sY3ruestVsloIgyXdFydDCS22iAz1lllue7W2Q_uy44vDxXavB03D_pGTzeGkhhl9eyIvL6MgVzaXtivBkTA5sqSoDiIs6GKa3lnn5lmdfne8gDyq.rc; access_token=P%3EbLPZEY%24%21t.%3F9jIWgGg%28sG%5DrBF1S%28b%21%231.x7g%21%3E; pseudo=kbobryakov; _pk_id.10.0242=a36509097c0e55fd.1751222019.; search_order=v; tb=y; tc=y; tn=y; tp=y; tt=y;";

	public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15";

	private final WebClient client;

	public NumistaHttpClient() {
		this.client = WebClient.builder()
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
				.build();
	}

	@LogExecutionTime
	public Mono<Document> loadPageByURLReactive(String urlString) {

		return client.get()
				.uri(urlString)
				.headers(h -> {
					if (useCookies) {
						h.set(HttpHeaders.COOKIE, COOKIE);
					}
				})
				.retrieve()
				.onStatus(status -> {
					log.info("HTTP loading page by URL: {} with status: {}", urlString, status.value());
					return status.isSameCodeAs(HttpStatus.NOT_FOUND);
				}, r -> Mono.empty())
				.bodyToMono(String.class)
				.map(Jsoup::parse)
				.doOnError(e -> log.error("Error loading page by URL: {}", urlString, e))
				.onErrorResume(e -> {
					log.error("Error loading page by URL: {}", urlString, e);
					return Mono.empty();
				})
				.doOnSuccess(d -> log.debug("Successfully loaded page by URL: {}", urlString));

	}
}