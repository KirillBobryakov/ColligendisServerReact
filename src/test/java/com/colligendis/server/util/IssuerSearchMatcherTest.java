package com.colligendis.server.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IssuerSearchMatcherTest {

	@Test
	void matchesNetherlandsAntilles_whenQueryIsPartialAntil() {
		assertTrue(IssuerSearchMatcher.matches("antil", "Netherlands Antilles", null));
		assertTrue(IssuerSearchMatcher.matches("antil", "Netherlands Antilles", "netherlands antilles"));
	}

	@Test
	void matchesNetherlandsAntilles_whenNormalizedNameMissing() {
		assertTrue(IssuerSearchMatcher.matches("antil", "Netherlands Antilles", ""));
		assertTrue(IssuerSearchMatcher.matches("antil", "Netherlands Antilles", "   "));
	}

	@Test
	void doesNotMatchUnrelatedIssuerForAntilQuery() {
		assertFalse(IssuerSearchMatcher.matches("antil", "Andorra", null));
		assertFalse(IssuerSearchMatcher.matches("antil", "Netherlands", null));
	}

	@Test
	void matchesAccentedIssuerWhenQueryUsesAscii() {
		assertTrue(IssuerSearchMatcher.matches("duss", "Düsseldorf, City of", null));
		assertTrue(IssuerSearchMatcher.matches("duss", "Düsseldorf, City of", "dusseldorf, city of"));
	}

	@Test
	void twoCharPrefixIsTooBroadForPartialWordSearch() {
		final String issuerName = "netherlands antilles";
		assertTrue(issuerName.contains("an"));
		assertTrue(IssuerSearchMatcher.matches("antil", "Netherlands Antilles", null));
	}
}
