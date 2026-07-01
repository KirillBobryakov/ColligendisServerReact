package com.colligendis.server.util;

/**
 * Shared catalogue filter for typed denomination numeric values (e.g. {@code 10} matches
 * {@code 10 Kroner} and {@code 10 Øre}).
 */
public final class DenominationNumericFilter {

	private DenominationNumericFilter() {
	}

	public static String cypherClause() {
		return """
				AND (coalesce($denominationNid, "") = "" OR denomination.nid = $denominationNid)
				AND (
				  $denominationNumericValue IS NULL
				  OR denomination.numericValue = $denominationNumericValue
				  OR (
				    coalesce($denominationNumericText, "") <> ""
				    AND (
				      toLower(coalesce(denomination.name, '')) STARTS WITH toLower($denominationNumericText) + ' '
				      OR toLower(coalesce(denomination.name, '')) = toLower($denominationNumericText)
				      OR toLower(coalesce(denomination.fullName, '')) STARTS WITH toLower($denominationNumericText) + ' '
				      OR toLower(coalesce(denomination.fullName, '')) STARTS WITH toLower($denominationNumericText) + '('
				    )
				  )
				)
				""";
	}

	public static String numericText(Double value) {
		if (value == null) {
			return "";
		}
		if (Double.isFinite(value) && value == Math.rint(value)) {
			return String.valueOf((long) value.doubleValue());
		}
		return value.toString();
	}
}
