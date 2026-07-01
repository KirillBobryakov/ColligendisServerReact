package com.colligendis.server.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NormalizedNeo4jPropertyUtilTest {

	@Test
	void sourcePropertyKeyForNormalized_mapsStandardSuffixes() {
		assertEquals("name", NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized("normalizedName"));
		assertEquals("fullName", NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized("normalizedFullName"));
		assertEquals("title", NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized("normalizedTitle"));
		assertEquals("lettering", NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized("normalizedLettering"));
		assertEquals("comment", NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized("normalizedComment"));
	}

	@Test
	void sourcePropertyKeyForNormalized_invalid() {
		assertNull(NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized(null));
		assertNull(NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized("name"));
		assertNull(NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized("normalized"));
	}

	@Test
	void normalizedPropertyKeyForSource_inverseOfSourcePropertyKeyForNormalized() {
		assertEquals("normalizedName", NormalizedNeo4jPropertyUtil.normalizedPropertyKeyForSource("name"));
		assertEquals("normalizedFullName", NormalizedNeo4jPropertyUtil.normalizedPropertyKeyForSource("fullName"));
		assertNull(NormalizedNeo4jPropertyUtil.normalizedPropertyKeyForSource(null));
		assertNull(NormalizedNeo4jPropertyUtil.normalizedPropertyKeyForSource(""));
	}

	@Test
	void roundtrip_sourceAndNormalizedKeys() {
		String[] sources = { "name", "title", "fullName", "lettering", "comment" };
		for (String s : sources) {
			String n = NormalizedNeo4jPropertyUtil.normalizedPropertyKeyForSource(s);
			assertEquals(s, NormalizedNeo4jPropertyUtil.sourcePropertyKeyForNormalized(n));
		}
	}

	@Test
	void normalizedSyncStats_summary() {
		var stats = new NormalizedNeo4jPropertyUtil.NormalizedSyncStats(100, 5, 7, 2);
		assertEquals(
				"Normalized sync complete: scanned=100, updated=5, propertiesWritten=7, propertiesRemoved=2",
				stats.summary());
	}

	@Test
	void syncNormalizedPropertiesForLabel_rejectsInvalidLabel() {
		assertThrows(IllegalArgumentException.class,
				() -> NormalizedNeo4jPropertyUtil.syncNormalizedPropertiesForLabel(null, "neo4j", "", 100));
		assertThrows(IllegalArgumentException.class,
				() -> NormalizedNeo4jPropertyUtil.syncNormalizedPropertiesForLabel(null, "neo4j", "bad label", 100));
		assertThrows(IllegalArgumentException.class,
				() -> NormalizedNeo4jPropertyUtil.syncNormalizedPropertiesForLabel(null, "neo4j", "issuer", 100));
	}
}
