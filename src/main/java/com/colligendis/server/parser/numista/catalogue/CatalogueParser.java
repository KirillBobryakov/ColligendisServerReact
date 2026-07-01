package com.colligendis.server.parser.numista.catalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.util.web.WebPageClient;
import com.colligendis.server.util.web.WebPageLoadException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogueParser {

	/**
	 * https://en.numista.com/catalogue/index.php?e=ddr&st=147-150&cat=y&s=n&q=200&p=2
	 */

	private static final String CATALOGUE_URL_PREFIX = "https://en.numista.com/catalogue/index.php?e=";

	private final WebPageClient webPageClient;

	public CatalogueParseResult parse(
			String issuerNumistaCode,
			String collectableTypeCode,
			boolean withNids,
			ColligendisUser user) {
		String firstPage = CATALOGUE_URL_PREFIX + issuerNumistaCode + "&st=" + collectableTypeCode
				+ "&cat=y&s=n&q=200&p=1";

		Document document = loadDocument(firstPage, user);
		if (document == null) {
			return new CatalogueParseResult(issuerNumistaCode, 0, new ArrayList<>());
		}

		// 1. Check "no results" case
		Element noResult = document.selectFirst("p:matchesOwn(No result has been found)");

		if (noResult != null) {
			return new CatalogueParseResult(issuerNumistaCode, 0, new ArrayList<>());
		}

		Element nav = document.selectFirst("div.catalogue_navigation");
		String text;
		if (nav != null) {
			text = nav.ownText();
		} else {
			Element body = document.body();
			text = body != null ? body.text() : "";
		}

		// extract the number
		Pattern pattern = Pattern.compile("([\\d\\s\\u00A0\\u202F]+)\\s+results found\\.");
		Matcher matcher = pattern.matcher(text);

		int count = 0;
		if (matcher.find()) {
			String rawCount = matcher.group(1);
			String normalizedCount = rawCount.replaceAll("\\D", "");
			if (!normalizedCount.isEmpty()) {
				count = Integer.parseInt(normalizedCount);
			}
		}

		if (!withNids) {
			return new CatalogueParseResult(issuerNumistaCode, count, new ArrayList<>());
		}

		List<String> nids = new ArrayList<>();

		nids.addAll(getNidsFromPage(document));

		if (count > 200) {
			for (int page = 1; page <= count / 200; page++) {
				String url = CATALOGUE_URL_PREFIX + issuerNumistaCode + "&st=" + collectableTypeCode
						+ "&cat=y&s=n&q=200&p=" + (page + 1);
				Document nextPageDocument = loadDocument(url, user);
				nids.addAll(getNidsFromPage(nextPageDocument));
			}
		}

		return new CatalogueParseResult(issuerNumistaCode, count, nids);
	}

	private Document loadDocument(String url, ColligendisUser user) {
		try {
			return webPageClient.loadPageDocument(url, resolveCookie(user)).block();
		} catch (WebPageLoadException e) {
			log.warn("Failed to load catalogue page {}: {}", url, e.getMessage());
			return null;
		}
	}

	private static String resolveCookie(ColligendisUser user) {
		if (user != null && StringUtils.hasText(user.getNumistaCookie())) {
			return user.getNumistaCookie().strip();
		}
		return "";
	}

	private List<String> getNidsFromPage(Document document) {
		if (document == null) {
			return List.of();
		}

		Elements results = document.select("div.resultat_recherche");

		List<String> nids = new ArrayList<>();

		for (Element result : results) {
			Element link = result.selectFirst("div.description_piece strong a[href]");

			if (link != null) {
				String href = link.attr("href").replace("/", "");

				nids.add(href);
			}
		}

		return nids;
	}

	public record CatalogueParseResult(String issuerNumistaCode, int nTypesCount, List<String> nids) {
	}

}
