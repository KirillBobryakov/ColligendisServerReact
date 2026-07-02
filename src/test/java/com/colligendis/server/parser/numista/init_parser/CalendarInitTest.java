package com.colligendis.server.parser.numista.init_parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.colligendis.server.database.common.model.Calendar;

class CalendarInitTest {

	@Test
	void parseCalendars_mapsCodesNamesAndShifts() throws Exception {
		String optionsHtml = """
				<select>
				<option value="gregorien">Gregorian/Julian calendar</option>
				<option value="persan">Persian calendar (Solar Hijri)</option>
				<option value="inconnu">Unknown</option>
				</select>
				""";
		Map<String, Integer> shifts = new java.util.LinkedHashMap<>();
		shifts.put("gregorien", 0);
		shifts.put("persan", 621);
		shifts.put("inconnu", null);

		List<Calendar> calendars = CalendarInit.parseCalendars(optionsHtml, shifts);

		assertEquals(3, calendars.size());
		assertEquals("gregorien", calendars.get(0).getCode());
		assertEquals("Gregorian/Julian calendar", calendars.get(0).getName());
		assertEquals(0, calendars.get(0).getToGregorianShift());
		assertEquals("persan", calendars.get(1).getCode());
		assertEquals(621, calendars.get(1).getToGregorianShift());
		assertEquals("inconnu", calendars.get(2).getCode());
		assertNull(calendars.get(2).getToGregorianShift());
	}

	@Test
	void parseCalendars_fullResourceSetCoversAllOptions() throws Exception {
		String optionsHtml = new ClassPathResource("numista/calendar-options.html")
				.getContentAsString(StandardCharsets.UTF_8);
		Map<String, Integer> shifts = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
				new ClassPathResource("numista/calendar-shifts.json").getInputStream(),
				new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Integer>>() {
				});

		List<Calendar> calendars = CalendarInit.parseCalendars(optionsHtml, shifts);

		assertEquals(190, calendars.size());
		assertTrue(calendars.stream().anyMatch(c -> "seleucid".equals(c.getCode()) && Integer.valueOf(-311).equals(c.getToGregorianShift())));
		assertTrue(calendars.stream().anyMatch(c -> "persan".equals(c.getCode()) && Integer.valueOf(621).equals(c.getToGregorianShift())));
	}
}
