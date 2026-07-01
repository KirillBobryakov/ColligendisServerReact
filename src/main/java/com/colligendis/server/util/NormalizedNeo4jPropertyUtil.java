package com.colligendis.server.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

/**
 * Scans Neo4j nodes that either carry a {@code normalized*} property or a known source field
 * ({@code name}, {@code title}, …), recomputes each normalized value via {@link UnicodeNormalizer},
 * and persists when values differ.
 * <p>
 * Naming rule: {@code normalizedName} ↔ {@code name}, {@code normalizedFullName} ↔ {@code fullName},
 * etc. (strip the {@code normalized} prefix and decapitalize the remainder for the source key).
 */
public final class NormalizedNeo4jPropertyUtil {

	public static final String NORMALIZED_PROPERTY_PREFIX = "normalized";

	/**
	 * Source property names that have a {@code normalized*} twin in domain models (extend when new
	 * pairs appear).
	 */
	public static final List<String> SOURCE_PROPERTIES_WITH_NORMALIZED_TWIN = List.of(
			"name", "title", "fullName", "lettering", "comment");

	private NormalizedNeo4jPropertyUtil() {
	}

	/**
	 * Maps a stored {@code normalized*} property key to the camelCase source property that feeds it.
	 *
	 * @return {@code null} if the key does not start with {@value #NORMALIZED_PROPERTY_PREFIX} or has no suffix
	 */
	public static String sourcePropertyKeyForNormalized(String normalizedPropertyKey) {
		if (normalizedPropertyKey == null || !normalizedPropertyKey.startsWith(NORMALIZED_PROPERTY_PREFIX)) {
			return null;
		}
		String tail = normalizedPropertyKey.substring(NORMALIZED_PROPERTY_PREFIX.length());
		if (tail.isEmpty()) {
			return null;
		}
		return Character.toLowerCase(tail.charAt(0)) + tail.substring(1);
	}

	/**
	 * Inverse of {@link #sourcePropertyKeyForNormalized}: {@code name} → {@code normalizedName}.
	 *
	 * @return {@code null} if {@code sourcePropertyKey} is null or empty
	 */
	public static String normalizedPropertyKeyForSource(String sourcePropertyKey) {
		if (sourcePropertyKey == null || sourcePropertyKey.isEmpty()) {
			return null;
		}
		return NORMALIZED_PROPERTY_PREFIX + Character.toUpperCase(sourcePropertyKey.charAt(0))
				+ sourcePropertyKey.substring(1);
	}

	/**
	 * Runs a read/write pass over the graph: nodes with {@code uuid}, not {@code *_DELETED} /
	 * {@code *_VERSIONED}, and with at least one {@code normalized*} key or a known source property
	 * from {@link #SOURCE_PROPERTIES_WITH_NORMALIZED_TWIN}.
	 *
	 * @param database Neo4j database name (same as {@code spring.neo4j.database})
	 * @param batchSize  number of nodes to load per scan batch ({@code >= 1})
	 */
	public static NormalizedSyncStats syncNormalizedProperties(Driver driver, String database, int batchSize) {
		return syncNormalizedProperties(driver, database, batchSize, null);
	}

	public static NormalizedSyncStats syncNormalizedProperties(Driver driver, String database, int batchSize,
			Consumer<String> progress) {
		return syncNormalizedProperties(driver, database, null, batchSize, progress);
	}

	/**
	 * Like {@link #syncNormalizedProperties(Driver, String, int)} but only for live nodes carrying
	 * the given Neo4j label (for example {@code ISSUER}, {@code NTYPE}).
	 *
	 * @param label primary node label; must match {@code [A-Z][A-Z0-9_]*} (same as domain {@code LABEL} constants)
	 */
	public static NormalizedSyncStats syncNormalizedPropertiesForLabel(Driver driver, String database, String label,
			int batchSize) {
		return syncNormalizedPropertiesForLabel(driver, database, label, batchSize, null);
	}

	public static NormalizedSyncStats syncNormalizedPropertiesForLabel(Driver driver, String database, String label,
			int batchSize, Consumer<String> progress) {
		Objects.requireNonNull(label, "label");
		if (label.isBlank()) {
			throw new IllegalArgumentException("label must not be blank");
		}
		validateNodeLabel(label);
		return syncNormalizedProperties(driver, database, label, batchSize, progress);
	}

	private static NormalizedSyncStats syncNormalizedProperties(Driver driver, String database, String labelOrNull,
			int batchSize, Consumer<String> progress) {
		Objects.requireNonNull(driver, "driver");
		int limit = Math.max(1, batchSize);
		SessionConfig config = SessionConfig.builder().withDatabase(Objects.requireNonNull(database, "database")).build();

		long nodesScanned = 0;
		long nodesUpdated = 0;
		long propertiesWritten = 0;
		long propertiesRemoved = 0;

		final String matchClause = labelOrNull == null
				? "MATCH (n)"
				: "MATCH (n:" + labelOrNull + ")";

		final String scope = labelOrNull == null ? "all labels" : labelOrNull;
		report(progress, "Normalized sync started [%s, database=%s, batchSize=%d]".formatted(scope, database, limit));

		try (Session session = driver.session(config)) {
			long skip = 0;
			while (true) {
				final long skipForQuery = skip;
				var readResult = session.executeRead(tx -> tx.run(
						"""
								%s
								WHERE n.uuid IS NOT NULL
								  AND NONE(l IN labels(n) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
								  AND (
								    any(k IN keys(n) WHERE k STARTS WITH $prefix)
								    OR any(k IN keys(n) WHERE k IN $sourceKeys)
								  )
								RETURN n.uuid AS uuid, properties(n) AS props
								ORDER BY n.uuid
								SKIP $skip
								LIMIT $limit
								""".formatted(matchClause),
						Values.parameters(
								"prefix", NORMALIZED_PROPERTY_PREFIX,
								"sourceKeys", SOURCE_PROPERTIES_WITH_NORMALIZED_TWIN,
								"skip", skipForQuery,
								"limit", limit)).list());

				if (readResult.isEmpty()) {
					break;
				}

				for (var record : readResult) {
					String uuid = record.get("uuid").asString(null);
					if (uuid == null || uuid.isBlank()) {
						continue;
					}
					Map<String, Object> props = record.get("props").asMap();
					nodesScanned++;

					Map<String, String> sets = new HashMap<>();
					List<String> removes = new ArrayList<>();
					collectNormalizedUpdates(props, sets, removes);

					if (sets.isEmpty() && removes.isEmpty()) {
						continue;
					}

					session.executeWrite(tx -> {
						if (!sets.isEmpty()) {
							tx.run("MATCH (n {uuid: $uuid}) SET n += $sets", Values.parameters("uuid", uuid, "sets", sets));
						}
						for (String prop : removes) {
							tx.run("MATCH (n {uuid: $uuid}) REMOVE n." + backtick(prop),
									Values.parameters("uuid", uuid));
						}
						return null;
					});

					nodesUpdated++;
					propertiesWritten += sets.size();
					propertiesRemoved += removes.size();
				}

				skip += readResult.size();
				report(progress,
						"  batch: +%d nodes (total scanned=%d, updated=%d, written=%d, removed=%d)".formatted(
								readResult.size(), nodesScanned, nodesUpdated, propertiesWritten, propertiesRemoved));
			}
		}

		NormalizedSyncStats stats = new NormalizedSyncStats(nodesScanned, nodesUpdated, propertiesWritten,
				propertiesRemoved);
		report(progress, stats.summary());
		return stats;
	}

	private static void report(Consumer<String> progress, String message) {
		if (progress != null) {
			progress.accept(message);
		}
	}

	/** Validates a Neo4j label before embedding it in Cypher ({@code MATCH (n:LABEL)}). */
	private static void validateNodeLabel(String label) {
		if (!label.matches("[A-Z][A-Z0-9_]*")) {
			throw new IllegalArgumentException("Invalid Neo4j label: " + label);
		}
	}

	private static void collectNormalizedUpdates(Map<String, Object> props, Map<String, String> sets,
			List<String> removes) {
		Set<String> normKeys = new LinkedHashSet<>();
		for (String k : props.keySet()) {
			if (k.startsWith(NORMALIZED_PROPERTY_PREFIX)) {
				normKeys.add(k);
			}
		}
		for (String sk : SOURCE_PROPERTIES_WITH_NORMALIZED_TWIN) {
			if (props.containsKey(sk)) {
				String nk = normalizedPropertyKeyForSource(sk);
				if (nk != null) {
					normKeys.add(nk);
				}
			}
		}
		for (String key : normKeys) {
			String sourceKey = sourcePropertyKeyForNormalized(key);
			if (sourceKey == null) {
				continue;
			}
			Object sourceObj = props.get(sourceKey);
			String sourceStr = sourceObj == null ? null : sourceObj.toString();
			String computed = UnicodeNormalizer.normalize(sourceStr);

			Object oldObj = props.get(key);
			String oldStr = oldObj == null ? null : oldObj.toString();

			if (Objects.equals(oldStr, computed)) {
				continue;
			}
			if (computed == null) {
				if (oldObj != null) {
					removes.add(key);
				}
			} else {
				sets.put(key, computed);
			}
		}
	}

	private static String backtick(String identifier) {
		return "`" + identifier.replace("`", "``") + "`";
	}

	public record NormalizedSyncStats(
			long nodesScanned,
			long nodesUpdated,
			long propertiesWritten,
			long propertiesRemoved) {

		public String summary() {
			return "Normalized sync complete: scanned=%d, updated=%d, propertiesWritten=%d, propertiesRemoved=%d"
					.formatted(nodesScanned, nodesUpdated, propertiesWritten, propertiesRemoved);
		}
	}
}
