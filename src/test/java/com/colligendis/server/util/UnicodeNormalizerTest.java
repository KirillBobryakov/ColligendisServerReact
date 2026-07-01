package com.colligendis.server.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnicodeNormalizerTest {

	@Test
	void normalize_foldsUmlautsForIssuerSearch() {
		assertEquals("dusseldorf", UnicodeNormalizer.normalize("Düsseldorf"));
		assertEquals("duss", UnicodeNormalizer.normalize("duss"));
	}

	@Test
	void normalize_supportsAsciiQueryAgainstAccentedName() {
		assertTrue(UnicodeNormalizer.normalize("Düsseldorf, City of").contains("duss"));
	}

	@Test
	void normalize_supportsPartialWordSearchForNetherlandsAntilles() {
		assertTrue(UnicodeNormalizer.normalize("Netherlands Antilles").contains("antil"));
		assertTrue(UnicodeNormalizer.normalize("Netherlands Antilles").contains("antilles"));
	}
}
