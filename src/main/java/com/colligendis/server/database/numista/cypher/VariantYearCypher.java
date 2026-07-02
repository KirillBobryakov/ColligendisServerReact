package com.colligendis.server.database.numista.cypher;

/**
 * Cypher fragments for resolving variant year fields from graph relationships.
 */
public final class VariantYearCypher {

	private VariantYearCypher() {
	}

	public static final String DATE_GREGORIAN_YEAR = """
			head([
			  (v)-[:DATED_AT]->(dy:YEAR)
			  | CASE
			      WHEN head([(dy)-[:TO_NUMBER_IN]->(c:CALENDAR) WHERE c.code = 'gregorien' | c]) IS NOT NULL
			        THEN dy.dateYear
			      WHEN head([(dy)-[:MATCH_UP_TO_GREGORIAN]->(gy:YEAR) | gy.dateYear]) IS NOT NULL
			        THEN head([(dy)-[:MATCH_UP_TO_GREGORIAN]->(gy:YEAR) | gy.dateYear])
			      WHEN head([(dy)-[:TO_NUMBER_IN]->(c:CALENDAR) | c.toGregorianShift]) IS NOT NULL
			        THEN dy.dateYear + head([(dy)-[:TO_NUMBER_IN]->(c:CALENDAR) | c.toGregorianShift])
			      ELSE NULL
			    END
			])""";

	public static final String DATE_YEAR = "head([(v)-[:DATED_AT]->(dy:YEAR) | dy.dateYear])";

	public static final String MATCH_UP_TO_GREGORIAN_YEAR = """
			head([(v)-[:DATED_AT]->(dy:YEAR)-[:MATCH_UP_TO_GREGORIAN]->(gy:YEAR) | gy.dateYear])""";

	public static final String CALENDAR = """
			coalesce(
			  head([(v)-[:WITH_CALENDAR]->(c:CALENDAR) | { code: c.code, name: c.name }]),
			  head([(v)-[:DATED_AT]->(dy:YEAR)-[:TO_NUMBER_IN]->(c:CALENDAR) | { code: c.code, name: c.name }])
			)""";

	public static final String FROM_GREGORIAN_YEAR = "head([(v)-[:DATED_FROM]->(fy:YEAR) | fy.dateYear])";

	public static final String TILL_GREGORIAN_YEAR = "head([(v)-[:DATED_TILL]->(ty:YEAR) | ty.dateYear])";

	public static String forVariantAlias(String expression, String variantAlias) {
		return expression.replace("(v)", "(" + variantAlias + ")");
	}

	public static String dateGregorianYearFor(String variantAlias) {
		return forVariantAlias(DATE_GREGORIAN_YEAR, variantAlias);
	}

	public static String dateYearFor(String variantAlias) {
		return forVariantAlias(DATE_YEAR, variantAlias);
	}

	public static String matchUpToGregorianYearFor(String variantAlias) {
		return forVariantAlias(MATCH_UP_TO_GREGORIAN_YEAR, variantAlias);
	}

	public static String calendarFor(String variantAlias) {
		return forVariantAlias(CALENDAR, variantAlias);
	}

	public static String fromGregorianYearFor(String variantAlias) {
		return forVariantAlias(FROM_GREGORIAN_YEAR, variantAlias);
	}

	public static String tillGregorianYearFor(String variantAlias) {
		return forVariantAlias(TILL_GREGORIAN_YEAR, variantAlias);
	}

	public static String issuerSearchYearFilterClause(String variantAlias) {
		String dateGregorian = dateGregorianYearFor(variantAlias);
		String fromGregorian = fromGregorianYearFor(variantAlias);
		String tillGregorian = tillGregorianYearFor(variantAlias);
		return """
				AND (
				  ($startYear IS NULL AND $endYear IS NULL)
				  OR EXISTS {
				    MATCH (n)-[:HAS_VARIANT]->(%s:VARIANT)
				    WHERE coalesce(%s.deletedOnNumista, false) = false
				    AND (
				      (%s IS NOT NULL
				        AND ($startYear IS NULL OR %s >= $startYear)
				        AND ($endYear IS NULL OR %s <= $endYear))
				      OR
				      (%s IS NOT NULL
				        AND ($endYear IS NULL OR %s <= $endYear)
				        AND ($startYear IS NULL OR %s IS NULL OR %s >= $startYear))
				    )
				  }
				)
				""".formatted(variantAlias, variantAlias,
				dateGregorian, dateGregorian, dateGregorian,
				fromGregorian, fromGregorian, tillGregorian, tillGregorian);
	}

}
