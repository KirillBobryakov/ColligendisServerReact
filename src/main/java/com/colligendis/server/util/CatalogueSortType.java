package com.colligendis.server.util;

import java.util.Locale;

public final class CatalogueSortType {

	public enum Sort {
		COUNTRY,
		ISSUER,
		DENOMINATION;

		public static Sort fromParam(String value) {
			if (value == null || value.isBlank()) {
				return COUNTRY;
			}
			return switch (value.trim().toLowerCase(Locale.ROOT)) {
				case "issuer" -> ISSUER;
				case "denomination" -> DENOMINATION;
				default -> COUNTRY;
			};
		}
	}

	private CatalogueSortType() {
	}

	public static String orderByClause(Sort sort) {
		return switch (sort) {
			case COUNTRY ->
					"ORDER BY toLower(coalesce(countryNumistaCode, '')), toLower(coalesce(issuerNumistaCode, '')), coalesce(denominationNumericValue, 0), n.nid\n";
			case ISSUER ->
					"ORDER BY toLower(coalesce(issuerNumistaCode, '')), coalesce(denominationNumericValue, 0), n.nid\n";
			case DENOMINATION -> "ORDER BY coalesce(denominationNumericValue, 0), n.nid\n";
		};
	}
}
