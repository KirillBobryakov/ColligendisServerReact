package com.colligendis.server.parser.numista.collection;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.logger.LogExecutionTime;
import com.colligendis.server.parser.numista.CloudflareBlockException;
import com.colligendis.server.util.web.WebPageAccept;
import com.colligendis.server.util.web.WebPageClient;
import com.colligendis.server.util.web.WebPageResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * HTTP client for Numista collection form endpoints.
 * <p>
 * Save ({@code save_collection.php}) returns HTML with the updated collection
 * row.
 * Remove ({@code remove_collection_item.php}) typically returns {@code 200}
 * with an
 * empty body — that is treated as success.
 */
@Slf4j
@Component
public class NumistaCollectionClient {

	private static final String COLLECTION_PAGE_BASE_URL = "https://en.numista.com/vous/vos_pieces.php";
	private static final String COOKIE_NOT_CONFIGURED = "Numista cookie is not configured. Add it in Settings / Profile.";

	private static final Consumer<HttpHeaders> COLLECTION_REQUEST_HEADERS = headers -> {
		headers.set(HttpHeaders.ORIGIN, "https://en.numista.com");
		headers.set(HttpHeaders.REFERER, "https://en.numista.com/");
		headers.set("X-Requested-With", "XMLHttpRequest");
	};

	private final WebPageClient webPageClient;
	private final boolean useCookies;

	public NumistaCollectionClient(
			WebPageClient webPageClient,
			@Value("${colligendis.numista.use-cookies:true}") boolean useCookies) {
		this.webPageClient = webPageClient;
		this.useCookies = useCookies;
	}

	@LogExecutionTime
	public Mono<String> fetchCollectionPage(String issuerNumistaCode, ColligendisUser user) {
		String encodedIssuer = URLEncoder.encode(issuerNumistaCode.strip(), StandardCharsets.UTF_8);
		String url = COLLECTION_PAGE_BASE_URL + "?issuer=" + encodedIssuer;

		return Mono.defer(() -> webPageClient
				.loadPage(url, requireCookie(user), WebPageAccept.HTML, COLLECTION_REQUEST_HEADERS))
				.flatMap(response -> {
					if (!response.is2xxSuccessful()) {
						return Mono.error(failedRequest(
								"Numista collection page fetch failed",
								url,
								response));
					}
					if (isCloudflareChallenge(response.body())) {
						return Mono.error(new CloudflareBlockException(url));
					}
					if (!isCollectionPageHtml(response.body())) {
						return Mono.error(new NumistaCollectionHttpException(
								"Numista collection page is not available (check your cookie or sign in on Numista)",
								response.statusCode(),
								response.body()));
					}
					return Mono.just(response.body());
				})
				.doOnError(
						e -> log.error("Failed to fetch Numista collection page for issuer={}", issuerNumistaCode, e));
	}

	/**
	 * Fetches a specific page of the user's full collection (all issuers) using
	 * {@code vos_pieces.php?issuer=&swap=3&type=&page=N}.
	 */
	@LogExecutionTime
	public Mono<String> fetchAllCollectionPage(int page, ColligendisUser user) {
		String url = COLLECTION_PAGE_BASE_URL + "?issuer=&swap=3&type=&page=" + page;

		return Mono.defer(() -> webPageClient
				.loadPage(url, requireCookie(user), WebPageAccept.HTML, COLLECTION_REQUEST_HEADERS))
				.flatMap(response -> {
					if (!response.is2xxSuccessful()) {
						return Mono.error(failedRequest(
								"Numista all-collection page fetch failed",
								url,
								response));
					}
					if (isCloudflareChallenge(response.body())) {
						return Mono.error(new CloudflareBlockException(url));
					}
					if (!isCollectionPageHtml(response.body())) {
						return Mono.error(new NumistaCollectionHttpException(
								"Numista collection page is not available (check your cookie or sign in on Numista)",
								response.statusCode(),
								response.body()));
					}
					return Mono.just(response.body());
				})
				.doOnError(e -> log.error("Failed to fetch Numista all-collection page={}", page, e));
	}

	@LogExecutionTime
	public Mono<String> saveCollectionItem(NumistaCollectionSaveRequest request, ColligendisUser user) {
		return Mono.defer(() -> webPageClient
				.postForm(NumistaCollectionSaveRequest.SAVE_URL, requireCookie(user), request.toFormData(),
						COLLECTION_REQUEST_HEADERS))
				.flatMap(response -> {
					if (!response.is2xxSuccessful()) {
						return Mono.error(failedRequest(
								"Numista collection save failed",
								NumistaCollectionSaveRequest.SAVE_URL,
								response));
					}
					if (!response.hasBody()) {
						log.warn(
								"Numista save returned {} with empty body for coinId={}",
								response.statusCode(),
								request.getCoinId());
					}
					return Mono.just(response.body());
				})
				.doOnError(
						e -> log.error("Failed to save Numista collection item for coinId={}", request.getCoinId(), e));
	}

	/**
	 * Removes a collection row on Numista. Success is any {@code 2xx} response; an
	 * empty
	 * body on {@code 200} is normal.
	 */
	@LogExecutionTime
	public Mono<Boolean> removeCollectionItem(NumistaCollectionRemoveRequest request, ColligendisUser user) {
		return Mono.defer(() -> webPageClient
				.postForm(NumistaCollectionRemoveRequest.REMOVE_URL, requireCookie(user), request.toFormData(),
						COLLECTION_REQUEST_HEADERS))
				.flatMap(response -> {
					if (!response.is2xxSuccessful()) {
						return Mono.error(failedRequest(
								"Numista collection remove failed",
								NumistaCollectionRemoveRequest.REMOVE_URL,
								response));
					}
					if (response.hasBody() && looksLikeErrorPage(response.body())) {
						return Mono.error(new NumistaCollectionHttpException(
								"Numista collection remove returned an error page",
								response.statusCode(),
								response.body()));
					}
					log.info(
							"Numista remove succeeded: item={} collectible={} version={} status={} bodyLength={}",
							request.getItem(),
							request.getCollectible(),
							request.getVersion(),
							response.statusCode(),
							response.bodyLength());
					return Mono.just(Boolean.TRUE);
				})
				.doOnError(e -> log.error(
						"Failed to remove Numista collection item item={} collectible={}",
						request.getItem(),
						request.getCollectible(),
						e));
	}

	String resolveCookie(ColligendisUser user) {
		if (user != null && StringUtils.hasText(user.getNumistaCookie())) {
			return user.getNumistaCookie().strip();
		}
		return "";
	}

	private String requireCookie(ColligendisUser user) {
		String cookie = resolveCookie(user);
		if (useCookies && !StringUtils.hasText(cookie)) {
			throw new NumistaCollectionHttpException(COOKIE_NOT_CONFIGURED, 0, "");
		}
		return cookie;
	}

	private static NumistaCollectionHttpException failedRequest(
			String message,
			String url,
			WebPageResponse response) {
		return new NumistaCollectionHttpException(
				message + " (" + url + ", HTTP " + response.statusCode() + ")",
				response.statusCode(),
				response.body());
	}

	private static boolean looksLikeErrorPage(String body) {
		if (!StringUtils.hasText(body)) {
			return false;
		}
		String lower = body.toLowerCase();
		return lower.contains("class=\"error\"")
				|| lower.contains("id=\"error\"")
				|| lower.contains("access denied")
				|| lower.contains("please log in")
				|| lower.contains("session expired");
	}

	/**
	 * Authenticated {@code vos_pieces.php} pages include the collection table or
	 * title.
	 * Generic {@link #looksLikeErrorPage} checks false-positive on full HTML pages.
	 */
	static boolean isCollectionPageHtml(String body) {
		if (!StringUtils.hasText(body)) {
			return false;
		}
		String lower = body.toLowerCase();
		return lower.contains("id=\"vos_pieces\"")
				|| lower.contains("table id=vos_pieces")
				|| lower.contains("<title>my collection");
	}

	/**
	 * Returns {@code true} when the response body is a Cloudflare anti-bot
	 * challenge page. Two markers must be present to avoid false positives.
	 */
	static boolean isCloudflareChallenge(String body) {
		if (!StringUtils.hasText(body)) {
			return false;
		}
		return body.contains("challenge-verify.php")
				|| (body.contains("Checking connection") && body.contains("recaptcha"));
	}
}
