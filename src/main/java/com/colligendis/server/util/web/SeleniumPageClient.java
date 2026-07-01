package com.colligendis.server.util.web;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.colligendis.server.parser.numista.NumistaParseUtils;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Loads HTML pages through a persistent headless Chrome session (Selenium).
 * Used by {@link com.colligendis.server.parser.numista.PageLoader} so Numista
 * pages render JavaScript (Select2, variety tables) and pass Cloudflare checks.
 */
@Slf4j
@Component
public class SeleniumPageClient {

	private static final String NUMISTA_HOME_URL = "https://en.numista.com/";
	private static final String USER_AGENT = WebPageClient.DEFAULT_USER_AGENT;
	private static final long WARMUP_INTERVAL_MS = 30 * 60 * 1000L;

	private final boolean headless;
	private final long challengeWaitMs;
	private final boolean challengeWaitForever;
	private final long navigateTimeoutMs;
	private final Path profileDir;

	private final Object driverLock = new Object();
	private WebDriver driver;
	private String appliedCookieHeader = "";
	private final AtomicLong lastWarmupMs = new AtomicLong(0);

	public SeleniumPageClient(
			@Value("${colligendis.numista.selenium.headless:true}") boolean headless,
			@Value("${colligendis.numista.selenium.challenge-wait-ms:180000}") long challengeWaitMs,
			@Value("${colligendis.numista.selenium.challenge-wait-forever:false}") boolean challengeWaitForever,
			@Value("${colligendis.numista.selenium.navigate-timeout-ms:60000}") long navigateTimeoutMs,
			@Value("${colligendis.numista.selenium.profile-dir:${user.home}/.colligendis/selenium-numista-profile}") String profileDir) {
		this.headless = resolveHeadless(headless);
		this.challengeWaitMs = resolveChallengeWaitMs(challengeWaitMs);
		this.challengeWaitForever = challengeWaitForever
				|| Boolean.parseBoolean(System.getProperty("numista.selenium.challenge-wait-forever", "false"));
		this.navigateTimeoutMs = resolveNavigateTimeoutMs(navigateTimeoutMs);
		this.profileDir = Paths.get(profileDir).toAbsolutePath().normalize();
		log.info(
				"Selenium page client: headless={}, challengeWaitMs={}, challengeWaitForever={}, navigateTimeoutMs={}, profile={}",
				this.headless, this.challengeWaitMs, this.challengeWaitForever, this.navigateTimeoutMs,
				this.profileDir);
	}

	public Mono<SeleniumPageResult> loadPage(String url, String cookie) {
		String normalizedUrl = normalizeUrl(url);
		String cookieHeader = cookie == null ? "" : cookie.strip();
		return Mono.fromCallable(() -> loadPageBlocking(normalizedUrl, cookieHeader))
				.subscribeOn(Schedulers.boundedElastic());
	}

	private SeleniumPageResult loadPageBlocking(String url, String cookieHeader) {
		synchronized (driverLock) {
			WebDriver webDriver = getOrCreateDriver();
			applyCookies(webDriver, cookieHeader, url);
			navigate(webDriver, url);
			waitPastBotChallenge(webDriver, url, effectiveChallengeWaitMs());

			String html = webDriver.getPageSource();
			if (NumistaParseUtils.isBotChallengeHtml(html)) {
				log.info("Bot challenge on {}, warming session via {}", url, NUMISTA_HOME_URL);
				navigate(webDriver, NUMISTA_HOME_URL);
				waitPastBotChallenge(webDriver, NUMISTA_HOME_URL, effectiveChallengeWaitMs());
				lastWarmupMs.set(System.currentTimeMillis());

				navigate(webDriver, url);
				waitPastBotChallenge(webDriver, url, effectiveChallengeWaitMs());
				html = webDriver.getPageSource();
			}

			if (NumistaParseUtils.isBotChallengeHtml(html)) {
				long extendedWaitMs = challengeWaitForever ? Long.MAX_VALUE : challengeWaitMs * 3;
				if (!headless) {
					log.warn(
							"Solve Cloudflare in the visible browser window if prompted, then wait{}...",
							challengeWaitForever ? " (no timeout)" : " (up to " + extendedWaitMs / 1000 + " s)");
				} else {
					log.warn(
							"Cloudflare challenge still present; waiting{} for auto-clearance (cf_clearance cookie)...",
							challengeWaitForever ? " indefinitely" : " up to " + extendedWaitMs / 1000 + " s");
				}
				waitPastBotChallenge(webDriver, url, extendedWaitMs);
				html = webDriver.getPageSource();
			}

			int statusCode = inferStatusCode(webDriver, html);
			return new SeleniumPageResult(statusCode, html);
		}
	}

	private WebDriver getOrCreateDriver() {
		if (isDriverAlive(driver)) {
			return driver;
		}
		closeDriverQuietly();
		initDriver();
		return driver;
	}

	private void initDriver() {
		try {
			Files.createDirectories(profileDir);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to init Selenium profile directory", e);
		}

		WebDriverManager.chromedriver().setup();

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--user-data-dir=" + profileDir);
		options.addArguments("--disable-blink-features=AutomationControlled");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=en-US");
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
		options.setExperimentalOption("useAutomationExtension", false);
		options.addArguments("--user-agent=" + USER_AGENT);

		driver = new ChromeDriver(options);
		driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(navigateTimeoutMs));
		driver.manage().timeouts().scriptTimeout(Duration.ofMillis(navigateTimeoutMs));
		appliedCookieHeader = "";

		long sinceWarmup = System.currentTimeMillis() - lastWarmupMs.get();
		if (sinceWarmup > WARMUP_INTERVAL_MS) {
			try {
				navigate(driver, NUMISTA_HOME_URL);
				waitPastBotChallenge(driver, NUMISTA_HOME_URL, effectiveChallengeWaitMs());
				lastWarmupMs.set(System.currentTimeMillis());
			} catch (RuntimeException e) {
				log.warn("Selenium warmup navigation failed: {}", e.getMessage());
			}
		}

		log.info("Selenium Chrome session started (headless={}, profile={})", headless, profileDir);
	}

	private void navigate(WebDriver webDriver, String url) {
		try {
			webDriver.get(url);
			waitForDocumentReady(webDriver);
		} catch (TimeoutException e) {
			log.warn("Navigate timed out after {} ms for {}. Continuing with current page content.",
					navigateTimeoutMs, url);
		}
	}

	private static void waitForDocumentReady(WebDriver webDriver) {
		try {
			new WebDriverWait(webDriver, Duration.ofMillis(5_000)).until(d -> {
				Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(state);
			});
		} catch (TimeoutException ignored) {
			// page may still be usable
		}
	}

	private void waitPastBotChallenge(WebDriver webDriver, String url, long maxWaitMs) {
		if (challengeWaitForever) {
			maxWaitMs = Long.MAX_VALUE;
		}
		boolean unbounded = maxWaitMs == Long.MAX_VALUE;
		long deadline = unbounded ? Long.MAX_VALUE : System.currentTimeMillis() + maxWaitMs;
		long nextLogAt = 0;
		long startedAt = System.currentTimeMillis();
		while (unbounded || System.currentTimeMillis() < deadline) {
			String html = webDriver.getPageSource();
			if (!NumistaParseUtils.isBotChallengeHtml(html)) {
				if (System.currentTimeMillis() - startedAt > 5_000) {
					log.info("Numista bot challenge cleared for {} (waited {} s)", url,
							(System.currentTimeMillis() - startedAt) / 1000);
				}
				return;
			}
			long now = System.currentTimeMillis();
			if (now >= nextLogAt) {
				long waitedSec = (now - startedAt) / 1000;
				if (unbounded) {
					log.info("Waiting for Numista bot challenge to clear: {} ({} s, no timeout)", url, waitedSec);
				} else {
					long remainingSec = Math.max(0, (deadline - now) / 1000);
					log.info("Waiting for Numista bot challenge to clear: {} ({} s elapsed, ~{} s left)",
							url, waitedSec, remainingSec);
				}
				nextLogAt = now + 5_000;
			}
			sleepQuietly(1_000);
		}
	}

	private void applyCookies(WebDriver webDriver, String cookieHeader, String url) {
		if (!StringUtils.hasText(cookieHeader) || cookieHeader.equals(appliedCookieHeader)) {
			return;
		}
		String cookieDomain = cookieDomainForUrl(url);
		if (cookieDomain == null) {
			return;
		}
		navigate(webDriver, NUMISTA_HOME_URL);
		for (Cookie cookie : toSeleniumCookies(cookieHeader, cookieDomain)) {
			try {
				webDriver.manage().addCookie(cookie);
			} catch (RuntimeException e) {
				log.debug("Skipping cookie {}: {}", cookie.getName(), e.getMessage());
			}
		}
		appliedCookieHeader = cookieHeader;
	}

	private static List<Cookie> toSeleniumCookies(String rawCookieHeader, String cookieDomain) {
		if (!StringUtils.hasText(rawCookieHeader)) {
			return List.of();
		}
		List<Cookie> cookies = new ArrayList<>();
		Arrays.stream(rawCookieHeader.split(";"))
				.map(String::trim)
				.filter(part -> !part.isEmpty() && part.contains("="))
				.forEach(part -> {
					String[] kv = part.split("=", 2);
					String name = kv[0].trim();
					String value = kv.length > 1 ? kv[1] : "";
					if (name.isEmpty()) {
						return;
					}
					cookies.add(new Cookie.Builder(name, value).domain(cookieDomain).path("/").build());
				});
		return cookies;
	}

	private static String cookieDomainForUrl(String urlString) {
		try {
			String host = URI.create(urlString).getHost();
			if (!StringUtils.hasText(host)) {
				return null;
			}
			if (host.endsWith("numista.com")) {
				return ".numista.com";
			}
			return host.startsWith(".") ? host : "." + host;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static int inferStatusCode(WebDriver webDriver, String html) {
		if (html == null) {
			return 0;
		}
		String title = webDriver.getTitle();
		if (title != null && title.contains("404")) {
			return 404;
		}
		String lower = html.toLowerCase();
		if (lower.contains("404 not found") || lower.contains("page not found")) {
			return 404;
		}
		return 200;
	}

	private static boolean isDriverAlive(WebDriver webDriver) {
		if (webDriver == null) {
			return false;
		}
		try {
			webDriver.getTitle();
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private void closeDriverQuietly() {
		if (driver == null) {
			return;
		}
		try {
			driver.quit();
		} catch (RuntimeException ignored) {
			// driver may already be closed
		}
		driver = null;
		appliedCookieHeader = "";
	}

	@PreDestroy
	void shutdown() {
		synchronized (driverLock) {
			closeDriverQuietly();
		}
	}

	private long effectiveChallengeWaitMs() {
		return challengeWaitForever ? Long.MAX_VALUE : challengeWaitMs;
	}

	private static boolean resolveHeadless(boolean fromConfig) {
		String property = System.getProperty("numista.selenium.headless");
		if (property != null && !property.isBlank()) {
			return Boolean.parseBoolean(property.strip());
		}
		return fromConfig;
	}

	private static long resolveChallengeWaitMs(long fromConfig) {
		String property = System.getProperty("numista.selenium.challenge-wait-ms");
		if (property != null && !property.isBlank()) {
			try {
				return Long.parseLong(property.strip());
			} catch (NumberFormatException e) {
				log.warn("Invalid numista.selenium.challenge-wait-ms={}, using config value", property);
			}
		}
		return fromConfig > 0 ? fromConfig : 180_000L;
	}

	private static long resolveNavigateTimeoutMs(long fromConfig) {
		String property = System.getProperty("numista.selenium.navigate-timeout-ms");
		if (property != null && !property.isBlank()) {
			try {
				return Long.parseLong(property.strip());
			} catch (NumberFormatException e) {
				log.warn("Invalid numista.selenium.navigate-timeout-ms={}, using config value", property);
			}
		}
		return fromConfig > 0 ? fromConfig : 60_000L;
	}

	private static String normalizeUrl(String url) {
		if (!StringUtils.hasText(url)) {
			throw new WebPageLoadException("URL is required", "");
		}
		return url.strip();
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public record SeleniumPageResult(int statusCode, String html) {
		public boolean is2xxSuccessful() {
			return statusCode >= 200 && statusCode < 300;
		}
	}
}
