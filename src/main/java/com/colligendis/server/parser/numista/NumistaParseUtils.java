package com.colligendis.server.parser.numista;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.common.service.YearService;
import com.colligendis.server.logger.LogExecutionTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class NumistaParseUtils {

	private final YearService yearService;

	protected static Boolean useCookies = true;
	protected static String COOKIE = "_pk_ses.10.0242=1; __eoi=ID=b5cb8c17bfeff269:T=1762711520:RT=1775381833:S=AA-AfjZeOF6g0IJhKpux_LcmJMen; __gads=ID=832995cdcb1024f1:T=1762711520:RT=1775381833:S=ALNI_MaK7izygkkOAagl3LnfgtMYv4u-Hw; __gpi=UID=000012884dc93d7f:T=1762711520:RT=1775381833:S=ALNI_MbwSQATS4iyXHYDCQJzCyCoKj4KqQ; hb_insticator_uid=8772cf40-1e4f-4830-a325-6b5ee8930de8; challenge_pass=MTAzLjI0NS4yMjkuMjI1fDE3NzU0NjgxNjN8OWE3YTVhNDM2MTQ5MjEyMzc4MDcwMDlhZDU3OGM4MTk2ZTc3NDIxNjAwYjFkMjcwZTQ2NGFmNDkwNjYwZTJjOQ%3D%3D; _pk_ref.10.0242=%5B%22%22%2C%22%22%2C1775381642%2C%22https%3A%2F%2Fwww.google.com%2F%22%5D; cto_bidid=kM6Bjl94M1NCNnBZViUyRlJEJTJGOVpRUmlCdE01Qnc3bE1aWSUyQkVza2l6QjNUVlNjSEVjU09OVHNJanBUeiUyQjQ1dG5zQ0FjMk43YkpYOXdxVGFHbks1NnQweXVXRHNqeXM1d1lBQ3ZUdGlZblVwa20zcGpnJTNE; cto_bundle=isPHal9VcUJ0c1hqNiUyQjQ5aUZRTlBrMlFKdU1vT3oxTzFOMHJ3bWd4WFF3N0dxcE5KeDhTVUxBeW1tZTcyZ21tUzN0U0hOVWFTdmdEeE9VOUlRRnpFbHFKNmFBYyUyRk9reiUyQjFJZXVtJTJCVWpiNUZTNzA2RkRrb2xWSnp1MCUyRnZObkxvUzNjTVNTJTJGQzROWnglMkJlekRlellPRER4dzFsSmYxaDRDWnVSaUlja1M2N1hMcUtGcyUzRA; IABGPP_HDR_GppString=DBABMA~CQhhRkAQhhRkAAKA4AENCXFsAP_gAEPgAAqIMMtR_G__bWlr-bb3abtkeYxP9_hr7sQxBgbJk24FzLPW7JwHx2E5NAzatqIKmRIAu3TBIQNlHJDURUCgKIgFryDMaE2U4TNKJ6BkiFMZA2tYCFxvm4tjWQCY4vr_5lc1mB-t7dr82dzyy6hHn3a5fmS1UJCdIYetDfv8ZBOT-9IEd-x8v4v4_EbpEm-eS1n_pGtp4jd6YnM_dBmxt-Tyff7Pn__rl_e7X_ve_n3zv8oXH77r____f_-7___2b_-___b-__4MNAAmGhUQRlkQIBAoCEECABQVhABQIAgAASBogIATBgQ5AwAXWEyAEAKAAYIAQAAgwABAAAJAAhEAEABAIAQIBAoAAwAIAgIAGBgADABYiAQAAgOgYpgQQCBYAJEZVBpgSgAJBAS2VCCUDAgrhCkWOAQQIiYKAAAEAAoAAEB8LAQklBKxIIAuILoAACAAAKIESBFIWYAgqDNFoKwJOAyNMAyfMEySnQZAEwQkZBkQmqCQeKYohQQ5AbFLMAdPEFACLtZIQ8AA.fwAAAAAAAAAA; addtl_consent=1~20.23.3.9.2.4.9.13.6.4.15.9.5.2.11.8.1.3.2.10.2.23.8.4.15.17.2.6.3.16.4.7.6.14.5.20.2.1.6.2.1.4.31.9.3.1.14.22.8.9.5.1.6.9.24.17.5.3.1.27.1.17.10.10.8.6.2.8.3.4.30.102.14.60.1.4.1.17.7.12.25.35.5.18.9.7.17.4.20.2.4.1.17.24.4.2.7.6.1.1.3.2.14.25.3.2.2.8.2.17.9.8.6.3.10.4.20.2.4.13.10.5.6.1.3.22.16.2.6.8.6.11.6.5.17.13.3.11.9.10.28.12.1.3.2.2.17.9.6.40.17.2.2.9.15.8.7.3.12.7.2.4.1.14.5.13.22.13.2.6.1.7.10.1.4.15.2.4.9.4.4.1.4.7.3.10.5.3.12.17.4.14.8.2.15.2.5.6.2.3.2.14.11.8.2.2.7.9.13.6.11.1.13.2.14.4.1.1.3.1.1.9.7.2.16.5.19.8.3.1.5.3.5.4.8.4.1.3.2.10.4.2.13.4.2.6.9.6.3.2.2.3.1.6.9.10.11.9.19.8.3.3.1.2.1.1.1.2.7.17.2.18.4.4.3.13.4.10.1.2.4.6.3.3.3.4.1.7.11.4.1.11.3.3.1.10.13.3.2.1.1.3.1.3.1.1.2.7.2.13.7.6.8.4.3.4.5.7.2.2.5.5.3.5.4.7.9.1.4.1.2.1.7.10.7.4.1.3.1.1.2.1.3.2.2.4.1.5.6.1.8.1.3.1.1.2.2.4.3.3.3.1.1.4.3.6.1.2.1.4.1.1.4.1.1.2.1.8.1.7.4.3.2.1.2.1.5.3.15.1.15.10.28.1.2.2.6.6.3.4.1.6.3.4.7.1.1.2.1.4.1.2.3.3.1.1.1.1.4.1.5.2.3.1.2.2.6.2.1.2.2.2.3.2.1.3.2.1.1.1.1.2.1.1.1.2.2.1.1.2.1.2.1.7.1.7.1.1.2.2.1.4.2.1.1.9.1.6.2.1.6.2.3.2.1.1.1.2.2.2.1.1.1.4.1.1.2.2.1.1.7.1.2.2.1.1.1.1.2.3.1.1.2.4.1.1.1.4.5.3.3.4.5.8.1.1.2.3.1.4.3.2.2.3.1.1.1.1.11.1.1.3.1.1.2.2.1.6.1.2.3.5.2.7.1.1.2.3.2.1.1.8.4.1.1.2.1.1.8.2.2.2.3.1.4.5.1.1.1.1.1.1.1.1.4.2.4.1.8.1.1.2.1.1.2.1.4.1.2.1.1.1.2.1.2.1.1.1.1.1.2.4.1.5.1.2.1.3.3.6.4.2.9.5.2.1.1.2.1.3.3.1.6.1.2.5.1.1.2.5.1.4.2.1.200.100.100.100.300.200.200.100.100.100.400.1700.200.104.596.100.1000.800.500.400.200.200.500.100.1800.201.99.303.99.104.95.1399.1100.100.4302.498.1300.2100.800.100.600.200.900.100.200.301.399.100.800.700.201.200.1799.1400.300.400.100.2100.2300.400.1101.499.400.2100.100.100.2100.1100.201.299.600.1100.101.99.1400.2000.1400.2600.100.200.100.300; euconsent-v2=CQhhRkAQhhRkAAKA4AENCXFsAP_gAEPgAAqIMMtR_G__bWlr-bb3abtkeYxP9_hr7sQxBgbJk24FzLPW7JwHx2E5NAzatqIKmRIAu3TBIQNlHJDURUCgKIgFryDMaE2U4TNKJ6BkiFMZA2tYCFxvm4tjWQCY4vr_5lc1mB-t7dr82dzyy6hHn3a5fmS1UJCdIYetDfv8ZBOT-9IEd-x8v4v4_EbpEm-eS1n_pGtp4jd6YnM_dBmxt-Tyff7Pn__rl_e7X_ve_n3zv8oXH77r____f_-7___2b_-___b-__4MNAAmGhUQRlkQIBAoCEECABQVhABQIAgAASBogIATBgQ5AwAXWEyAEAKAAYIAQAAgwABAAAJAAhEAEABAIAQIBAoAAwAIAgIAGBgADABYiAQAAgOgYpgQQCBYAJEZVBpgSgAJBAS2VCCUDAgrhCkWOAQQIiYKAAAEAAoAAEB8LAQklBKxIIAuILoAACAAAKIESBFIWYAgqDNFoKwJOAyNMAyfMEySnQZAEwQkZBkQmqCQeKYohQQ5AbFLMAdPEFACLtZIQ8AA.IMNNR_G__bXlv-bb36btkeYxf9_hr7sQxBgbJs24FzLvW7JwH32E7NEzatqYKmRIAu3TBIQNtHJjURUChKIgVrzDsaE2U4TtKJ-BkiHMZY2tYCFxvm4tjWQCZ4vr_51d9mT-t7dr-2dzy27hnv3a9fuS1UJidKYetHfv8ZBOT-_IU9_x-_4v4_MbpEm-eS1v_tWtt43d64vP_dpuxt-Tyff7____73_e7X__e__33_-qXX_77____________f_________8.fwAAAAAAAAAA; usprivacy=1---; PHPSESSID=jc44uqi0u66117avugursn2b1n; cf_hmac=243029-1775340105-QVrXbIJ4PdnVpfz59oFN6qkmkcq4aCVLKpK1mV134aE; _sharedID=ca6bb764-6890-4fdf-9d94-222e1e66266c; _sharedID_cst=ayxgLOcsPA%3D%3D; __mggpc__=0; _ublock=1; search_order=y; __ai_fp_uuid=f4f9ff7b862be841%3A1; __upin=6ikmALdCWVhE5CWjmElk0g; saisie_rapide=c; carte=type; search_subtypes=all; pieces_par_page=200; issuer_sort=d; cf_clearance=KNlKQpcinFgvCqybJxRu3gJ6CwQF86ExLIGFv.HIHtk-1759610496-1.2.1.1-ZIkhKIJyRywXBhOw8kg8YUklv.wmZiHx.NCsNBy.RhXJC4s_gd8XTFhE5ZJgq9j_VHIOAHAHV.P2ZcH7aCzHJ_MHMGc5x27wzP65r_ZMatDXheLQIUX6jm9sY3ruestVsloIgyXdFydDCS22iAz1lllue7W2Q_uy44vDxXavB03D_pGTzeGkhhl9eyIvL6MgVzaXtivBkTA5sqSoDiIs6GKa3lnn5lmdfne8gDyq.rc; access_token=P%3EbLPZEY%24%21t.%3F9jIWgGg%28sG%5DrBF1S%28b%21%231.x7g%21%3E; pseudo=kbobryakov; _pk_id.10.0242=a36509097c0e55fd.1751222019.";
	public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15";

	private static final Object PLAYWRIGHT_LOCK = new Object();
	private static volatile Playwright playwrightRef;
	private static volatile Browser playwrightBrowser;

	@LogExecutionTime
	public static Document loadPageByURL(String urlString) {
		synchronized (PLAYWRIGHT_LOCK) {
			try {
				if (playwrightBrowser == null) {
					Playwright created = Playwright.create();
					try {
						playwrightBrowser = created.chromium().launch(
								new BrowserType.LaunchOptions().setHeadless(true));
						playwrightRef = created;
					} catch (RuntimeException e) {
						created.close();
						throw e;
					}
					Runtime.getRuntime().addShutdownHook(new Thread(() -> {
						synchronized (PLAYWRIGHT_LOCK) {
							if (playwrightBrowser != null) {
								playwrightBrowser.close();
								playwrightBrowser = null;
							}
							if (playwrightRef != null) {
								playwrightRef.close();
								playwrightRef = null;
							}
						}
					}));
				}

				Map<String, String> headers = new HashMap<>();
				headers.put("Accept",
						"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
				headers.put("Accept-Language", "en-US,en;q=0.9");
				headers.put("User-Agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
								+ "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
				if (useCookies) {
					headers.put("Cookie", COOKIE);
				}

				try (BrowserContext context = playwrightBrowser.newContext(
						new Browser.NewContextOptions().setExtraHTTPHeaders(headers))) {
					Page page = context.newPage();
					Response response = page.navigate(urlString);
					if (response != null && response.status() == 404) {
						return null;
					}
					return Jsoup.parse(page.content());
				}
			} catch (PlaywrightException e) {
				log.error("Error loading page by URL: {}", urlString, e);
				return null;
			}
		}
	}

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

		Element option = element.selectFirst("option");

		if (option == null) {
			numistaPage.getPipelineStepLogger().trace("Can't find <option> tag in " + searchQuery + " on the page");
			return null;
		}

		if (option.text().isEmpty()) {
			log.info("The " + searchQuery + " name is empty on the page");
			return null;
		}

		if (option.attributes().get(key).isEmpty()) {
			log.info("The " + searchQuery + " " + key + " is empty on the page");
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
			log.info("Can't find " + searchQuery + " on the page");
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
			log.info("The " + searchQuery + " name is empty on the page");
			return null;
		}

		if (option.attributes().get(key).isEmpty()) {
			log.info("The " + searchQuery + " " + key + " is empty on the page");
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
					.findFirst().map(option -> {
						HashMap<String, String> hashMap = new HashMap<>();
						hashMap.put("value", option.attributes().get("value"));
						hashMap.put("text", option.text());
						return hashMap;
					}).stream().collect(Collectors.toList());
		}
		return null;
	}

	public static String getTagText(Element element) {
		if (element != null && !element.text().isEmpty()) {
			return element.text();
		}
		return null;
	}

	/**
	 * Fetches content from the given URL and parses it as a JSON object.
	 *
	 * @param urlString  The URL to fetch JSON data from.
	 * @param useCookies Whether to include the predefined COOKIE and USER_AGENT
	 *                   (useful for numista.com APIs).
	 * @return A JsonObject if parsing is successful, otherwise null.
	 */
	public static <T> T fetchAndParseJson(String urlString, boolean useCookies, Class<T> clazz) {
		try {
			URL url = URI.create(urlString).toURL();
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json"); // Indicate we expect JSON

			if (useCookies) {
				con.setRequestProperty("User-Agent", USER_AGENT);
				con.setRequestProperty("Cookie", COOKIE); // Use with caution if the JSON source is not numista
			}

			int responseCode = con.getResponseCode();
			if (responseCode >= 200 && responseCode < 300) { // Check for successful response
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				StringBuilder responseContent = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					responseContent.append(inputLine);
				}
				in.close();

				// Parse the JSON string
				ObjectMapper objectMapper = new ObjectMapper();
				return objectMapper.readValue(responseContent.toString(), clazz);

			} else {
				log.error("HTTP GET request failed with response code: " + responseCode + " for URL: " + urlString);
				// Log error response body if any
				try (BufferedReader errorStream = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
					String errorLine;
					StringBuilder errorResponse = new StringBuilder();
					while ((errorLine = errorStream.readLine()) != null) {
						errorResponse.append(errorLine);
					}
					System.err.println("Error response: " + errorResponse.toString());
				} catch (Exception e) {
					// Ignore if error stream cannot be read
				}
				return null;
			}
		} catch (IOException e) {
			log.error("IOException during fetching/parsing JSON from URL: " + urlString + " - " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Fetches content from the given URL and parses it as a JSON object.
	 *
	 * @param urlString  The URL to fetch JSON data from.
	 * @param useCookies Whether to include the predefined COOKIE and USER_AGENT
	 *                   (useful for numista.com APIs).
	 * @return A JsonObject if parsing is successful, otherwise null.
	 */
	public static String fetchJson(String urlString, boolean useCookies) {
		try {
			URL url = URI.create(urlString).toURL();
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json"); // Indicate we expect JSON

			if (useCookies) {
				con.setRequestProperty("User-Agent", USER_AGENT);
				con.setRequestProperty("Cookie", COOKIE); // Use with caution if the JSON source is not numista
			}

			int responseCode = con.getResponseCode();
			if (responseCode >= 200 && responseCode < 300) { // Check for successful response
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				StringBuilder responseContent = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					responseContent.append(inputLine);
				}
				in.close();

				return responseContent.toString();
			} else {
				log.error("HTTP GET request failed with response code: " + responseCode + " for URL: " + urlString);
				// Log error response body if any
				try (BufferedReader errorStream = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
					String errorLine;
					StringBuilder errorResponse = new StringBuilder();
					while ((errorLine = errorStream.readLine()) != null) {
						errorResponse.append(errorLine);
					}
					System.err.println("Error response: " + errorResponse.toString());
				} catch (Exception e) {
					// Ignore if error stream cannot be read
				}
				return null;
			}
		} catch (IOException e) {
			log.error("IOException during fetching/parsing JSON from URL: " + urlString + " - " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

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
