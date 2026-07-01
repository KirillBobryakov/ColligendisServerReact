package com.colligendis.server.parser.numista.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NumistaCollectionSaveResponseParser {

	private static final Pattern QUANTITY_IN_Q = Pattern.compile("(\\d+)\\s*(?:&times;|×|x)", Pattern.CASE_INSENSITIVE);
	private static final Pattern COLLEC_MODAL_NEW_ARGS = Pattern.compile("collec_modal_new\\((.*)\\)\\s*;", Pattern.DOTALL);

	public NumistaCollectionSaveResponse parse(String html) {
		if (!StringUtils.hasText(html)) {
			return null;
		}

		Document doc = Jsoup.parseBodyFragment(html);
		Element row = findCollectionRow(doc);
		if (row == null) {
			doc = Jsoup.parse("<table><tbody>" + html + "</tbody></table>");
			row = findCollectionRow(doc);
		}
		if (row == null) {
			log.warn("Numista collection save response: no collection row found");
			return null;
		}

		NumistaCollectionSaveResponse.NumistaCollectionSaveResponseBuilder builder = NumistaCollectionSaveResponse
				.builder()
				.responseRowHtml(row.outerHtml());

		Element picturesSpan = row.selectFirst(".collec_pictures");
		if (picturesSpan != null) {
			builder.numistaCollectionItemId(picturesSpan.attr("data-thumb-id"));
			builder.numistaThumbUserId(picturesSpan.attr("data-thumb-user"));
			builder.pictures(splitPictures(picturesSpan.attr("data-thumb-pictures")));
		}

		Element quantitySpan = row.selectFirst(".collec_q");
		if (quantitySpan != null) {
			builder.quantityDisplay(quantitySpan.text().trim());
			Matcher qm = QUANTITY_IN_Q.matcher(quantitySpan.html());
			if (qm.find()) {
				builder.quantity(Integer.parseInt(qm.group(1)));
			}
			String coinClass = quantitySpan.classNames().stream()
					.filter(c -> c.startsWith("col"))
					.findFirst()
					.orElse(null);
			if (coinClass != null && coinClass.length() > 3) {
				builder.coinId(coinClass.substring(3));
			}
		}

		builder.displayGrade(extractDisplayGrade(row));
		builder.slabDisplay(textOf(row, ".collec_slab"));
		builder.measureDisplay(textOf(row, ".collec_measure"));
		builder.serialNumber(textOf(row, ".collec_serial"));
		builder.internalId(textOf(row, ".collec_internal"));
		builder.storageLocation(textOf(row, ".collec_storage"));
		builder.priceDisplay(textOf(row, ".collec_price"));
		builder.comment(textOf(row, ".collec_comment"));
		builder.swapComment(textOf(row, ".collec_swap_comment"));

		Element editButton = row.selectFirst(".collec_edit");
		if (editButton != null) {
			applyEditOnclick(builder, editButton.attr("onclick"));
		}

		return builder.build();
	}

	/**
	 * Parses {@code collec_modal_new(...)} from the edit button onclick.
	 * <p>
	 * Argument positions: collectible type code, {@link NumistaCollectionSaveResponse#getCoinId()
	 * NType nid}, {@link NumistaCollectionSaveResponse#getVersionId() variant nid},
	 * {@link NumistaCollectionSaveResponse#getNumistaCollectionItemId() item nid}, quantity, grade,
	 * forSwap, value, comment, swapComment, …
	 */
	private static void applyEditOnclick(
			NumistaCollectionSaveResponse.NumistaCollectionSaveResponseBuilder builder,
			String onclick) {
		if (!StringUtils.hasText(onclick)) {
			return;
		}

		List<String> args = parseCollecModalNewArguments(onclick);
		if (args.isEmpty()) {
			return;
		}

		if (args.size() > 1) {
			builder.coinId(parseNumericArg(args.get(1)));
		}
		if (args.size() > 2) {
			builder.versionId(parseNumericArg(args.get(2)));
		}
		if (args.size() > 3) {
			builder.numistaCollectionItemId(parseNumericArg(args.get(3)));
		}
		if (args.size() > 4) {
			builder.quantity(parseIntegerArg(args.get(4)));
		}
		if (args.size() > 5) {
			builder.gradeCode(parseNullableStringArg(args.get(5)));
		}
		if (args.size() > 6) {
			builder.forSwap(parseBooleanArg(args.get(6)));
		}
		if (args.size() > 7) {
			builder.value(parseNullableStringArg(args.get(7)));
		}
		if (args.size() > 8) {
			builder.comment(parseNullableStringArg(args.get(8)));
		}
		if (args.size() > 9) {
			builder.swapComment(parseNullableStringArg(args.get(9)));
		}
		if (args.size() > 11) {
			List<String> onclickPictures = parsePicturesArrayArg(args.get(11));
			if (!onclickPictures.isEmpty()) {
				builder.pictures(onclickPictures);
			}
		}
		if (args.size() > 12) {
			builder.gradingService(parseNullableStringArg(args.get(12)));
		}
		if (args.size() > 13) {
			builder.gradingMark(parseNullableStringArg(args.get(13)));
		}
		if (args.size() > 14) {
			String gradingJson = parseNullableStringArg(args.get(14));
			if (StringUtils.hasText(gradingJson)) {
				builder.gradingDesignationJson(unescapeHtml(gradingJson));
			}
		}
		if (args.size() > 15) {
			builder.slabNumber(parseNullableStringArg(args.get(15)));
		}
		if (args.size() > 16) {
			builder.cacSticker(parseNullableStringArg(args.get(16)));
		}
		if (args.size() > 17) {
			builder.storageLocation(parseNullableStringArg(args.get(17)));
		}
		if (args.size() > 18) {
			builder.acquisitionPlace(parseNullableStringArg(args.get(18)));
		}
		if (args.size() > 19) {
			builder.acquisitionDate(parseNullableStringArg(args.get(19)));
		}
		if (args.size() > 20) {
			builder.serialNumber(parseNullableStringArg(args.get(20)));
		}
		if (args.size() > 21) {
			builder.internalId(parseNullableStringArg(args.get(21)));
		}
		if (args.size() > 22) {
			builder.size(parseNullableStringArg(args.get(22)));
		}
	}

	static List<String> parseCollecModalNewArguments(String onclick) {
		Matcher matcher = COLLEC_MODAL_NEW_ARGS.matcher(onclick);
		if (!matcher.find()) {
			return List.of();
		}
		String argsBody = matcher.group(1).trim();
		if (argsBody.endsWith(",")) {
			argsBody = argsBody.substring(0, argsBody.length() - 1).trim();
		}
		return splitTopLevelComma(argsBody);
	}

	private static List<String> splitTopLevelComma(String args) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int parenDepth = 0;
		int bracketDepth = 0;
		int braceDepth = 0;
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;

		for (int i = 0; i < args.length(); i++) {
			char c = args.charAt(i);

			if (inSingleQuote) {
				current.append(c);
				if (c == '\'' && !isEscaped(args, i)) {
					inSingleQuote = false;
				}
				continue;
			}
			if (inDoubleQuote) {
				current.append(c);
				if (c == '"' && !isEscaped(args, i)) {
					inDoubleQuote = false;
				}
				continue;
			}

			switch (c) {
				case '\'' -> {
					inSingleQuote = true;
					current.append(c);
				}
				case '"' -> {
					inDoubleQuote = true;
					current.append(c);
				}
				case '(' -> {
					parenDepth++;
					current.append(c);
				}
				case ')' -> {
					parenDepth--;
					current.append(c);
				}
				case '[' -> {
					bracketDepth++;
					current.append(c);
				}
				case ']' -> {
					bracketDepth--;
					current.append(c);
				}
				case '{' -> {
					braceDepth++;
					current.append(c);
				}
				case '}' -> {
					braceDepth--;
					current.append(c);
				}
				case ',' -> {
					if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
						parts.add(current.toString().trim());
						current.setLength(0);
					} else {
						current.append(c);
					}
				}
				default -> current.append(c);
			}
		}

		if (current.length() > 0) {
			parts.add(current.toString().trim());
		}
		return parts;
	}

	private static boolean isEscaped(String text, int index) {
		return index > 0 && text.charAt(index - 1) == '\\';
	}

	private static String parseNumericArg(String raw) {
		String value = parseNullableStringArg(raw);
		return value != null ? value : raw.trim();
	}

	private static String parseNullableStringArg(String raw) {
		if (!StringUtils.hasText(raw) || "null".equalsIgnoreCase(raw.trim())) {
			return null;
		}
		String trimmed = raw.trim();
		if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
				|| (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
			String inner = trimmed.substring(1, trimmed.length() - 1);
			return unescapeHtml(inner);
		}
		return trimmed;
	}

	private static Integer parseIntegerArg(String raw) {
		String value = parseNullableStringArg(raw);
		if (!StringUtils.hasText(value)) {
			if (raw != null && raw.chars().allMatch(Character::isDigit)) {
				return Integer.parseInt(raw.trim());
			}
			return null;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Boolean parseBooleanArg(String raw) {
		if (!StringUtils.hasText(raw) || "null".equalsIgnoreCase(raw.trim())) {
			return null;
		}
		String trimmed = raw.trim();
		if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
			return false;
		}
		if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
			return true;
		}
		return null;
	}

	private static List<String> parsePicturesArrayArg(String raw) {
		if (!StringUtils.hasText(raw) || "null".equalsIgnoreCase(raw.trim())) {
			return List.of();
		}
		String trimmed = raw.trim();
		if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
			return List.of();
		}
		String inner = trimmed.substring(1, trimmed.length() - 1).trim();
		if (inner.isEmpty()) {
			return List.of();
		}
		return splitTopLevelComma(inner).stream()
				.map(NumistaCollectionSaveResponseParser::parseNullableStringArg)
				.filter(StringUtils::hasText)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private static Element findCollectionRow(Document doc) {
		Element edit = doc.selectFirst(".collec_edit");
		if (edit != null) {
			Element tr = edit.closest("tr");
			if (tr != null) {
				return tr;
			}
		}
		Element quantity = doc.selectFirst(".collec_q");
		if (quantity != null) {
			return quantity.closest("tr");
		}
		return doc.selectFirst("tr:has(.collec_edit), tr:has(.collec_q)");
	}

	private static String extractDisplayGrade(Element row) {
		Element flex = row.selectFirst("td div[style*=flex]");
		if (flex == null) {
			return null;
		}
		StringBuilder grade = new StringBuilder();
		for (Node child : flex.childNodes()) {
			if (child instanceof TextNode textNode) {
				String t = textNode.text().trim();
				if (StringUtils.hasText(t)) {
					if (grade.length() > 0) {
						grade.append(' ');
					}
					grade.append(t);
				}
			}
		}
		return grade.length() > 0 ? grade.toString() : null;
	}

	private static String textOf(Element row, String cssQuery) {
		Element el = row.selectFirst(cssQuery);
		return el != null ? el.text().trim() : null;
	}

	private static List<String> splitPictures(String raw) {
		if (!StringUtils.hasText(raw)) {
			return List.of();
		}
		return Arrays.stream(raw.trim().split("\\s+"))
				.filter(StringUtils::hasText)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private static String unescapeHtml(String value) {
		return value.replace("&quot;", "\"");
	}
}
