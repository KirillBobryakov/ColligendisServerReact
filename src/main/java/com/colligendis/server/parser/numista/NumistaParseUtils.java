package com.colligendis.server.parser.numista;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.common.service.YearService;
import com.colligendis.server.logger.LogExecutionTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class NumistaParseUtils {

	private final YearService yearService;

	@Value("${colligendis.numista.cookie:}")
	private String numistaCookie;

	@Value("${colligendis.numista.use-cookies:true}")
	private boolean numistaUseCookies;

	@Value("${colligendis.numista.playwright.challenge-wait-ms:180000}")
	private long challengeWaitMs;

	@Value("${colligendis.numista.playwright.challenge-wait-forever:false}")
	private boolean challengeWaitForever;

	/**
	 * Cookie header for Numista (from {@code colligendis.numista.cookie}). Filled
	 * in
	 * {@link #applyNumistaCookieFromConfig()} for static helpers.
	 */
	private static volatile String configuredNumistaCookie = "";

	private static volatile boolean configuredUseCookies = true;

	private static volatile long configuredChallengeWaitMs = 180_000L;

	private static volatile boolean configuredChallengeWaitForever = false;

	@PostConstruct
	void applyNumistaCookieFromConfig() {
		configuredNumistaCookie = numistaCookie == null ? "" : numistaCookie.strip();
		configuredUseCookies = numistaUseCookies;
		configuredChallengeWaitMs = resolveChallengeWaitMs(challengeWaitMs);
		configuredChallengeWaitForever = challengeWaitForever
				|| Boolean.parseBoolean(System.getProperty("numista.playwright.challenge-wait-forever", "false"));
		log.info(
				"Numista cookie config: useCookies={}, cookieLength={}, challengeWaitMs={}, challengeWaitForever={}",
				configuredUseCookies, configuredNumistaCookie.length(), configuredChallengeWaitMs,
				configuredChallengeWaitForever);
	}

	private static long resolveChallengeWaitMs(long fromConfig) {
		String property = System.getProperty("numista.playwright.challenge-wait-ms");
		if (property != null && !property.isBlank()) {
			try {
				return Long.parseLong(property.strip());
			} catch (NumberFormatException e) {
				log.warn("Invalid numista.playwright.challenge-wait-ms={}, using config value", property);
			}
		}
		return fromConfig > 0 ? fromConfig : 180_000L;
	}

	// public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS
	// X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3
	// Safari/605.1.15";

	private static final double NAVIGATE_TIMEOUT_MS = Double.parseDouble(
			System.getProperty("numista.playwright.navigate-timeout-ms", "60000"));
	private static final Path PLAYWRIGHT_PROFILE_DIR = Paths
			.get(System.getProperty("user.home"), ".colligendis", "playwright-numista-profile");
	private static final boolean PLAYWRIGHT_HEADLESS = Boolean
			.parseBoolean(System.getProperty("numista.playwright.headless", "true"));

	private static final String BOT_CHALLENGE_MARKER = "Enable JavaScript and cookies to continue";
	private static final String NUMISTA_HOME_URL = "https://en.numista.com/";

	// @LogExecutionTime
	// public static Document loadPageByURL(String urlString) {
	// return loadPageByURL(urlString, null);
	// }

	// /**
	// * @param cookieHeaderOverride when non-blank, used instead of configured /
	// * fallback cookie
	// */
	// @LogExecutionTime
	// public static Document loadPageByURL(String urlString, String
	// cookieHeaderOverride) {
	// try {
	// Files.createDirectories(PLAYWRIGHT_PROFILE_DIR);
	// } catch (IOException e) {
	// throw new IllegalStateException("Failed to init Playwright profile
	// directory", e);
	// }

	// if (configuredUseCookies && configuredNumistaCookie.isEmpty()) {
	// log.warn(
	// "Numista cookie is empty (set NUMISTA_COOKIE or colligendis.numista.cookie).
	// "
	// + "Cloudflare will likely block headless requests.");
	// }

	// Page page = null;
	// try {
	// BrowserContext context = NumistaPlaywrightSession.getContext();
	// NumistaPlaywrightSession.applyCookies(context, cookieHeaderOverride,
	// urlString);
	// page = context.newPage();

	// Response response = navigate(page, urlString);
	// if (response != null && response.status() == 404) {
	// return null;
	// }

	// waitPastBotChallenge(page, urlString);

	// if (isBotChallengeHtml(page.content())) {
	// log.info("Bot challenge on {}, warming session via {}", urlString,
	// NUMISTA_HOME_URL);
	// navigate(page, NUMISTA_HOME_URL);
	// waitPastBotChallenge(page, NUMISTA_HOME_URL);
	// NumistaPlaywrightSession.markWarmedUp();

	// response = navigate(page, urlString);
	// if (response != null && response.status() == 404) {
	// return null;
	// }
	// waitPastBotChallenge(page, urlString);
	// }

	// String html = page.content();
	// if (isBotChallengeHtml(html)) {
	// long extendedWaitMs = configuredChallengeWaitForever
	// ? Long.MAX_VALUE
	// : configuredChallengeWaitMs * 3;
	// if (!PLAYWRIGHT_HEADLESS) {
	// log.warn(
	// "Solve Cloudflare in the visible browser window if prompted, then wait{}...",
	// configuredChallengeWaitForever ? " (no timeout)"
	// : " (up to " + extendedWaitMs / 1000 + " s)");
	// } else {
	// log.warn(
	// "Cloudflare challenge still present; waiting{} for auto-clearance
	// (cf_clearance cookie)...",
	// configuredChallengeWaitForever ? " indefinitely"
	// : " up to " + extendedWaitMs / 1000 + " s");
	// }
	// waitPastBotChallenge(page, urlString, extendedWaitMs);
	// html = page.content();
	// }

	// if (isBotChallengeHtml(html)) {
	// log.error(
	// "Numista bot challenge still present for {}. Set a fresh Cookie header "
	// + "(cf_clearance, PHPSESSID) in NUMISTA_COOKIE, or run once with "
	// + "-Dnumista.playwright.headless=false and open {} in the launched browser.",
	// urlString, NUMISTA_HOME_URL);
	// return null;
	// }
	// return Jsoup.parse(html, urlString);
	// } catch (PlaywrightException e) {
	// log.error("Error loading page by URL: {}", urlString, e);
	// return null;
	// } finally {
	// if (page != null) {
	// try {
	// page.close();
	// } catch (PlaywrightException ignored) {
	// // page may already be closed if context was reset
	// }
	// }
	// }
	// }

	private static Response navigate(Page page, String urlString) {
		try {
			return page.navigate(
					urlString,
					new NavigateOptions()
							.setWaitUntil(WaitUntilState.LOAD)
							.setTimeout(NAVIGATE_TIMEOUT_MS));
		} catch (TimeoutError e) {
			log.warn("Navigate timed out after {} ms for {}. Will continue with current page content.",
					(int) NAVIGATE_TIMEOUT_MS, urlString);
			return null;
		}
	}

	/** True when HTML is Cloudflare / bot wall instead of Numista content. */
	public static boolean isBotChallengeHtml(String html) {
		if (html == null || html.isBlank()) {
			return false;
		}
		return html.contains(BOT_CHALLENGE_MARKER)
				|| html.contains("challenge-platform")
				|| html.contains("cf-browser-verification");
	}

	public static boolean isBotChallengeDocument(Document document) {
		if (document == null) {
			return false;
		}
		return isBotChallengeHtml(document.html());
	}

	/**
	 * Polls until the challenge page is replaced or the configured wait budget
	 * elapses.
	 */
	private static void waitPastBotChallenge(Page page, String urlString) {
		waitPastBotChallenge(page, urlString, effectiveChallengeWaitMs());
	}

	private static long effectiveChallengeWaitMs() {
		if (configuredChallengeWaitForever) {
			return Long.MAX_VALUE;
		}
		return configuredChallengeWaitMs;
	}

	private static void waitPastBotChallenge(Page page, String urlString, long maxWaitMs) {
		if (configuredChallengeWaitForever) {
			maxWaitMs = Long.MAX_VALUE;
		}
		boolean unbounded = maxWaitMs == Long.MAX_VALUE;
		long deadline = unbounded ? Long.MAX_VALUE : System.currentTimeMillis() + maxWaitMs;
		long nextLogAt = 0;
		long startedAt = System.currentTimeMillis();
		while (unbounded || System.currentTimeMillis() < deadline) {
			String html = page.content();
			if (!isBotChallengeHtml(html)) {
				if (System.currentTimeMillis() - startedAt > 5_000) {
					log.info("Numista bot challenge cleared for {} (waited {} s)", urlString,
							(System.currentTimeMillis() - startedAt) / 1000);
				}
				return;
			}
			long now = System.currentTimeMillis();
			if (now >= nextLogAt) {
				long waitedSec = (now - startedAt) / 1000;
				if (unbounded) {
					log.info("Waiting for Numista bot challenge to clear: {} ({} s, no timeout)", urlString,
							waitedSec);
				} else {
					long remainingSec = Math.max(0, (deadline - now) / 1000);
					log.info("Waiting for Numista bot challenge to clear: {} ({} s elapsed, ~{} s left)",
							urlString, waitedSec, remainingSec);
				}
				nextLogAt = now + 5_000;
			}
			try {
				page.waitForTimeout(1_000);
			} catch (PlaywrightException e) {
				return;
			}
		}
	}

	// private static List<Cookie> toPlaywrightCookies(String rawCookieHeader,
	// String urlString) {
	// if (rawCookieHeader == null || rawCookieHeader.isBlank()) {
	// return List.of();
	// }
	// String cookieDomain = cookieDomainForUrl(urlString);
	// if (cookieDomain == null) {
	// return List.of();
	// }

	// List<Cookie> cookies = new ArrayList<>();
	// Arrays.stream(rawCookieHeader.split(";"))
	// .map(String::trim)
	// .filter(part -> !part.isEmpty() && part.contains("="))
	// .forEach(part -> {
	// String[] kv = part.split("=", 2);
	// String name = kv[0].trim();
	// String value = kv.length > 1 ? kv[1] : "";
	// if (name.isEmpty()) {
	// return;
	// }
	// Cookie cookie = new Cookie(name, value)
	// .setDomain(cookieDomain)
	// .setPath("/");
	// cookies.add(cookie);
	// });
	// return cookies;
	// }

	// /**
	// * {@code .numista.com} so cookies apply to {@code en.numista.com} and
	// * subdomains.
	// */
	// private static String cookieDomainForUrl(String urlString) {
	// try {
	// String host = URI.create(urlString).getHost();
	// if (host == null || host.isBlank()) {
	// return null;
	// }
	// if (host.endsWith("numista.com")) {
	// return ".numista.com";
	// }
	// return host.startsWith(".") ? host : "." + host;
	// } catch (RuntimeException e) {
	// return null;
	// }
	// }

	// /**
	// * Reuses one persistent Chromium profile so {@code cf_clearance} survives
	// * across requests.
	// */
	// private static final class NumistaPlaywrightSession {

	// private static final Object LOCK = new Object();
	// private static final AtomicLong LAST_WARMUP_MS = new AtomicLong(0);
	// private static final long WARMUP_INTERVAL_MS = 30 * 60 * 1000L;

	// private static Playwright playwright;
	// private static BrowserContext context;
	// private static String appliedCookieHeader = "";

	// static {
	// Runtime.getRuntime()
	// .addShutdownHook(new Thread(NumistaPlaywrightSession::close,
	// "numista-playwright-shutdown"));
	// }

	// static BrowserContext getContext() {
	// synchronized (LOCK) {
	// if (isContextAlive(context)) {
	// return context;
	// }
	// if (context != null) {
	// close();
	// }
	// initContext();
	// return context;
	// }
	// }

	// /**
	// * {@link BrowserContext#browser()} is null for {@code
	// launchPersistentContext};
	// * probe lightly instead.
	// */
	// private static boolean isContextAlive(BrowserContext ctx) {
	// if (ctx == null) {
	// return false;
	// }
	// try {
	// Browser browser = ctx.browser();
	// if (browser != null) {
	// return browser.isConnected();
	// }
	// ctx.pages();
	// return true;
	// } catch (PlaywrightException | NullPointerException e) {
	// return false;
	// }
	// }

	// static void applyCookies(BrowserContext ctx, String cookieHeader, String
	// urlString) {
	// if (cookieHeader == null || cookieHeader.isBlank()) {
	// return;
	// }
	// synchronized (LOCK) {
	// if (cookieHeader.equals(appliedCookieHeader)) {
	// return;
	// }
	// List<Cookie> cookies = toPlaywrightCookies(cookieHeader, urlString);
	// if (!cookies.isEmpty()) {
	// ctx.addCookies(cookies);
	// appliedCookieHeader = cookieHeader;
	// }
	// }
	// }

	// static void markWarmedUp() {
	// LAST_WARMUP_MS.set(System.currentTimeMillis());
	// }

	// private static void initContext() {
	// close();
	// try {
	// Files.createDirectories(PLAYWRIGHT_PROFILE_DIR);
	// } catch (IOException e) {
	// throw new IllegalStateException("Failed to init Playwright profile
	// directory", e);
	// }

	// playwright = Playwright.create();
	// context = playwright.chromium().launchPersistentContext(
	// PLAYWRIGHT_PROFILE_DIR,
	// new BrowserType.LaunchPersistentContextOptions()
	// .setHeadless(PLAYWRIGHT_HEADLESS)
	// .setJavaScriptEnabled(true)
	// .setUserAgent(
	// "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML,
	// like Gecko) Version/26.4 Safari/605.1.15")
	// .setArgs(List.of(
	// "--disable-blink-features=AutomationControlled")));

	// Map<String, String> headers = new HashMap<>();
	// headers.put("Accept",
	// "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
	// headers.put("Accept-Language", "en-GB,en;q=0.9");
	// context.setExtraHTTPHeaders(headers);
	// appliedCookieHeader = "";

	// long sinceWarmup = System.currentTimeMillis() - LAST_WARMUP_MS.get();
	// if (sinceWarmup > WARMUP_INTERVAL_MS) {
	// Page warmup = context.newPage();
	// try {
	// navigate(warmup, NUMISTA_HOME_URL);
	// waitPastBotChallenge(warmup, NUMISTA_HOME_URL);
	// markWarmedUp();
	// } finally {
	// warmup.close();
	// }
	// }

	// log.info("Numista Playwright session started (headless={}, profile={})",
	// PLAYWRIGHT_HEADLESS, PLAYWRIGHT_PROFILE_DIR);
	// }

	// private static void close() {
	// synchronized (LOCK) {
	// if (context != null) {
	// try {
	// context.close();
	// } catch (PlaywrightException ignored) {
	// }
	// context = null;
	// }
	// if (playwright != null) {
	// try {
	// playwright.close();
	// } catch (PlaywrightException ignored) {
	// }
	// playwright = null;
	// }
	// appliedCookieHeader = "";
	// }
	// }
	// }

	public static String getAttribute(Element element, String key) {
		if (element != null && !element.attributes().get(key).isEmpty()) {
			return element.attributes().get(key);
		}
		return null;
	}

	public static Map<String, String> getAttributeWithTextSingleOption(NumistaPage numistaPage, String searchQuery,
			String key) {
		Element element = numistaPage.page.selectFirst(searchQuery);

		if (element == null) {
			log.info("Can't find " + searchQuery + " on the page");
			return null;
		}

		Element option = element.select("option").stream()
				.filter(o -> o.hasAttr("selected"))
				.findFirst()
				.orElse(null);

		if (option == null) {
			numistaPage.getPipelineStepLogger().trace("Can't find selected <option> in " + searchQuery + " on the page");
			return null;
		}

		if (option.text().isEmpty()) {
			numistaPage.getPipelineStepLogger().debugOrange("The " + searchQuery + " name is empty on the page");
			return null;
		}

		if (option.attributes().get(key).isEmpty()) {
			numistaPage.getPipelineStepLogger().debugOrange("The " + searchQuery + " " + key + " is empty on the page");
			return null;
		}

		return Map.of(key, option.attributes().get(key), "text", option.text());
	}

	/**
	 * Like {@link #getAttributeWithTextSingleOption} but prefers the
	 * {@code selected} option (current value on the
	 * coin page). Falls back to the first option if none is marked selected.
	 */
	public static Map<String, String> getAttributeWithTextSelectedOrFirstOption(Document page, String searchQuery,
			String key, NumistaPage numistaPage) {
		Element element = page.selectFirst(searchQuery);

		if (element == null) {
			numistaPage.getPipelineStepLogger().debugOrange("Can't find " + searchQuery + " on the page");
			return null;
		}

		Element option = element.select("option").stream()
				.filter(o -> o.attributes().hasKey("selected"))
				.findFirst()
				.orElse(null);
		if (option == null) {
			option = element.selectFirst("option");
		}

		if (option == null) {
			numistaPage.getPipelineStepLogger().trace("Can't find <option> tag in " + searchQuery + " on the page");
			return null;
		}

		if (option.text().isEmpty()) {
			numistaPage.getPipelineStepLogger().debugOrange("The " + searchQuery + " name is empty on the page");
			return null;
		}

		if (option.attributes().get(key).isEmpty()) {
			numistaPage.getPipelineStepLogger().debugOrange("The " + searchQuery + " " + key + " is empty on the page");
			return null;
		}

		return Map.of(key, option.attributes().get(key), "text", option.text());
	}

	public static HashMap<String, String> getAttributeWithTextSelectedOption(Object source, String searchQuery) {
		Element element = null;
		if (source instanceof Document) {
			element = ((Document) source).selectFirst(searchQuery);
		} else if (source instanceof Element) {
			element = ((Element) source).selectFirst(searchQuery);
		}

		if (element == null)
			return null;

		return element.select("option").stream().filter(option -> option.attributes().hasKey("selected")).findFirst()
				.map(option -> {
					HashMap<String, String> r = new HashMap<>();
					r.put("value", option.attributes().get("value"));
					r.put("text", option.text());
					return r;
				}).orElse(null);

	}

	public static List<HashMap<String, String>> getAttributesWithTextSelectedOptions(Element element) {
		if (element != null) {
			return element.select("option").stream().filter(option -> option.attributes().hasKey("selected"))
					.map(option -> {
						HashMap<String, String> hashMap = new HashMap<>();
						hashMap.put("value", option.attributes().get("value"));
						hashMap.put("text", option.text());
						return hashMap;
					}).collect(Collectors.toList());
		}
		return List.of();
	}

	public static String getTagText(Element element) {
		if (element != null && !element.text().isEmpty()) {
			return element.text();
		}
		return null;
	}

	// /**
	// * Fetches content from the given URL and parses it as a JSON object.
	// *
	// * @param urlString The URL to fetch JSON data from.
	// * @param useCookies Whether to include the configured Numista cookie and
	// * USER_AGENT
	// * (useful for numista.com APIs).
	// * @return A JsonObject if parsing is successful, otherwise null.
	// */
	// public static <T> T fetchAndParseJson(String urlString, boolean useCookies,
	// Class<T> clazz) {
	// try {
	// URL url = URI.create(urlString).toURL();
	// HttpURLConnection con = (HttpURLConnection) url.openConnection();

	// con.setRequestMethod("GET");
	// con.setRequestProperty("Accept", "application/json"); // Indicate we expect
	// JSON

	// if (useCookies && configuredUseCookies && !configuredNumistaCookie.isEmpty())
	// {
	// con.setRequestProperty("User-Agent", USER_AGENT);
	// con.setRequestProperty("Cookie", configuredNumistaCookie); // Use with
	// caution if the JSON source is not
	// // numista
	// }

	// int responseCode = con.getResponseCode();
	// if (responseCode >= 200 && responseCode < 300) { // Check for successful
	// response
	// BufferedReader in = new BufferedReader(new
	// InputStreamReader(con.getInputStream()));
	// String inputLine;
	// StringBuilder responseContent = new StringBuilder();
	// while ((inputLine = in.readLine()) != null) {
	// responseContent.append(inputLine);
	// }
	// in.close();

	// // Parse the JSON string
	// ObjectMapper objectMapper = new ObjectMapper();
	// return objectMapper.readValue(responseContent.toString(), clazz);

	// } else {
	// log.error("HTTP GET request failed with response code: " + responseCode + "
	// for URL: " + urlString);
	// // Log error response body if any
	// try (BufferedReader errorStream = new BufferedReader(new
	// InputStreamReader(con.getErrorStream()))) {
	// String errorLine;
	// StringBuilder errorResponse = new StringBuilder();
	// while ((errorLine = errorStream.readLine()) != null) {
	// errorResponse.append(errorLine);
	// }
	// System.err.println("Error response: " + errorResponse.toString());
	// } catch (Exception e) {
	// // Ignore if error stream cannot be read
	// }
	// return null;
	// }
	// } catch (IOException e) {
	// log.error("IOException during fetching/parsing JSON from URL: " + urlString +
	// " - " + e.getMessage());
	// e.printStackTrace();
	// return null;
	// }
	// }

	// /**
	// * Fetches content from the given URL and parses it as a JSON object.
	// *
	// * @param urlString The URL to fetch JSON data from.
	// * @param useCookies Whether to include the configured Numista cookie and
	// * USER_AGENT
	// * (useful for numista.com APIs).
	// * @return A JsonObject if parsing is successful, otherwise null.
	// */
	// public static String fetchJson(String urlString, boolean useCookies) {
	// try {
	// URL url = URI.create(urlString).toURL();
	// HttpURLConnection con = (HttpURLConnection) url.openConnection();

	// con.setRequestMethod("GET");
	// con.setRequestProperty("Accept", "application/json"); // Indicate we expect
	// JSON

	// if (useCookies && configuredUseCookies && !configuredNumistaCookie.isEmpty())
	// {
	// con.setRequestProperty("User-Agent", USER_AGENT);
	// con.setRequestProperty("Cookie", configuredNumistaCookie); // Use with
	// caution if the JSON source is not
	// // numista
	// }

	// int responseCode = con.getResponseCode();
	// if (responseCode >= 200 && responseCode < 300) { // Check for successful
	// response
	// BufferedReader in = new BufferedReader(new
	// InputStreamReader(con.getInputStream()));
	// String inputLine;
	// StringBuilder responseContent = new StringBuilder();
	// while ((inputLine = in.readLine()) != null) {
	// responseContent.append(inputLine);
	// }
	// in.close();

	// return responseContent.toString();
	// } else {
	// log.error("HTTP GET request failed with response code: " + responseCode + "
	// for URL: " + urlString);
	// // Log error response body if any
	// try (BufferedReader errorStream = new BufferedReader(new
	// InputStreamReader(con.getErrorStream()))) {
	// String errorLine;
	// StringBuilder errorResponse = new StringBuilder();
	// while ((errorLine = errorStream.readLine()) != null) {
	// errorResponse.append(errorLine);
	// }
	// System.err.println("Error response: " + errorResponse.toString());
	// } catch (Exception e) {
	// // Ignore if error stream cannot be read
	// }
	// return null;
	// }
	// } catch (IOException e) {
	// log.error("IOException during fetching/parsing JSON from URL: " + urlString +
	// " - " + e.getMessage());
	// e.printStackTrace();
	// return null;
	// }
	// }

	/**
	 * Parse string {@code fullName} to find year periods.
	 * Example: (1887-1918), (1887), (1990-date)
	 *
	 * @return Pair with left = "fromYears UUIDs" and right = "tillYears UUIDs"
	 */
	public Pair<List<Year>, List<Year>> parseYearPeriods(String fullName) {

		Pair<List<Year>, List<Year>> result = MutablePair.of(new ArrayList<>(), new ArrayList<>());

		Pattern pattern = Pattern.compile("[(]\\S+[)]");
		Matcher matcher = pattern.matcher(fullName);

		while (matcher.find()) {
			String periodStr = matcher.group(0);
			Year yearFrom = null;
			Year yearTill = null;

			String[] years = periodStr.replace("(", "").replace(")", "").split("-");

			// Years can be (1887-1918), (1936), (1990-date)
			// After splitting by "-", we can get array of 2 strings or 1 string

			if (years.length == 0 || years.length > 2) {
				log.error("Can't parse PHP request (years for = {} with length != 1 or 2).", fullName);
				return null;
			} else if (years.length == 1) { // we have a period during one year, example "(1936)"
				if (StringUtils.isNumeric(years[0])) {

					Mono<Year> yearFromMono = yearService
							.findGregorianYearByValue(Integer.parseInt(years[0]));

					yearFrom = yearFromMono.block();

					yearTill = yearFrom;
				} else { // Try to catch another variants for ruler's period with one year which is not
							// numeric
					log.error("Can't parse PHP request (period for = {} with one year which is not Numeric)",
							fullName);
					continue;
				}
			} else { // Ruler's Period has two years (1887-1918) or (1990-date)

				if (StringUtils.isNumeric(years[0])) { // Now I only know that the start year is only number

					Mono<Year> yearFromMono = yearService
							.findGregorianYearByValue(Integer.parseInt(years[0]));

					yearFrom = yearFromMono.block();

				} else { // Try to catch another variants for ruler's period with two year which start
							// year is not numeric
					log.error("Can't parse PHP request (start year = {} is not Numeric).", fullName);
					return null;
				}

				if (years[1].equals("date")) { // End year can be Numeric or "date". The "date" means that the ruling is
												// not finished.
					yearTill = null;
				} else if (StringUtils.isNumeric(years[1])) {

					Mono<Year> yearTillMono = yearService
							.findGregorianYearByValue(Integer.parseInt(years[1]));

					yearTill = yearTillMono.block();

				} else { // Try to catch another variants for ruler's period with two year which end year
							// is not numeric and not "date"
					log.error("Can't parse PHP request (end year = {} is not Numeric and not 'date').", fullName);
					return null;
				}
			}

			if (!result.getLeft().contains(yearFrom)) {
				result.getLeft().add(yearFrom);
			}

			if (yearTill != null && !result.getRight().contains(yearTill)) {
				result.getRight().add(yearTill);
			}
		}

		return result;

	}

	public static List<String> getTextsSelectedOptions(Element element) {
		if (element != null) {
			return element.select("option").stream().filter(option -> option.attributes().hasKey("selected"))
					.map(Element::text).collect(Collectors.toList());
		}
		return null;
	}

}
