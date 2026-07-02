package com.colligendis.server.parser.numista.init_parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.common.service.CalendarService;
import com.colligendis.server.logger.BaseLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarInit {

	private static final String OPTIONS_RESOURCE = "numista/calendar-options.html";
	private static final String SHIFTS_RESOURCE = "numista/calendar-shifts.json";

	private static final ObjectMapper JSON = new ObjectMapper();

	private final CalendarService calendarService;

	private final ColligendisUserService colligendisUserService;

	private final BaseLogger calendarInitLogger = new BaseLogger();

	/**
	 * Loads Numista calendar options and shifts from classpath resources, then
	 * persists each {@link Calendar} (code, name, toGregorianShift).
	 */
	public void saveAllFromEmbeddedOptions() {
		try {
			String optionsHtml = readClasspathResource(OPTIONS_RESOURCE);
			Map<String, Integer> shifts = loadShifts();
			List<Calendar> calendars = parseCalendars(optionsHtml, shifts);
			persistCalendars(calendars);
		} catch (IOException e) {
			log.error("Calendar init failed while loading resources", e);
		}
	}

	private void persistCalendars(List<Calendar> calendars) {
		final int total = calendars.size();
		var savedCount = new java.util.concurrent.atomic.AtomicInteger(0);

		Flux.fromIterable(calendars)
				.flatMap(calendar -> calendarService
						.findByCodeOrCreate(calendar.getCode(), calendar.getName(), calendar.getToGregorianShift(),
								colligendisUserService.getNumistaParserUserMono(), calendarInitLogger)
						.doOnSuccess(r -> {
							int current = savedCount.incrementAndGet();
							System.out.printf("Saved calendar %d/%d: %s (code=%s, shift=%s)%n",
									current, total, calendar.getName(), calendar.getCode(),
									calendar.getToGregorianShift());
						})
						.doOnError(err -> System.err.printf("Error saving calendar %s: %s%n",
								calendar.getCode(), err.getMessage()))
						.onErrorResume(err -> Mono.empty()),
						10)
				.doOnComplete(() -> log.info("Calendar init finished ({} calendars).", total))
				.doOnError(err -> log.error("Calendar init failed", err))
				.subscribe();
	}

	static List<Calendar> parseCalendars(String htmlWrappedSelect, Map<String, Integer> shifts) {
		Document document = Jsoup.parse(htmlWrappedSelect);
		List<Calendar> out = new ArrayList<>();
		Set<String> seenCodes = new LinkedHashSet<>();
		for (Element option : document.select("option")) {
			String code = option.attr("value").trim();
			String name = option.text().trim();
			if (code.isEmpty() || name.isEmpty() || !seenCodes.add(code)) {
				continue;
			}
			if (!shifts.containsKey(code)) {
				throw new IllegalArgumentException("Missing toGregorianShift for calendar code: " + code);
			}
			out.add(new Calendar(code, name, shifts.get(code)));
		}
		return out;
	}

	private static Map<String, Integer> loadShifts() throws IOException {
		try (InputStream input = new ClassPathResource(SHIFTS_RESOURCE).getInputStream()) {
			return JSON.readValue(input, new TypeReference<LinkedHashMap<String, Integer>>() {
			});
		}
	}

	private static String readClasspathResource(String path) throws IOException {
		try (InputStream input = new ClassPathResource(path).getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
