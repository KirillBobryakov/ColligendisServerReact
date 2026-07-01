package com.colligendis.server.util;

/**
 * Accent-insensitive issuer name matching for catalogue search.
 */
public final class IssuerSearchMatcher {

	private IssuerSearchMatcher() {
	}

	public static String normalizeQuery(String query) {
		if (query == null) {
			return "";
		}
		return UnicodeNormalizer.normalize(query.trim());
	}

	public static boolean matches(String query, String name, String storedNormalizedName) {
		final String normalizedQuery = normalizeQuery(query);
		if (normalizedQuery.isBlank()) {
			return false;
		}
		if (storedNormalizedName != null
				&& !storedNormalizedName.isBlank()
				&& storedNormalizedName.contains(normalizedQuery)) {
			return true;
		}
		return UnicodeNormalizer.normalize(name).contains(normalizedQuery);
	}
}
