package com.colligendis.server.util.web;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Reactive HTTP GET client backed by {@link WebClient}.
 * <p>
 * Pass a {@code Cookie} header value when the target site requires an authenticated session.
 * Configure the default {@code Accept} header via {@code colligendis.web.client.accept}
 * ({@code html}, {@code json}, {@code html-and-json}, or a custom value such as
 * {@code application/json}).
 */
@Slf4j
@Component
public class WebPageClient {

	public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15";

	private static final int DEFAULT_MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;

	private final WebClient client;
	private final boolean requireCookieWhenEnabled;
	private final boolean useCookies;
	private final String defaultAcceptHeader;

	public WebPageClient(
			@Value("${colligendis.web.client.use-cookies:true}") boolean useCookies,
			@Value("${colligendis.web.client.require-cookie:false}") boolean requireCookieWhenEnabled,
			@Value("${colligendis.web.client.max-in-memory-size:" + DEFAULT_MAX_IN_MEMORY_SIZE + "}") int maxInMemorySize,
			@Value("${colligendis.web.client.user-agent:Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15}") String userAgent,
			@Value("${colligendis.web.client.accept:html}") String acceptConfig) {
		this.useCookies = useCookies;
		this.requireCookieWhenEnabled = requireCookieWhenEnabled;
		this.defaultAcceptHeader = WebPageAccept.resolve(acceptConfig).headerValue();
		this.client = WebClient.builder()
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
				.exchangeStrategies(ExchangeStrategies.builder()
						.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize))
						.build())
				.defaultHeader(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name())
				.defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
				.defaultHeader(HttpHeaders.USER_AGENT, userAgent)
				.build();
	}

	/**
	 * GET {@code url} using the configured default {@code Accept} header.
	 */
	public Mono<WebPageResponse> loadPage(String url) {
		return loadPage(url, null, null);
	}

	/**
	 * GET {@code url}, sending {@code cookie} as the {@code Cookie} header when non-blank and
	 * cookies are enabled, using the configured default {@code Accept} header.
	 */
	public Mono<WebPageResponse> loadPage(String url, String cookie) {
		return loadPage(url, cookie, null);
	}

	/**
	 * GET {@code url} with an explicit {@code Accept} preset (overrides the configured default
	 * when non-null).
	 */
	public Mono<WebPageResponse> loadPage(String url, String cookie, WebPageAccept accept) {
		return loadPage(url, cookie, accept, null);
	}

	/**
	 * GET {@code url} with optional extra request headers (e.g. {@code Origin}, {@code Referer}).
	 */
	public Mono<WebPageResponse> loadPage(String url, String cookie, WebPageAccept accept,
			Consumer<HttpHeaders> extraHeaders) {
		String normalizedUrl = normalizeUrl(url);
		String cookieHeader = resolveCookieHeader(cookie);
		String acceptHeader = resolveAcceptHeader(accept);

		log.debug("GET {} accept={} cookieLength={}", normalizedUrl, acceptHeader, cookieHeader.length());

		return client.get()
				.uri(normalizedUrl)
				.header(HttpHeaders.ACCEPT, acceptHeader)
				.headers(headers -> applyRequestHeaders(headers, cookieHeader, extraHeaders))
				.exchangeToMono(response -> toWebPageResponse("GET", normalizedUrl, response))
				.onErrorResume(WebClientResponseException.class,
						ex -> toWebPageResponseFromException("GET", normalizedUrl, ex));
	}

	/**
	 * POST {@code application/x-www-form-urlencoded} form data to {@code url}.
	 */
	public Mono<WebPageResponse> postForm(String url, String cookie, MultiValueMap<String, String> formData) {
		return postForm(url, cookie, formData, null);
	}

	/**
	 * POST form data with optional extra request headers.
	 */
	public Mono<WebPageResponse> postForm(String url, String cookie, MultiValueMap<String, String> formData,
			Consumer<HttpHeaders> extraHeaders) {
		String normalizedUrl = normalizeUrl(url);
		String cookieHeader = resolveCookieHeader(cookie);

		log.debug("POST {} cookieLength={} fields={}", normalizedUrl, cookieHeader.length(), formData.keySet());

		return client.post()
				.uri(normalizedUrl)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.header(HttpHeaders.ACCEPT, defaultAcceptHeader)
				.headers(headers -> applyRequestHeaders(headers, cookieHeader, extraHeaders))
				.body(BodyInserters.fromFormData(formData))
				.exchangeToMono(response -> toWebPageResponse("POST", normalizedUrl, response))
				.onErrorResume(WebClientResponseException.class,
						ex -> toWebPageResponseFromException("POST", normalizedUrl, ex));
	}

	/**
	 * GET {@code url} with {@code Accept: application/json}.
	 */
	public Mono<String> loadJson(String url) {
		return loadJson(url, null);
	}

	/**
	 * GET {@code url} with {@code Accept: application/json}, optionally sending a cookie.
	 */
	public Mono<String> loadJson(String url, String cookie) {
		String normalizedUrl = normalizeUrl(url);
		return loadPage(normalizedUrl, cookie, WebPageAccept.JSON)
				.flatMap(response -> toSuccessfulBody(normalizedUrl, response));
	}

	/**
	 * GET {@code url} and return the HTML body, failing on non-2xx or empty body.
	 */
	public Mono<String> loadPageHtml(String url, String cookie) {
		String normalizedUrl = normalizeUrl(url);
		return loadPage(normalizedUrl, cookie, WebPageAccept.HTML)
				.flatMap(response -> toSuccessfulBody(normalizedUrl, response));
	}

	public Mono<String> loadPageHtml(String url) {
		return loadPageHtml(url, null);
	}

	/**
	 * GET {@code url} and parse the HTML body as a Jsoup {@link Document}.
	 */
	public Mono<Document> loadPageDocument(String url, String cookie) {
		String normalizedUrl = normalizeUrl(url);
		return loadPage(normalizedUrl, cookie, WebPageAccept.HTML)
				.flatMap(response -> toDocument(normalizedUrl, response));
	}

	public Mono<Document> loadPageDocument(String url) {
		return loadPageDocument(url, null);
	}

	private String resolveAcceptHeader(WebPageAccept accept) {
		return accept != null ? accept.headerValue() : defaultAcceptHeader;
	}

	private static Mono<Document> toDocument(String url, WebPageResponse response) {
		if (response.statusCode() == HttpStatus.NOT_FOUND.value()) {
			return Mono.empty();
		}
		if (!response.is2xxSuccessful() || !response.hasBody()) {
			return Mono.error(new WebPageLoadException(
					"Failed to load page: HTTP " + response.statusCode(),
					url,
					response.statusCode()));
		}
		return Mono.just(Jsoup.parse(response.body(), url));
	}

	private static Mono<String> toSuccessfulBody(String url, WebPageResponse response) {
		if (response.statusCode() == HttpStatus.NOT_FOUND.value()) {
			return Mono.error(new WebPageLoadException("Page not found", url, response.statusCode()));
		}
		if (!response.is2xxSuccessful()) {
			return Mono.error(new WebPageLoadException(
					"HTTP " + response.statusCode(),
					url,
					response.statusCode()));
		}
		if (!response.hasBody()) {
			return Mono.error(new WebPageLoadException("Empty response body", url));
		}
		return Mono.just(response.body());
	}

	private String resolveCookieHeader(String cookie) {
		if (!useCookies) {
			return "";
		}
		String normalized = cookie == null ? "" : cookie.strip();
		if (requireCookieWhenEnabled && !StringUtils.hasText(normalized)) {
			throw new WebPageLoadException(
					"Cookie is required but was not provided",
					"");
		}
		return normalized;
	}

	private static void applyRequestHeaders(HttpHeaders headers, String cookie, Consumer<HttpHeaders> extraHeaders) {
		applyCookieHeader(headers, cookie);
		if (extraHeaders != null) {
			extraHeaders.accept(headers);
		}
	}

	private Mono<WebPageResponse> toWebPageResponse(String method, String url,
			org.springframework.web.reactive.function.client.ClientResponse response) {
		return response.bodyToMono(String.class)
				.defaultIfEmpty("")
				.map(body -> {
					WebPageResponse pageResponse = WebPageResponse.of(response.statusCode(), body);
					logResponse(method, url, pageResponse, response.headers().asHttpHeaders());
					return pageResponse;
				});
	}

	private Mono<WebPageResponse> toWebPageResponseFromException(String method, String url,
			WebClientResponseException ex) {
		String body = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
		WebPageResponse pageResponse = new WebPageResponse(ex.getStatusCode().value(), body);
		logResponse(method, url, pageResponse, ex.getHeaders());
		return Mono.just(pageResponse);
	}

	private static void applyCookieHeader(HttpHeaders headers, String cookie) {
		if (StringUtils.hasText(cookie)) {
			headers.set(HttpHeaders.COOKIE, cookie);
		}
	}

	private static String normalizeUrl(String url) {
		if (!StringUtils.hasText(url)) {
			throw new WebPageLoadException("URL is required", "");
		}
		return url.strip();
	}

	private void logResponse(String method, String url, WebPageResponse response, HttpHeaders responseHeaders) {
		String contentType = responseHeaders.getContentType() != null
				? responseHeaders.getContentType().toString()
				: "";
		log.info(
				"{} {} -> status={}, contentType={}, bodyLength={}",
				method,
				url,
				response.statusCode(),
				contentType,
				response.bodyLength());
		if (!response.is2xxSuccessful()) {
			log.warn("{} {} error bodyPreview={}", method, url, preview(response.body(), 500));
		} else if (log.isDebugEnabled()) {
			log.debug("{} {} success bodyPreview={}", method, url, preview(response.body(), 300));
		}
	}

	private static String preview(String body, int maxLength) {
		if (!StringUtils.hasText(body)) {
			return "";
		}
		String normalized = body.strip();
		return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
	}
}
