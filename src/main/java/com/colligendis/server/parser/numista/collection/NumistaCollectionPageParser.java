package com.colligendis.server.parser.numista.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses Numista "My collection" issuer pages ({@code vos_pieces.php}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NumistaCollectionPageParser {

	private static final Pattern COLLEC_LINE_ID = Pattern.compile("^collec_line(\\d+)$");
	private static final Pattern PAGE_PARAM = Pattern.compile("[?&]page=(\\d+)");

	private final NumistaCollectionSaveResponseParser rowParser;

	public List<NumistaCollectionSaveResponse> parse(String html) {
		return parse(html, null);
	}

	public List<NumistaCollectionSaveResponse> parse(String html, String issuerNumistaCode) {
		if (!StringUtils.hasText(html)) {
			return List.of();
		}

		Document doc = Jsoup.parse(html);
		Element table = doc.selectFirst("table#vos_pieces");
		if (table == null) {
			log.warn("Numista collection page: table#vos_pieces not found (issuer={})", issuerNumistaCode);
			return List.of();
		}

		log.info(
				"Numista collection page: parsing table#vos_pieces (issuer={}, htmlLength={})",
				issuerNumistaCode,
				html.length());

		List<NumistaCollectionSaveResponse> items = new ArrayList<>();
		String currentCoinId = null;
		for (Element tbody : table.select("tbody")) {
			for (Element coinRow : tbody.select("tr[id^=t]")) {
				String rowId = coinRow.id();
				if (StringUtils.hasText(rowId) && rowId.length() > 1) {
					currentCoinId = rowId.substring(1);
				}
			}
			if (!tbody.classNames().contains("collec")) {
				continue;
			}
			NumistaCollectionSaveResponse parsed = parseCollectionTbody(tbody, currentCoinId, issuerNumistaCode);
			if (parsed != null) {
				items.add(parsed);
			}
		}

		log.info(
				"Numista collection page: parsed {} collection item(s) (issuer={})",
				items.size(),
				issuerNumistaCode);
		return items;
	}

	private NumistaCollectionSaveResponse parseCollectionTbody(
			Element tbody,
			String currentCoinId,
			String issuerNumistaCode) {
		String versionId = extractVersionIdFromTbodyId(tbody.id());
		Element row = tbody.selectFirst("tr");
		if (row == null) {
			return null;
		}

		NumistaCollectionSaveResponse parsed = rowParser.parse(row.outerHtml());
		if (parsed == null) {
			log.warn(
					"Numista collection page: could not parse tbody id={} (issuer={})",
					tbody.id(),
					issuerNumistaCode);
			return null;
		}

		if (!StringUtils.hasText(parsed.getVersionId()) && StringUtils.hasText(versionId)) {
			parsed.setVersionId(versionId);
		}
		if (!StringUtils.hasText(parsed.getCoinId()) && StringUtils.hasText(currentCoinId)) {
			parsed.setCoinId(currentCoinId);
		}
		if (!StringUtils.hasText(parsed.getNumistaCollectionItemId())) {
			log.warn(
					"Numista collection page: row without item id (coinId={}, versionId={}, issuer={})",
					parsed.getCoinId(),
					parsed.getVersionId(),
					issuerNumistaCode);
			return null;
		}

		log.info(
				"Numista collection item parsed: issuer={} tbodyId={} itemId={} coinId={} versionId={} qty={} grade={} displayGrade={} value={} forSwap={} comment={} swapComment={} storage={} acquisitionPlace={} price={}",
				issuerNumistaCode,
				tbody.id(),
				parsed.getNumistaCollectionItemId(),
				parsed.getCoinId(),
				parsed.getVersionId(),
				parsed.getQuantity(),
				parsed.getGradeCode(),
				parsed.getDisplayGrade(),
				parsed.getValue(),
				parsed.getForSwap(),
				parsed.getComment(),
				parsed.getSwapComment(),
				parsed.getStorageLocation(),
				parsed.getAcquisitionPlace(),
				parsed.getPriceDisplay());
		return parsed;
	}

	/**
	 * Extracts the maximum page number from the "Pages:" navigation block in a
	 * {@code vos_pieces.php} HTML page. Returns {@code 1} when no pagination links
	 * are found (single-page collection or parse failure).
	 */
	public int extractMaxPageCount(String html) {
		if (!StringUtils.hasText(html)) {
			return 1;
		}
		Document doc = Jsoup.parse(html);
		int maxPage = 1;
		for (Element a : doc.select("a[href*=page=]")) {
			String href = a.attr("href");
			Matcher m = PAGE_PARAM.matcher(href);
			if (m.find()) {
				try {
					int p = Integer.parseInt(m.group(1));
					if (p > maxPage) {
						maxPage = p;
					}
				} catch (NumberFormatException ignored) {
				}
			}
		}
		log.info("Numista collection page count extracted: {}", maxPage);
		return maxPage;
	}

	private static String extractVersionIdFromTbodyId(String tbodyId) {
		if (!StringUtils.hasText(tbodyId)) {
			return null;
		}
		Matcher matcher = COLLEC_LINE_ID.matcher(tbodyId.trim());
		return matcher.matches() ? matcher.group(1) : null;
	}
}
