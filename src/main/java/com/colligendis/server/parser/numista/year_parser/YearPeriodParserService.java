package com.colligendis.server.parser.numista.year_parser;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.common.service.CalendarService;
import com.colligendis.server.database.common.service.YearService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class YearPeriodParserService {
	private static final Pattern PERIOD_PATTERN = Pattern.compile("\\(([^()]+)\\)");
	private static final Pattern YEAR_CONTENT = Pattern.compile("\\d{3,4}|date", Pattern.CASE_INSENSITIVE);
	private static final Pattern YEAR_ENTRY_SEPARATOR = Pattern.compile("[,;]");
	private static final String MONTH = "(?:Jan(?:uary)?\\.?|Feb(?:ruary)?\\.?|Mar(?:ch)?\\.?|Apr(?:il)?\\.?|May\\.?|Jun(?:e)?\\.?|Jul(?:y)?\\.?|Aug(?:ust)?\\.?|Sep(?:t(?:ember)?)?\\.?|Oct(?:ober)?\\.?|Nov(?:ember)?\\.?|Dec(?:ember)?\\.?)";
	private static final String DAY = "\\d{1,2}(?:st|nd|rd|th)?";
	private static final String DATE = MONTH + "\\s+" + DAY;
	private static final String MONTH_RANGE = MONTH + "(?:-" + MONTH + ")?";
	private static final String DATE_RANGE = DATE + "(?:\\s*-\\s*" + DATE + ")?";
	private static final Pattern DATE_PREFIX = Pattern.compile(
			"^(?:(?:" + DATE_RANGE + "|" + MONTH_RANGE + ")\\s*)+",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern YEAR_TOKEN = Pattern.compile("\\d{3,4}");

	private final YearService yearService;
	private final ColligendisUserService colligendisUserService;

	public Mono<CirculationPeriods> parsePeriods(String fullName) {
		Matcher matcher = PERIOD_PATTERN.matcher(fullName);

		List<String> raw = matcher.results()
				.map(m -> m.group(1)) // content only, inside (...)
				.filter(content -> YEAR_CONTENT.matcher(content).find())
				.toList();

		if (raw.isEmpty()) {
			return Mono.just(CirculationPeriods.empty());
		}

		// For each element in raw, split by "," or ";" and trim; flatten into newRaw
		List<String> newRaw = raw.stream()
				.flatMap(s -> YEAR_ENTRY_SEPARATOR.splitAsStream(s))
				.map(String::trim)
				.filter(str -> !str.isEmpty())
				.toList();

		return Flux.fromIterable(newRaw)
				.flatMap(str -> parseSingle(str))
				.collectList()
				.map(CirculationPeriods::new);
	}

	private Mono<CirculationPeriod> parseSingle(String inside) {
		// Case 1: "(notgeld, 1914-1924)"
		final String kind;
		final String yearsPart;

		if (inside.contains(",")) {
			String[] split = inside.split(",", 2);
			kind = split[0].trim();
			yearsPart = split[1].trim();
		} else if (inside.contains(";")) {
			String[] split = inside.split(";", 2);
			kind = null;
			if (split.length > 1) {
				if (split[0].contains("renamed")) {
					yearsPart = split[1].trim();
				} else if (split[1].contains("renamed")) {
					yearsPart = split[0].trim();
				} else {
					yearsPart = inside;
				}
			} else {
				yearsPart = inside;
			}
		} else {
			kind = null;
			yearsPart = inside;
		}

		String normalizedYearsPart = stripMonthPrefixes(yearsPart);

		// Case 2: "1990-date", "1936", "1887-1918"
		String[] parts = normalizedYearsPart.split("-");

		if (parts.length == 1) {
			return parseSingleYear(parts[0])
					.map(y -> new CirculationPeriod(Optional.of(y), Optional.of(y), kind));
		}

		if (parts.length == 2) {
			if ("date".equalsIgnoreCase(parts[1].strip()) || isYearRange(parts[0], parts[1])) {
				return parseDoubleYear(parts[0], parts[1], kind);
			}
			return parseSingleYear(normalizedYearsPart)
					.map(y -> new CirculationPeriod(Optional.of(y), Optional.of(y), kind));
		}

		return parseSingleYear(normalizedYearsPart)
				.map(y -> new CirculationPeriod(Optional.of(y), Optional.of(y), kind));
	}

	private static boolean isYearRange(String fromPart, String tillPart) {
		Optional<Integer> fromYear = parseYearToken(fromPart);
		Optional<Integer> tillYear = parseYearToken(tillPart);
		return fromYear.isPresent() && tillYear.isPresent()
				&& fromYear.get() >= 1000 && tillYear.get() >= 1000;
	}

	private Mono<Year> parseSingleYear(String yearStr) {
		return Mono.justOrEmpty(parseYearToken(yearStr))
				.flatMap(year -> yearService.findYearByValueWithCreate(year, CalendarService.GREGORIAN,
						colligendisUserService.getNumistaParserUserMono()));
	}

	static String stripMonthPrefixes(String text) {
		if (text == null || text.isBlank()) {
			return text == null ? "" : text.strip();
		}
		return DATE_PREFIX.matcher(text.strip()).replaceFirst("").strip();
	}

	private static Optional<Integer> parseYearToken(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		String trimmed = token.strip();
		if (StringUtils.isNumeric(trimmed)) {
			return Optional.of(Integer.parseInt(trimmed));
		}
		Matcher matcher = YEAR_TOKEN.matcher(trimmed);
		if (matcher.find()) {
			return Optional.of(Integer.parseInt(matcher.group()));
		}
		log.error("Year not numeric: {}", token);
		return Optional.empty();
	}

	private Mono<CirculationPeriod> parseDoubleYear(
			String fromStr, String tillStr,
			String kind) {

		Mono<Optional<Year>> fromMono = parseYearValue(fromStr).map(Optional::of);

		Mono<Optional<Year>> tillMono = "date".equalsIgnoreCase(tillStr)
				? Mono.just(Optional.empty())
				: parseYearValue(tillStr).map(Optional::of);

		return fromMono.flatMap(from -> tillMono.map(till -> new CirculationPeriod(from, till, kind)));
	}

	private Mono<Year> parseYearValue(String str) {
		return Mono.justOrEmpty(parseYearToken(str))
				.switchIfEmpty(Mono.error(new IllegalStateException("Year not numeric: " + str)))
				.flatMap(year -> yearService.findYearByValueWithCreate(year, CalendarService.GREGORIAN,
						colligendisUserService.getNumistaParserUserMono()));
	}
}
