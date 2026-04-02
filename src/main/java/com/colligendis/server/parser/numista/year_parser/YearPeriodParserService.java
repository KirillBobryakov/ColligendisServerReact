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

	private final YearService yearService;
	private final ColligendisUserService colligendisUserService;

	public Mono<CirculationPeriods> parsePeriods(String fullName) {
		Matcher matcher = PERIOD_PATTERN.matcher(fullName);

		List<String> raw = matcher.results()
				.map(m -> m.group(1)) // content only, inside (...)
				.toList();

		if (raw.isEmpty()) {
			return Mono.just(CirculationPeriods.empty());
		}

		// For each element in raw, split by "," and trim; flatten into newRaw
		List<String> newRaw = raw.stream()
				.flatMap(s -> List.of(s.split(",")).stream())
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
		} else {
			kind = null;
			yearsPart = inside;
		}

		// Case 2: "1990-date", "1936", "1887-1918"
		String[] parts = yearsPart.split("-");

		if (parts.length == 1) {
			return parseSingleYear(parts[0])
					.map(y -> new CirculationPeriod(Optional.of(y), Optional.of(y), kind));
		}

		if (parts.length == 2) {
			return parseDoubleYear(parts[0], parts[1], kind);
		}

		return Mono.error(new IllegalStateException("Invalid year format: " + inside));
	}

	private Mono<Year> parseSingleYear(String yearStr) {
		if (!StringUtils.isNumeric(yearStr)) {
			log.error("Year not numeric: {}", yearStr);
			return Mono.empty();
		}

		int year = Integer.parseInt(yearStr);

		return yearService.findGregorianYearByValue(year);
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
		if (!StringUtils.isNumeric(str)) {
			return Mono.error(new IllegalStateException("Year not numeric: " + str));
		}

		int year = Integer.parseInt(str);

		return yearService.findYearByValueWithCreate(year, CalendarService.GREGORIAN,
				colligendisUserService.getNumistaParserUserMono());
	}
}
