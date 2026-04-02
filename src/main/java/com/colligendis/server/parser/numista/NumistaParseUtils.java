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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class NumistaParseUtils {

	private final YearService yearService;

	protected static Boolean useCookies = true;
	protected static String COOKIE = "_pk_ses.10.0242=1; _pk_ref.10.0242=%5B%22%22%2C%22%22%2C1762191420%2C%22https%3A%2F%2Fwww.google.com%2F%22%5D; test_cookies=1; PHPSESSID=4tlrb75op7meo0jj50331demhf; _sharedID=ca6bb764-6890-4fdf-9d94-222e1e66266c; _sharedID_cst=2SzgLJUseQ%3D%3D; carte=piece; saisie_rapide=n; search_subtypes=all; pieces_par_page=200; issuer_sort=d; cf_clearance=KNlKQpcinFgvCqybJxRu3gJ6CwQF86ExLIGFv.HIHtk-1759610496-1.2.1.1-ZIkhKIJyRywXBhOw8kg8YUklv.wmZiHx.NCsNBy.RhXJC4s_gd8XTFhE5ZJgq9j_VHIOAHAHV.P2ZcH7aCzHJ_MHMGc5x27wzP65r_ZMatDXheLQIUX6jm9sY3ruestVsloIgyXdFydDCS22iAz1lllue7W2Q_uy44vDxXavB03D_pGTzeGkhhl9eyIvL6MgVzaXtivBkTA5sqSoDiIs6GKa3lnn5lmdfne8gDyq.rc; access_token=P%3EbLPZEY%24%21t.%3F9jIWgGg%28sG%5DrBF1S%28b%21%231.x7g%21%3E; pseudo=kbobryakov; _pk_id.10.0242=a36509097c0e55fd.1751222019.; search_order=v; tb=y; tc=y; tn=y; tp=y; tt=y;";
	public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15";

	@LogExecutionTime
	public static Document loadPageByURL(String urlString) {
		Document document;
		try {
			URL url = URI.create(urlString).toURL();
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json"); // Indicate we expect
			// JSON

			if (useCookies) {
				con.setRequestProperty("User-Agent", USER_AGENT);
				con.setRequestProperty("Cookie", COOKIE); // Use with caution if the JSON source is not numista
			}

			int responseCode = con.getResponseCode();

			if (responseCode == 404)
				return null;

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuilder response = new StringBuilder();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
				response.append(System.lineSeparator());
			}

			in.close();

			// Send the request to the server
			document = Jsoup.parse(response.toString());

		} catch (IOException e) {
			log.error("Error loading page by URL: {}", urlString, e);
			return null;
		}
		return document;
	}

	public static String getAttribute(Element element, String key) {
		if (element != null && !element.attributes().get(key).isEmpty()) {
			return element.attributes().get(key);
		}
		return null;
	}

	public static Map<String, String> getAttributeWithTextSingleOption(Document page, String searchQuery, String key) {
		Element element = page.selectFirst(searchQuery);

		if (element == null) {
			log.info("Can't find " + searchQuery + " on the page");
			return null;
		}

		Element option = element.selectFirst("option");

		if (option == null) {
			log.info("Can't find <option> tag in " + searchQuery + " on the page");
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
			String key) {
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
			log.info("Can't find <option> tag in " + searchQuery + " on the page");
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
}
