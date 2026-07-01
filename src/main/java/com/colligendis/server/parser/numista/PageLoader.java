package com.colligendis.server.parser.numista;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.util.web.SeleniumPageClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
@Slf4j
public class PageLoader extends Parser {

	private static final int MIN_CACHE_BYTES = 100 * 1024;

	private static final Path NTYPES_PAGES_DIR = Paths
			.get("/Users/kirillbobryakov/Coins/Numista/NTYPES_PAGES")
			.toAbsolutePath()
			.normalize();

	private final SeleniumPageClient seleniumPageClient;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return loadPageByURL().apply(numistaPage);
	}

	public Function<NumistaPage, Mono<NumistaPage>> loadPageByURL() {
		return numistaPage -> Mono.defer(() -> {
			numistaPage.getPipelineStepLogger()
					.infoBlue("Parsing started for item with nid: {} and url: {}", numistaPage.nid, numistaPage.url);
			return loadDocument(numistaPage);
		})
				.retryWhen(
						Retry.backoff(3, Duration.ofSeconds(1))
								.maxBackoff(Duration.ofSeconds(3))
								.filter(ex -> !(ex instanceof FileNotFoundException)
										&& !(ex instanceof CloudflareBlockException)) // no point retrying these
				)
				.doOnError(e -> numistaPage.getPipelineStepLogger().error("Failed to load after retries: {} : {}",
						numistaPage.url, e))
				.onErrorResume(e -> Mono.empty())
				.map(doc -> {
					numistaPage.page = doc;
					return numistaPage;
				});
	}

	public Mono<Document> loadDocument(NumistaPage numistaPage) {
		return numistaPage.getNumistaParserUserMono()
				.map(PageLoader::resolveCookieHeader)
				.defaultIfEmpty("")
				.flatMap(cookie -> Mono.fromCallable(() -> Optional.ofNullable(tryLoadLocal(numistaPage)))
						.subscribeOn(Schedulers.boundedElastic())
						.flatMap(local -> local
								.map(Mono::just)
								.orElseGet(() -> fetchFromNetwork(numistaPage, cookie))));
	}

	private static String resolveCookieHeader(ColligendisUser user) {
		if (user == null || user.getNumistaCookie() == null) {
			return "";
		}
		return user.getNumistaCookie().strip();
	}

	private static Document tryLoadLocal(NumistaPage numistaPage) throws IOException {
		Path localHtml = localHtmlPath(numistaPage.nid);
		if (!Files.isRegularFile(localHtml)) {
			return null;
		}
		long fileBytes = Files.size(localHtml);
		if (fileBytes <= 0 || fileBytes < MIN_CACHE_BYTES) {
			numistaPage.getPipelineStepLogger().trace(
					"Local cache miss for nid {} ({} bytes, minimum {} KB)",
					numistaPage.nid,
					fileBytes,
					MIN_CACHE_BYTES / 1024);
			return null;
		}
		numistaPage.getPipelineStepLogger().trace("Loading from local file {}", localHtml);
		return Jsoup.parse(localHtml.toFile(), StandardCharsets.UTF_8.name(), numistaPage.url);
	}

	private Mono<Document> fetchFromNetwork(NumistaPage numistaPage, String cookie) {
		numistaPage.getPipelineStepLogger().trace("Loading via Selenium {}", numistaPage.url);

		return seleniumPageClient.loadPage(numistaPage.url, cookie)
				.flatMap(response -> {
					if (response.statusCode() == 404) {
						numistaPage.getPipelineStepLogger().warning("404 Not Found: {}", numistaPage.url);
						return Mono.error(new FileNotFoundException(numistaPage.url));
					}
					String html = response.html() != null ? response.html() : "";
					if (isCloudflareChallenge(html) || NumistaParseUtils.isBotChallengeHtml(html)) {
						numistaPage.getPipelineStepLogger().warning(
								"Cloudflare anti-bot challenge still present for nid={} url={}. "
										+ "NType parsing will be skipped for this item.",
								numistaPage.nid, numistaPage.url);
						return Mono.error(new CloudflareBlockException(numistaPage.url));
					}
					return Mono.fromCallable(() -> cacheAndParse(numistaPage, html))
							.subscribeOn(Schedulers.boundedElastic());
				});
	}

	/**
	 * Returns {@code true} when the response body is a Cloudflare anti-bot
	 * challenge page rather than the real Numista content. The check is
	 * intentionally conservative (two distinct markers must be present).
	 */
	static boolean isCloudflareChallenge(String html) {
		if (html == null || html.isBlank()) {
			return false;
		}
		return html.contains("challenge-verify.php")
				|| (html.contains("Checking connection") && html.contains("recaptcha"));
	}

	private static Document cacheAndParse(NumistaPage numistaPage, String html) throws IOException {
		int htmlBytes = html.getBytes(StandardCharsets.UTF_8).length;
		if (!html.isEmpty() && htmlBytes >= MIN_CACHE_BYTES) {
			Path localHtml = localHtmlPath(numistaPage.nid);
			Files.createDirectories(localHtml.getParent());
			Files.writeString(localHtml, html, StandardCharsets.UTF_8);
		} else if (!html.isEmpty()) {
			numistaPage.getPipelineStepLogger().trace(
					"Skipping local cache for nid {} ({} bytes < {} KB)",
					numistaPage.nid,
					htmlBytes,
					MIN_CACHE_BYTES / 1024);
		}
		return Jsoup.parse(html, numistaPage.url);
	}

	private static Path localHtmlPath(String nid) {
		Path resolved = NTYPES_PAGES_DIR.resolve(nid + ".html").normalize();
		if (!resolved.startsWith(NTYPES_PAGES_DIR)) {
			throw new IllegalArgumentException("Invalid nid for cache path: " + nid);
		}
		return resolved;
	}
}
