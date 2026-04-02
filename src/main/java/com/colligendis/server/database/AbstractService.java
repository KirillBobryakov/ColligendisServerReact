package com.colligendis.server.database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.reactivestreams.ReactiveResult;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import com.colligendis.server.database.exception.AbstractServiceError;
import com.colligendis.server.database.exception.DatabaseError;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public abstract class AbstractService {

	protected static final String DELETED_NODE_LABEL_POSTFIX = "_DELETED";
	protected static final String VERSIONED_NODE_LABEL_POSTFIX = "_VERSIONED";

	protected static final String DELETED_RELATIONSHIP_TYPE_POSTFIX = "_DELETED";

	protected static final String CREATED_AT_PROPERTY = "createdAt";
	protected static final String CREATED_BY_PROPERTY = "createdBy";
	protected static final String UPDATED_AT_PROPERTY = "updatedAt";
	protected static final String UPDATED_BY_PROPERTY = "updatedBy";
	protected static final String DELETED_AT_PROPERTY = "deletedAt";
	protected static final String DELETED_BY_PROPERTY = "deletedBy";

	@Autowired
	protected Driver driver;

	@Value("${spring.neo4j.database:neo4j}")
	private String neo4jDatabase;

	private SessionConfig sessionConfig;

	@PostConstruct
	void initNeo4jSessionConfig() {
		this.sessionConfig = SessionConfig.builder().withDatabase(neo4jDatabase).build();
	}

	/**
	 * Blocking session (same DB as reactive); use from
	 * {@code Mono.fromCallable(...).subscribeOn(boundedElastic())}.
	 */
	protected Session openBlockingSession() {
		return driver.session(sessionConfig);
	}

	protected <T extends AbstractNode> Mono<ExecutionResult<T>> mapResultToExecutionResult(Record record,
			Class<T> nodeClass, BaseLogger baseLogger) {

		return Mono.just(ExecutionResult.<T>builder()
				.node(mapRecordToNode(record, nodeClass, baseLogger))
				.build());
	}

	/**
	 * Maps a {@code RETURN node AS result} (or equivalent) row to a domain node.
	 * Shared by reactive reads and
	 * blocking lookups.
	 */
	protected <N extends AbstractNode> N mapRecordToNode(Record record, Class<N> nodeClass, BaseLogger baseLogger) {
		try {
			Map<String, Object> map;
			try {
				map = record.get("result").asNode().asMap(v -> v.asObject());
			} catch (Exception notNode) {
				map = record.get("result").asMap();
			}
			return AbstractNode.fromPropertiesMap(nodeClass, map);
		} catch (Exception e) {
			baseLogger.traceRed("Failed to map Neo4j record to {}: {}", nodeClass.getSimpleName(), e.getMessage(), e);
			throw new RuntimeException("Failed to map Neo4j record to " + nodeClass.getSimpleName(), e);
		}
	}

	/**
	 * Maps a Neo4j {@code Record} containing a "resultNode" (node) and a "status"
	 * (execution status) field
	 * to an {@link ExecutionResult} with the given node class and execution status.
	 */
	protected <N extends AbstractNode> Function<Record, ExecutionResult<N>> recordToNodeAndStatusMapper(
			Class<N> nodeClass, BaseLogger baseLogger) {
		return (record) -> {
			try {
				N node = null;
				ExecutionStatus status = ExecutionStatus.UNKNOWN;

				// Try to map result if present and not null
				if (record.containsKey("resultNode") && !record.get("resultNode").isNull()) {
					try {
						Map<String, Object> map;
						try {
							map = record.get("resultNode").asNode().asMap(v -> v.asObject());
						} catch (Exception notNode) {
							map = record.get("resultNode").asMap();
						}
						node = AbstractNode.fromPropertiesMap(nodeClass, map);
					} catch (Exception e) {
						baseLogger.traceRed("Failed to map 'resultNode' to {}: {}", nodeClass.getSimpleName(),
								e.getMessage(), e);

						AbstractServiceError error = new AbstractServiceError(
								"recordToNodeAndStatusMapper",
								"Failed to map Neo4j record field 'resultNode' to " + nodeClass.getSimpleName()
										+ ": " + e.getMessage(),
								Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
								e.getStackTrace(), e);
						return ExecutionResult.<N>builder()
								.error(error)
								.build();
					}
				}

				// Try to map status if present
				if (record.containsKey("status") && !record.get("status").isNull()) {
					try {
						String statusStr = record.get("status").asString(null);
						if (statusStr != null) {
							status = ExecutionStatus.valueOf(statusStr);
						}
					} catch (Exception e) {
						baseLogger.traceOrange("Unrecognized 'status' in Neo4j record: {}",
								record.get("status").toString());
						// fallback: keep status UNKNOWN
					}
				}

				return ExecutionResult.<N>builder()
						.node(node)
						.status(status)
						.build();
			} catch (RuntimeException e) {
				baseLogger.traceRed("Failed to map Neo4j record (resultNode + status) to {}: {}",
						nodeClass.getSimpleName(),
						e.getMessage(), e);
				AbstractServiceError error = new AbstractServiceError(
						"recordToNodeAndStatusMapper",
						"Failed to map Neo4j record (resultNode + status) to " + nodeClass.getSimpleName()
								+ ": " + e.getMessage(),
						Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
						e.getStackTrace(), e);
				return ExecutionResult.<N>builder()
						.error(error)
						.build();
			}
		};
	}

	/**
	 * Maps a Neo4j {@code Record} containing a "status" (execution status) field
	 * to an {@link ExecutionResult} with the execution status.
	 */
	protected Function<Record, ExecutionResult<AbstractNode>> recordToStatusMapper(BaseLogger baseLogger) {
		return (record) -> {
			try {
				ExecutionStatus status = ExecutionStatus.UNKNOWN;

				// Try to map status if present
				if (record.containsKey("status") && !record.get("status").isNull()) {
					try {
						String statusStr = record.get("status").asString(null);
						if (statusStr != null) {
							status = ExecutionStatus.valueOf(statusStr);
						}
					} catch (Exception e) {
						baseLogger.traceOrange("Unrecognized 'status' in Neo4j record: {}",
								record.get("status").toString());
						// fallback: keep status UNKNOWN
					}
				}

				return ExecutionResult.<AbstractNode>builder()
						.status(status)
						.build();
			} catch (RuntimeException e) {
				baseLogger.traceRed("Failed to map Neo4j record (status) to ExecutionResult: {}",
						e.getMessage(), e);
				AbstractServiceError error = new AbstractServiceError(
						"recordToStatusMapper",
						"Failed to map Neo4j record (status) to ExecutionResult: " + e.getMessage(),
						Map.of("error", e.getClass().getName()),
						e.getStackTrace(), e);
				return ExecutionResult.builder()
						.error(error)
						.build();
			}
		};
	}

	/**
	 * Maps a Neo4j {@code Record} containing a "resultNode" (node) field
	 * to an {@link ExecutionResult} with the execution status.
	 */
	protected <N extends AbstractNode> Function<Record, ExecutionResult<N>> recordToNodeMapper(Class<N> nodeClass,
			BaseLogger baseLogger) {
		return (record) -> {
			try {
				N node = null;

				// Try to map result if present and not null
				if (record.containsKey("resultNode") && !record.get("resultNode").isNull()) {
					try {
						Map<String, Object> map;
						try {
							map = record.get("resultNode").asNode().asMap(v -> v.asObject());
						} catch (Exception notNode) {
							baseLogger.traceRed("Failed to map 'resultNode' to {}: {}", nodeClass.getSimpleName(),
									notNode.getMessage(), notNode);
							map = record.get("resultNode").asMap();
						}
						node = AbstractNode.fromPropertiesMap(nodeClass, map);
					} catch (Exception e) {
						baseLogger.traceRed("Failed to map 'resultNode' to {}: {}", nodeClass.getSimpleName(),
								e.getMessage(), e);
						// fallback to error result

						AbstractServiceError error = new AbstractServiceError(
								"recordToNodeMapper",
								"Failed to map Neo4j record field 'resultNode' to " + nodeClass.getSimpleName()
										+ ": " + e.getMessage(),
								Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
								e.getStackTrace(), e);
						return ExecutionResult.<N>builder()
								.error(error)
								.build();
					}
				}
				return ExecutionResult.<N>builder()
						.node(node)
						.build();
			} catch (RuntimeException e) {
				baseLogger.traceRed("Failed to map Neo4j record (resultNode) to {}: {}", nodeClass.getSimpleName(),
						e.getMessage(), e);
				AbstractServiceError error = new AbstractServiceError(
						"recordToNodeMapper",
						"Failed to map Neo4j record (resultNode) to " + nodeClass.getSimpleName() + ": "
								+ e.getMessage(),
						Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
						e.getStackTrace(), e);
				return ExecutionResult.<N>builder()
						.error(error)
						.build();
			}
		};
	}

	// Executers for read operations
	protected <T extends AbstractNode> Mono<ExecutionResult<T>> executeReadMono(String query,
			Map<String, Object> parameters, Function<Record, ExecutionResult<T>> resultMapper, String emptyResultError,
			String errorMessage, BaseLogger baseLogger) {
		return Flux.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> session.executeRead(tx -> Mono.fromDirect(tx.run(query, parameters))
						.flatMapMany(ReactiveResult::records)),
				ReactiveSession::close)
				.next()
				.map(resultMapper)
				.switchIfEmpty(
						ExecutionResults.<T>EXECUTION_READ_EMPTY_RESULT_MONO(emptyResultError,
								Map.of("query", query, "parameters", parameters),
								new AbstractServiceError("executeReadMono", "Empty result while reading mono",
										Map.of("query", query, "parameters", parameters),
										null, null)))
				.doOnError(
						error -> baseLogger.traceRed("Failed while query execution while reading mono: {}",
								error.getMessage()))
				.doOnSuccess(
						result -> {
							baseLogger.trace("=== executeReadMono SUCCESS ===");
							baseLogger.trace("Query: {}", query);
							baseLogger.trace("Parameters: {}", parameters);
						})
				.onErrorResume(error -> ExecutionResults.<T>NEO4J_INTERNAL_ERROR_MONO(
						errorMessage + ": " + error.getMessage(),
						Map.of("query", query, "parameters", parameters),
						error.getStackTrace(), error));
	}

	// protected <T extends AbstractNode> Mono<Either<DatabaseError, T>>
	// readMonoQuery(String query,
	// Map<String, Object> parameters,
	// Class<T> nodeClass, String emptyResultError, String errorMessage) {
	// return executeReadMono(query, parameters,
	// resultToExecutionResultMapper(nodeClass), emptyResultError,
	// errorMessage)
	// .map(this::executionResultToEither);
	// }

	protected <T extends AbstractNode> Flux<ExecutionResult<T>> executeReadFlux(String query,
			Map<String, Object> parameters,
			Function<Record, ExecutionResult<T>> resultMapper, String emptyResultError, String errorMessage,
			BaseLogger baseLogger) {
		return Flux.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> session.executeRead(tx -> Mono.fromDirect(tx.run(query, parameters))
						.flatMapMany(ReactiveResult::records)),
				ReactiveSession::close)
				.map(resultMapper)
				.switchIfEmpty(
						ExecutionResults.<T>EXECUTION_READ_EMPTY_RESULT_FLUX(emptyResultError,
								Map.of("query", query, "parameters", parameters),
								new AbstractServiceError("executeReadFlux", "Empty result while reading flux",
										Map.of("query", query, "parameters", parameters),
										null, null)))
				.doOnError(
						error -> {
							baseLogger.traceRed("Failed while query execution while reading flux: {}",
									error.getMessage());
							baseLogger.traceRed("Query: {}", query);
							baseLogger.traceRed("Parameters: {}", parameters.toString());
						})
				.doOnComplete(
						() -> {
							baseLogger.trace("=== executeReadFlux COMPLETED ===");
							baseLogger.trace("Query: {}", query);
							baseLogger.trace("Parameters: {}", parameters.toString());
						})
				.onErrorResume(error -> ExecutionResults.<T>NEO4J_INTERNAL_ERROR_FLUX(
						errorMessage + ": " + error.getMessage(),
						Map.of("query", query, "parameters", parameters),
						error.getStackTrace(), error));
	}

	// Executers for write operations

	protected <T extends AbstractNode> Mono<ExecutionResult<T>> executeWriteMono(String query,
			Map<String, Object> parameters,
			Function<Record, ExecutionResult<T>> resultMapper, String emptyResultError, String errorMessage,
			BaseLogger baseLogger) {
		return Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> Flux
						.from(session.executeWrite(tx -> Mono.fromDirect(tx.run(query, parameters))
								.flatMapMany(ReactiveResult::records)))
						.collectList()
						.flatMap(records -> {
							baseLogger.trace("query: {} result: {}", query, records);
							if (records.isEmpty()) {
								return Mono.empty();
							}
							return Mono.just(records.get(0));
						}),
				(ReactiveSession session) -> Mono.from(session.close()))
				.map(resultMapper)
				.switchIfEmpty(
						ExecutionResults.<T>EXECUTION_WRITE_EMPTY_RESULT_MONO(emptyResultError,
								Map.of("query", query, "parameters", parameters),
								new AbstractServiceError("executeWriteMono", "Empty result while writing mono",
										Map.of("query", query, "parameters", parameters),
										null, null)))
				.doOnError(
						error -> {
							baseLogger.traceRed("Failed while query execution while writing mono: {}",
									error.getMessage());
							baseLogger.traceRed("Query: {}", query);
							baseLogger.traceRed("Parameters: {}", parameters.toString());
						})
				.doOnSuccess(
						result -> {
							baseLogger.trace("=== executeWriteMono SUCCESS ===");
							baseLogger.trace("Query: {}", query);
							baseLogger.trace("Parameters: {}", parameters.toString());

						})
				.onErrorResume(error -> {
					return ExecutionResults.<T>NEO4J_INTERNAL_ERROR_MONO(errorMessage + ": " + error.getMessage(),
							Map.of("query", query, "parameters", parameters),
							error.getStackTrace(), error);
				});
	}

	// // Mappers

	// private Function<Record, String> stringResultMapper = (record) ->
	// record.get("result").asString();
	// private Function<Record, List<String>> listStringResultMapper = (record) ->
	// record.get("result")
	// .asList(v -> v.asString());

	// private Function<Record, Boolean> booleanResultMapper = (record) ->
	// record.get("result").asBoolean();
	// private Function<Record, Integer> integerResultMapper = (record) ->
	// record.get("result").asInt();
	// private Function<Record, Long> longResultMapper = (record) ->
	// record.get("result").asLong();
	// private Function<Record, Double> doubleResultMapper = (record) ->
	// record.get("result").asDouble();

	// private <N extends AbstractNode> BiFunction<Record, Class<N>,
	// Either<DatabaseError, N>> recordToNodeMapper() {
	// return (record, nodeClass) -> {
	// try {
	// N n = mapRecordToNode(record, nodeClass);
	// return Either.<DatabaseError, N>right(n);
	// } catch (RuntimeException e) {
	// return Either.left(Errors.NEO4J_INTERNAL_ERROR(
	// "Failed to map Neo4j record to " + nodeClass.getSimpleName() + ": " +
	// e.getMessage(),
	// Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
	// e.getStackTrace(), e));
	// }
	// };
	// }

	// private <N extends AbstractNode> BiFunction<Record, Class<N>,
	// ExecutionResult<N>> recordToNodeAndStatusMapper() {
	// return (record, nodeClass) -> {
	// try {
	// N n = mapRecordToNode(record, nodeClass);
	// ExecutionStatus status = ExecutionStatus.SUCCESS; // or derive from record if
	// needed
	// return ExecutionResult.<N>builder()
	// .node(n)
	// .status(status)
	// .build();
	// } catch (RuntimeException e) {
	// return ExecutionResult.<N>builder()
	// .error(Errors.NEO4J_INTERNAL_ERROR(
	// "Failed to map Neo4j record to " + nodeClass.getSimpleName() + ": " +
	// e.getMessage(),
	// Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
	// e.getStackTrace(), e))
	// .build();
	// }
	// };
	// }

	// protected <N extends AbstractNode> Function<Record, ExecutionResult<N>>
	// resultMapper(
	// Class<N> nodeClass) {
	// return (record) -> {
	// try {
	// N n = mapRecordToNode(record, nodeClass);
	// return ExecutionResult.<N>builder().node(n).build();
	// } catch (RuntimeException e) {
	// return ExecutionResult.<N>builder().error(Errors.NEO4J_INTERNAL_ERROR(
	// "Failed to map Neo4j record to " + nodeClass.getSimpleName() + ": " +
	// e.getMessage(),
	// Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
	// e.getStackTrace(), e)).build();
	// }
	// };
	// }

	// protected <N extends AbstractNode> Function<Record, ExecutionResult<N>>
	// resultToExecutionResultMapper(
	// Class<N> nodeClass) {
	// return record -> resultMapper(nodeClass).apply(record);
	// }

	// private <N extends AbstractNode> Either<DatabaseError, N>
	// executionResultToEither(ExecutionResult<N> er) {
	// if (er.getError() != null) {
	// return Either.left(er.getError());
	// }
	// return Either.right(er.getNode());
	// }

	// private Either<DatabaseError, ExecutionStatus>
	// executionResultToEitherStatus(ExecutionResult<?> er) {
	// if (er.getError() != null) {
	// return Either.left(er.getError());
	// }
	// return Either.right(er.getStatus());
	// }

	// protected Function<Record, Either<DatabaseError, Boolean>>
	// booleanResultEitherMapper = (record) -> Either
	// .<DatabaseError, Boolean>right(booleanResultMapper.apply(record));

	// protected Function<Record, Either<DatabaseError, String>>
	// stringResultEitherMapper = (record) -> Either
	// .<DatabaseError, String>right(stringResultMapper.apply(record));

	// Queries

	/**
	 * Checks if a node exists in the database with a given property value and
	 * label. Can be found many nodes with the same property value and label, but
	 * only one node with the same property value and label is expected to be found.
	 * Try to use unique property value if possible.
	 * 
	 * Statuses:
	 * - NODE_IS_NOT_EXISTS: The node does not exist.
	 * - NODE_IS_EXISTS: The node exists.
	 * - MORE_THAN_ONE_NODE_IS_FOUND: More than one node was found.
	 * 
	 * @param propertyName  The name of the property to check.
	 * @param propertyValue The value of the property to check.
	 * @param nodeLabel     The label of the node to check.
	 * @return Either a {@link DatabaseError} or an {@link ExecutionStatus} such as
	 *         {@link ExecutionStatus#NODE_IS_EXISTS} /
	 *         {@link ExecutionStatus#NODE_IS_NOT_EXISTS}.
	 */
	protected <T extends AbstractNode> Mono<ExecutionResult<T>> isNodeExistsByUniquePropertyValue(
			String propertyName,
			String propertyValue,
			String nodeLabel, Class<T> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Checking if node exists with property: {} value: {} node label: {}", propertyName,
				propertyValue,
				nodeLabel);

		if (propertyName == null) {
			baseLogger.traceRed("Input parameter propertyName is null");
			return ExecutionResults.INPUT_PARAMETERS_ERROR_MONO("isNodeExistsByUniquePropertyValue",
					"Property name is null");
		}
		if (propertyValue == null) {
			baseLogger.traceRed("Input parameter propertyValue is null");
			return ExecutionResults.INPUT_PARAMETERS_ERROR_MONO("isNodeExistsByUniquePropertyValue",
					"Property value is null");
		}
		if (propertyName.isEmpty()) {
			baseLogger.traceRed("Input parameter propertyName is empty");
			return ExecutionResults.INPUT_PARAMETERS_ERROR_MONO("isNodeExistsByUniquePropertyValue",
					"Property name is empty");
		}
		if (propertyValue.isEmpty()) {
			baseLogger.traceRed("Input parameter propertyValue is empty");
			return ExecutionResults.INPUT_PARAMETERS_ERROR_MONO("isNodeExistsByUniquePropertyValue",
					"Property value is empty");
		}
		if (nodeLabel == null) {
			baseLogger.traceRed("Input parameter nodeLabel is null");
			return ExecutionResults.INPUT_PARAMETERS_ERROR_MONO("isNodeExistsByUniquePropertyValue",
					"Node label is null");
		}
		if (nodeLabel.isEmpty()) {
			baseLogger.traceRed("Input parameter nodeLabel is empty");
			return ExecutionResults.INPUT_PARAMETERS_ERROR_MONO("isNodeExistsByUniquePropertyValue",
					"Node label is empty");
		}

		final String query = String.format(
				"MATCH (node:%s {%s: $propertyValue}) WITH node LIMIT 2 " +
						"WITH collect(node) AS nodes " +
						"RETURN " +
						"CASE WHEN size(nodes) = 1 THEN nodes[0] ELSE NULL END AS resultNode, " +
						"CASE " +
						"WHEN size(nodes) = 0 THEN 'NODE_IS_NOT_EXISTS' " +
						"WHEN size(nodes) = 1 THEN 'NODE_IS_EXISTS' " +
						"ELSE 'MORE_THAN_ONE_NODE_IS_FOUND' " +
						"END AS status",
				nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Empty result while checking if node exists with property: " + propertyName
				+ " value: " + propertyValue
				+ " node label: " + nodeLabel + ": Empty result while checking if node exists";
		String errorMessage = "Failed while query execution while checking if node exists with property: "
				+ propertyName + " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadMono(query, parameters, recordToNodeAndStatusMapper(nodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	/**
	 * Updates the properties of a node in the database.
	 * 
	 * Statuses:
	 * - NODE_IS_NOT_FOUND: The node was not found.
	 * - NODE_NOTHING_TO_UPDATE: The node has no properties to update.
	 * - NODE_WAS_UPDATED: The node was updated.
	 * 
	 * @param node       The node to update.
	 * @param user       The user who is updating the node.
	 * @param nodeClass  The class of the node to update.
	 * @param baseLogger The base logger to use.
	 * @return The execution result containing the updated node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N>> updateNodeProperties(N node,
			AbstractUser user, Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Updating node properties with uuid: {} and label: {}", node.getUuid(), node.getLabel());

		if (node.getUuid() == null) {
			baseLogger.traceRed("Input parameter node.uuid is null");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("updateNodeProperties",
					"Input parameter node.uuid is null");
		}
		if (node.getLabel() == null) {
			baseLogger.traceRed("Input parameter node.label is null");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("updateNodeProperties",
					"Input parameter node.label is null");
		}
		if (node.getPropertiesMap() == null || node.getPropertiesMap().isEmpty()) {
			baseLogger.traceRed("Input parameter node.propertiesMap is null or empty");
			return ExecutionResults
					.<N>INPUT_PARAMETERS_ERROR_MONO("updateNodeProperties",
							"Input parameter node.propertiesMap is null or empty");
		}

		String query = String.format(
				"OPTIONAL MATCH (node:%s {uuid: $nodeUuid}) " +
						"WITH node, $properties AS propsMap " +
						"WITH node, propsMap, " +
						"CASE WHEN node IS NULL THEN 'NODE_NOT_FOUND' ELSE 'OK' END AS nodeStatus, " +
						"CASE WHEN node IS NULL THEN [] ELSE [k IN keys(propsMap) WHERE node[k] IS NULL OR node[k] <> propsMap[k]] END AS diffKeys "
						+
						"CALL (node, nodeStatus, diffKeys, propsMap) { " +
						// --- NODE_IS_NOT_FOUND ---
						"WITH node, nodeStatus WHERE nodeStatus = 'NODE_IS_NOT_FOUND' RETURN NULL AS resultNode, 'NODE_IS_NOT_FOUND' AS status "
						+
						"UNION ALL " +
						// --- NODE_NOTHING_TO_UPDATE ---
						"WITH node, diffKeys, nodeStatus WHERE nodeStatus = 'OK' AND size(diffKeys) = 0 RETURN node AS resultNode, 'NODE_NOTHING_TO_UPDATE' AS status "
						+
						"UNION ALL " +
						// --- NODE_WAS_UPDATED ---
						"WITH node, propsMap, diffKeys, nodeStatus WHERE nodeStatus = 'OK' AND size(diffKeys) > 0 " +
						// ❗ удаляем старые связи версии
						"OPTIONAL MATCH (node)-[oldRel:PREVIOUS_VERSION]->() DELETE oldRel " +
						"WITH node, propsMap, diffKeys CALL apoc.refactor.cloneNodes([node], true, ['uuid']) YIELD output AS newNode "
						+
						"FOREACH (k IN diffKeys | SET newNode[k] = propsMap[k]) " +
						"SET newNode.uuid = randomUUID(), newNode.updatedAt = datetime(), newNode.updatedBy = $updatedBy "
						+
						"CREATE (newNode)-[:PREVIOUS_VERSION]->(node) " +
						"WITH node, newNode, labels(node) AS oldLabels " +
						"FOREACH (l IN oldLabels | REMOVE node:$(l)) " +
						"FOREACH (l IN [x IN oldLabels | x + '_VERSIONED'] | SET node:$(l)) " +
						"RETURN newNode AS resultNode, 'NODE_WAS_UPDATED' AS status " +
						"} RETURN resultNode, status",
				node.getLabel());

		Map<String, Object> parameters = Map.of("nodeUuid", node.getUuid(), "properties", node.getPropertiesMap(),
				"updatedBy",
				user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No properties updated for node with uuid: " + node.getUuid() + " and label: "
				+ node.getLabel() + ": No properties updated";
		String errorMessage = "Failed while query execution while updating node properties with uuid: " + node.getUuid()
				+ " and label: " + node.getLabel();

		return executeWriteMono(query, parameters,
				recordToNodeAndStatusMapper(nodeClass, baseLogger), emptyResultError, errorMessage, baseLogger);
	}

	/**
	 * Creates a new node in the database.
	 * Statuses:
	 * - NODE_NOT_CREATED: The node was not created.
	 * - NODE_WAS_CREATED: The node was created.
	 * 
	 * @param node       The node to create.
	 * @param user       The user who is creating the node.
	 * @param nodeClass  The class of the node to create.
	 * @param baseLogger The base logger to use.
	 * @return The execution result containing the created node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N>> createNode(N node, AbstractUser user,
			Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Creating node with label: {}", node.getLabel());

		if (node.getUuid() != null) {
			baseLogger.traceRed("Input parameter node.uuid is not null");
			return ExecutionResults.<N>CREATING_NODE_WITH_EXISTING_UUID_ERROR_MONO(
					"createNode",
					"Input parameter node.uuid is not null. Use updateNodeProperties instead of createNode to update node properties.",
					Map.of("node", node));
		}
		if (node.getLabel() == null || node.getLabel().isEmpty()) {
			baseLogger.traceRed("Input parameter node.label is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("createNode",
					"Input parameter node.label is null or empty");
		}

		String query = String.format(
				"""
						CREATE (node:%s {uuid: randomUUID(), createdAt: datetime({ timezone: '+03:00' }), createdBy: $createdBy %s})
						RETURN node AS resultNode,
						CASE
							WHEN node IS NULL
							THEN 'NODE_NOT_CREATED'
							ELSE 'NODE_WAS_CREATED'
						END AS status """,
				node.getLabel(),
				node.getPropertiesQuery());

		Map<String, Object> parameters = new HashMap<>(node.getPropertiesMap());
		parameters.put("createdBy", user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No node created with uuid: " + node.getUuid() + " and label: " + node.getLabel()
				+ ": No node created";
		String errorMessage = "Failed while query execution while creating node with uuid: " + node.getUuid()
				+ " and label: " + node.getLabel();

		return executeWriteMono(query, parameters, recordToNodeAndStatusMapper(nodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	/**
	 * Deletes a node in the database.
	 * Statuses:
	 * - NODE_IS_NOT_FOUND: The node was not found.
	 * - NODE_WAS_DELETED: The node was deleted.
	 * 
	 * @param node       The node to delete.
	 * @param user       The user who is deleting the node.
	 *                   protected <N extends AbstractNode> Mono<ExecutionResult<N>>
	 *                   deleteNode(N node,
	 * @param nodeClass  The class of the node to delete.
	 * @param baseLogger The base logger to use.
	 * @return The execution result containing the deleted node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N>> deleteNode(N node,
			AbstractUser user, Class<N> nodeClass, BaseLogger baseLogger) {
		if (node == null) {
			baseLogger.traceRed("Input parameter node is null");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("deleteNode", "Input parameter node is null");
		}
		baseLogger.trace("Deleting node with uuid: {} and label: {}", node.getUuid(), node.getLabel());

		if (node.getUuid() == null) {
			baseLogger.traceRed("Input parameter node.uuid is null");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("deleteNode", "Input parameter node.uuid is null");
		}

		String query = String.format(
				"OPTIONAL MATCH (n {uuid: $uuid})" +
						"WITH n, labels(n) AS oldLabels " +
						"CALL (n, oldLabels) { " +
						"WITH n, oldLabels " +
						"REMOVE n:$(oldLabels) " +
						"WITH n, oldLabels " +
						"WITH n, [label IN oldLabels | label + '_DELETED'] AS newLabels " +
						"SET n:$(newLabels) " +
						"SET n.deletedAt = datetime({ timezone: '+03:00' }), n.deletedBy = $deletedBy " +
						"RETURN n AS updatedNode } " +
						"RETURN " +
						"updatedNode AS resultNode, " +
						"CASE " +
						"WHEN n IS NULL THEN 'NODE_IS_NOT_FOUND' " +
						"ELSE 'NODE_WAS_DELETED' " +
						"END AS status");

		Map<String, Object> parameters = Map.of("uuid", node.getUuid(), "deletedBy",
				user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No node deleted with uuid: " + node.getUuid() + ": No node deleted";
		String errorMessage = "Failed while query execution while deleting node with uuid: " + node.getUuid();

		return executeWriteMono(query, parameters, recordToNodeAndStatusMapper(nodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	/**
	 * Finds a node by its unique property value.
	 * Statuses:
	 * - NODE_IS_NOT_FOUND: The node was not found.
	 * - NODE_IS_FOUND: The node was found.
	 * - MORE_THAN_ONE_NODE_IS_FOUND: More than one node was found.
	 * 
	 * @param propertyName  The name of the property to find the node by.
	 * @param propertyValue The value of the property to find the node by.
	 * @param nodeLabel     The label of the node to find.
	 * @param nodeClass     The class of the node to find.
	 * @param baseLogger    The base logger to use.
	 * @return The execution result containing the found node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N>> findNodeByUniquePropertyValue(
			String propertyName,
			Object propertyValue,
			String nodeLabel, Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Finding node with unique property: {} value: {} node label: {}", propertyName, propertyValue,
				nodeLabel);

		if (propertyName == null || propertyName.isEmpty()) {
			baseLogger.traceRed("Input parameter propertyName is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("findNodeByUniquePropertyValue",
					"Input parameter propertyName is null or empty");
		}
		if (propertyValue == null) {
			baseLogger.traceRed("Input parameter propertyValue is null");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("findNodeByUniquePropertyValue",
					"Input parameter propertyValue is null");
		}
		if (nodeLabel == null || nodeLabel.isEmpty()) {
			baseLogger.traceRed("Input parameter nodeLabel is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("findNodeByUniquePropertyValue",
					"Input parameter nodeLabel is null or empty");
		}

		final String query = String.format(
				"OPTIONAL MATCH (node:%s {%s: $propertyValue}) " +
						"WITH collect(node) AS nodes " +
						"RETURN " +
						"CASE " +
						"WHEN size(nodes) = 1 THEN nodes[0] " +
						"ELSE NULL " +
						"END AS resultNode, " +
						"CASE " +
						"WHEN size(nodes) = 0 THEN 'NODE_IS_NOT_FOUND' " +
						"WHEN size(nodes) = 1 THEN 'NODE_IS_FOUND' " +
						"ELSE 'MORE_THAN_ONE_NODE_IS_FOUND' " +
						"END AS status",
				nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No node found with property: " + propertyName + " value: " + propertyValue
				+ " node label: " + nodeLabel + ": No node found";
		String errorMessage = "Failed while query execution while finding node with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadMono(query, parameters, recordToNodeAndStatusMapper(nodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	/**
	 * Finds a node by its uuid.
	 * Statuses:
	 * - NODE_IS_NOT_FOUND: The node was not found.
	 * - NODE_IS_FOUND: The node was found.
	 * - MORE_THAN_ONE_NODE_IS_FOUND: More than one node was found.
	 * 
	 * @param uuid       The uuid of the node to find.
	 * @param nodeLabel  The label of the node to find.
	 * @param nodeClass  The class of the node to find.
	 * @param baseLogger The base logger to use.
	 * @return The execution result containing the found node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N>> findNodeByUuid(String uuid,
			String nodeLabel, Class<N> nodeClass, BaseLogger baseLogger) {
		baseLogger.trace("Finding node with uuid: {} and label: {}", uuid, nodeLabel);
		return findNodeByUniquePropertyValue("uuid", uuid, nodeLabel, nodeClass, baseLogger);
	}

	protected <N extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<N>> findUniqueNodeByPropertyValueAndTargetNode(
			String propertyName, Object propertyValue, String nodeLabel, Class<N> nodeClass, T targetNode,
			String relationshipType, BaseLogger baseLogger) {
		baseLogger.trace(
				"Finding unique node with property: {} value: {} node label: {} connected to node: {} with relationship type: {}",
				propertyName, propertyValue, nodeLabel, targetNode.getUuid(), relationshipType);

		if (propertyName == null || propertyName.isEmpty()) {
			baseLogger.traceRed("Input parameter propertyName is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("findUniqueNodeByPropertyValueAndTargetNode",
					"Input parameter propertyName is null or empty");
		}
		if (propertyValue == null) {
			baseLogger.traceRed("Input parameter propertyValue is null");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("findUniqueNodeByPropertyValueAndTargetNode",
					"Input parameter propertyValue is null");
		}
		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("findUniqueNodeByPropertyValueAndTargetNode",
					"Input parameter relationshipType is null or empty");
		}
		if (nodeLabel == null || nodeLabel.isEmpty()) {
			baseLogger.traceRed("Input parameter nodeLabel is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_MONO("findUniqueNodeByPropertyValueAndTargetNode",
					"Input parameter nodeLabel is null or empty");
		}

		final String query = String.format(
				"OPTIONAL MATCH (node:%s {%s: $propertyValue})-[:%s]->(connectedToNode {uuid: $connectedToNodeUuid}) " +
						"WITH collect(node) AS nodes " +
						"RETURN " +
						"CASE " +
						"WHEN size(nodes) = 1 THEN nodes[0] " +
						"ELSE NULL " +
						"END AS resultNode, " +
						"CASE " +
						"WHEN size(nodes) = 0 THEN 'NODE_IS_NOT_FOUND' " +
						"WHEN size(nodes) = 1 THEN 'NODE_IS_FOUND' " +
						"ELSE 'MORE_THAN_ONE_NODE_IS_FOUND' " +
						"END AS status",
				nodeLabel, propertyName, relationshipType);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue, "connectedToNodeUuid",
				targetNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for property: " + propertyName + " value: "
				+ propertyValue + " node label: " + nodeLabel + " connected to node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while finding node with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel + " connected to node: "
				+ targetNode.getUuid() + " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters, recordToNodeAndStatusMapper(nodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	protected <N extends AbstractNode> Flux<ExecutionResult<N>> findNodesByPropertyValue(
			String propertyName, Object propertyValue, String nodeLabel, Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Finding nodes with property: {} value: {} node label: {}", propertyName, propertyValue,
				nodeLabel);

		if (propertyName == null || propertyName.isEmpty()) {
			baseLogger.traceRed("Input parameter propertyName is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_FLUX("findNodesByPropertyValue",
					"Input parameter propertyName is null or empty");
		}
		if (propertyValue == null) {
			baseLogger.traceRed("Input parameter propertyValue is null");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_FLUX("findNodesByPropertyValue",
					"Input parameter propertyValue is null");
		}
		if (nodeLabel == null || nodeLabel.isEmpty()) {
			baseLogger.traceRed("Input parameter nodeLabel is null or empty");
			return ExecutionResults.<N>INPUT_PARAMETERS_ERROR_FLUX("findNodesByPropertyValue",
					"Input parameter nodeLabel is null or empty");
		}

		final String query = String.format("OPTIONAL MATCH (node:%s {%s: $propertyValue}) RETURN node AS result",
				nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for property: " + propertyName + " value: "
				+ propertyValue
				+ " node label: " + nodeLabel + ": No nodes found";
		String errorMessage = "Failed while query execution while finding nodes with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadFlux(query, parameters, recordToNodeAndStatusMapper(nodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	// RELATIONSHIPS

	// Unique relationship is relationship from one node to another node, there can
	// be only one relationship of this type between the two nodes.

	/*
	 * Check if a relationship exists between two nodes with a given relationship
	 * type.
	 * 
	 * Statuses:
	 * - RELATIONSHIP_IS_NOT_EXISTS: The relationship does not exist.
	 * - RELATIONSHIP_IS_EXISTS: The relationship exists.
	 * - MORE_THAN_ONE_RELATIONSHIP_IS_FOUND: More than one relationship was found.
	 * 
	 * @param sourceNode The source node.
	 * 
	 * @param targetNode The target node.
	 * 
	 * @param relationshipType The relationship type.
	 * 
	 * @param baseLogger The base logger.
	 * 
	 * @return The execution result containing the relationship and status.
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> isRelationshipExists(
			S sourceNode, T targetNode, String relationshipType, BaseLogger baseLogger) {

		baseLogger.trace(
				"Checking if relationship exists between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults
					.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("isRelationshipExists",
							"Input parameter relationshipType is null or empty");
		}

		String query = String.format("""
				OPTIONAL MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]-(target:%s {uuid: $targetNodeUuid})
				WITH count(r) AS relCount
				RETURN
				    CASE
				        WHEN relCount = 0 THEN 'RELATIONSHIP_IS_NOT_EXISTS'
				        WHEN relCount = 1 THEN 'RELATIONSHIP_IS_EXISTS'
				        ELSE 'MORE_THAN_ONE_RELATIONSHIP_IS_FOUND'
				    END AS status
				""",
				sourceNode.getLabel(), relationshipType, targetNode.getLabel());
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(), "targetNodeUuid",
				targetNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " and target node: " + targetNode.getUuid() + " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while checking if relationship exists between source node: "
				+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters,
				recordToStatusMapper(baseLogger), emptyResultError, errorMessage, baseLogger);
	}

	/*
	 * Check if any outgoing relationships exists from a node with a given
	 * relationship type.
	 */
	protected <S extends AbstractNode> Mono<ExecutionResult<AbstractNode>> isAnyOutgoingRelationshipsExists(
			S sourceNode,
			String relationshipType, BaseLogger baseLogger) {

		baseLogger.trace(
				"Checking if any outgoing relationships exists from source node: {} with relationship type: {}",
				sourceNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults
					.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("isAnyOutgoingRelationshipsExists",
							"Input parameter relationshipType is null or empty");
		}

		String query = String.format("""
				MATCH (source:%s {uuid: $sourceNodeUuid})
				OPTIONAL MATCH (source)-[r:%s]->(target)
				WITH count(r) AS relCount
				RETURN
				    CASE
				        WHEN relCount = 0 THEN 'RELATIONSHIP_IS_NOT_EXISTS'
				        WHEN relCount = 1 THEN 'RELATIONSHIP_IS_EXISTS'
				        ELSE 'MORE_THAN_ONE_RELATIONSHIP_IS_FOUND'
				    END AS status
				""",
				sourceNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while checking if any outgoing relationships exists from source node: "
				+ sourceNode.getUuid() + " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters, recordToStatusMapper(baseLogger), emptyResultError, errorMessage,
				baseLogger);
	}

	/*
	 * Check if any incoming relationships exists to a node with a given
	 * relationship type.
	 */
	protected <T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> isIncomingRelationshipsExists(
			T targetNode,
			String relationshipType, BaseLogger baseLogger) {

		baseLogger.trace(
				"Checking if any incoming relationships exists to target node: {} with relationship type: {}",
				targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults
					.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("isIncomingRelationshipsExists",
							"Input parameter relationshipType is null or empty");
		}

		String query = String.format("""
				MATCH (target:%s {uuid: $targetNodeUuid})
				OPTIONAL MATCH (source)-[r:%s]->(target)
				WITH count(r) AS relCount
				RETURN
				    CASE
				        WHEN relCount = 0 THEN 'RELATIONSHIP_IS_NOT_EXISTS'
				        WHEN relCount = 1 THEN 'RELATIONSHIP_IS_EXISTS'
				        ELSE 'MORE_THAN_ONE_RELATIONSHIP_IS_FOUND'
				    END AS status
				""",
				targetNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("targetNodeUuid", targetNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while checking if any incoming relationships exists to target node: "
				+ targetNode.getUuid() + " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters,
				recordToStatusMapper(baseLogger), emptyResultError, errorMessage, baseLogger);
	}

	/*
	 * Create a single relationship between two nodes. There are many relationships
	 * from one node to anothers with the same relationship type.
	 * 
	 * Statuses:
	 * - NODE_IS_NOT_FOUND: The source or target node is not found.
	 * - RELATIONSHIP_IS_EXISTS: The relationship already exists.
	 * - RELATIONSHIP_WAS_CREATED: The relationship was created.
	 * 
	 * @param sourceNode The source node.
	 * 
	 * @param targetNode The target node.
	 * 
	 * @param relationshipType The relationship type.
	 * 
	 * @param user The user.
	 * 
	 * @param baseLogger The base logger.
	 * 
	 * @return The execution result containing the created relationship and status.
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> createSingleRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {

		baseLogger.trace(
				"Creating single relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("createSingleRelationship",
					"Input parameter relationshipType is null or empty");
		}

		String query = String.format("""
				OPTIONAL MATCH (source:%s {uuid: $sourceNodeUuid})
				OPTIONAL MATCH (target:%s {uuid: $targetNodeUuid})
				WITH source, target,
				     CASE WHEN source IS NULL OR target IS NULL THEN 'NODE_IS_NOT_FOUND' ELSE 'OK' END AS nodeStatus
				OPTIONAL MATCH (source)-[r:%s]->(target)
				WITH source, target, r, nodeStatus,
				     CASE
				         WHEN nodeStatus = 'NODE_IS_NOT_FOUND' THEN 'NODE_IS_NOT_FOUND'
				         WHEN r IS NOT NULL THEN 'RELATIONSHIP_IS_EXISTS'
				         ELSE 'RELATIONSHIP_WAS_CREATED'
				     END AS status
				FOREACH (_ IN CASE WHEN status = 'RELATIONSHIP_WAS_CREATED' THEN [1] ELSE [] END |
				    CREATE (source)-[:%s {createdAt: datetime({ timezone: '+03:00' }), createdBy: $createdBy}]->(target)
				)
				RETURN status
				""",
				sourceNode.getLabel(), targetNode.getLabel(), relationshipType, relationshipType);

		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(), "targetNodeUuid",
				targetNode.getUuid(),
				"createdBy", user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " and target node: " + targetNode.getUuid() + " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while creating single relationship between source node: "
				+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid() + " with relationship type: "
				+ relationshipType;

		return executeWriteMono(query, parameters, recordToStatusMapper(baseLogger), emptyResultError, errorMessage,
				baseLogger);
	}

	/*
	 * Delete a single relationship between two nodes.
	 * 
	 * Statuses:
	 * - RELATIONSHIP_IS_NOT_EXISTS: The relationship does not exist.
	 * - RELATIONSHIP_WAS_DELETED: The relationship was deleted.
	 * - MORE_THAN_ONE_RELATIONSHIP_IS_FOUND: More than one relationship was found.
	 * 
	 * @param sourceNode The source node.
	 * 
	 * @param targetNode The target node.
	 * 
	 * @param relationshipType The relationship type.
	 * 
	 * @param user The user.
	 * 
	 * @param baseLogger The base logger.
	 * 
	 * @return The execution result containing the deleted relationship and status.
	 * 
	 * @throws IllegalArgumentException if the relationship type is null or empty.
	 * 
	 * @throws DatabaseError if the query execution fails.
	 * 
	 * @throws ServiceError if the query execution fails.
	 * 
	 * @throws ServiceException if the query execution fails.
	 * 
	 * @throws ServiceException if the query execution fails.
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> deleteSingleRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {

		baseLogger.trace(
				"Deleting single relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("deleteSingleRelationship",
					"Input parameter relationshipType is null or empty");
		}

		String query = String.format("""
				MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target:%s {uuid: $targetNodeUuid})
				WITH collect(r) AS rels, source, target
				FOREACH (_ IN CASE WHEN size(rels) = 1 THEN [1] ELSE [] END |
				    CREATE (source)-[r2:%s]->(target)
				    SET r2 = properties(rels[0]),
				        r2.deletedAt = datetime({ timezone: '+03:00' }),
				        r2.deletedBy = $deletedBy
				    DELETE rels[0]
				)
				RETURN
				    CASE
				        WHEN size(rels) = 0 THEN 'RELATIONSHIP_IS_NOT_EXISTS'
				        WHEN size(rels) = 1 THEN 'RELATIONSHIP_WAS_DELETED'
				        ELSE 'MORE_THAN_ONE_RELATIONSHIP_IS_FOUND'
				    END AS status
				""", sourceNode.getLabel(), relationshipType, targetNode.getLabel(),
				relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX);

		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(), "targetNodeUuid",
				targetNode.getUuid(),
				"deletedBy", user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " and target node: " + targetNode.getUuid() + " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while deleting single relationship between source node: "
				+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid() + " with relationship type: "
				+ relationshipType;

		return executeWriteMono(query, parameters, recordToStatusMapper(baseLogger), emptyResultError, errorMessage,
				baseLogger);
	}

	/*
	 * Delete all outgoing relationships with RelationshipType from a node
	 * 
	 * Statuses:
	 * - RELATIONSHIP_IS_NOT_EXISTS: The relationship does not exist.
	 * - RELATIONSHIP_WAS_DELETED: The relationship was deleted.
	 * 
	 */
	protected <S extends AbstractNode> Mono<ExecutionResult<AbstractNode>> deleteAllOutgoingRelationshipsWithRelationshipType(
			S sourceNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {
		baseLogger.trace(
				"Deleting all outgoing relationships with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO(
					"deleteAllOutgoingRelationshipsWithRelationshipType",
					"Input parameter relationshipType is null or empty");
		}

		String query = String.format("""
				MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target)
				CALL (source, r, target) {
				    WITH source, r, target
				    CREATE (source)-[r2:%s]->(target)
				    SET r2 = properties(r),
				        r2.deletedAt = datetime({ timezone: '+03:00' }),
				        r2.deletedBy = $deletedBy
				    DELETE r
				}
				RETURN
				    CASE
				        WHEN count(r) > 0 THEN 'RELATIONSHIP_WAS_DELETED'
				        ELSE 'RELATIONSHIP_IS_NOT_EXISTS'
				    END AS status
				""",
				sourceNode.getLabel(), relationshipType, relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(),
				"deletedBy", user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while deleting all outgoing relationships with relationship type: "
				+ relationshipType + " from source node: " + sourceNode.getUuid();

		return executeWriteMono(query, parameters, recordToStatusMapper(baseLogger), emptyResultError, errorMessage,
				baseLogger);
	}

	/*
	 * Delete all incoming relationships with RelationshipType to a node
	 */
	protected <T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> deleteAllIncomingRelationshipsWithRelationshipType(
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {

		baseLogger.trace(
				"Deleting all incoming relationships with relationship type: {} to target node: {}",
				relationshipType, targetNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO(
					"deleteAllIncomingRelationshipsWithRelationshipType",
					"Input parameter relationshipType is null or empty");
		}

		String query = String.format("""
				MATCH (source)-[r:%s]->(target:%s {uuid: $targetNodeUuid})
				CALL (source, r, target) {
				    WITH source, r, target
				    CREATE (source)-[r2:%s]->(target)
				    SET r2 = properties(r),
				        r2.deletedAt = datetime({ timezone: '+03:00' }),
				        r2.deletedBy = $deletedBy
				    DELETE r
				}
				RETURN
				    CASE
				        WHEN count(r) > 0 THEN 'RELATIONSHIP_WAS_DELETED'
				        ELSE 'RELATIONSHIP_IS_NOT_EXISTS'
				    END AS status
				""",
				relationshipType, targetNode.getLabel(), relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX);

		Map<String, Object> parameters = Map.of("targetNodeUuid", targetNode.getUuid(),
				"deletedBy", user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while deleting all incoming relationships with relationship type: "
				+ relationshipType + " to target node: " + targetNode.getUuid();

		return executeWriteMono(query, parameters, recordToStatusMapper(baseLogger), emptyResultError, errorMessage,
				baseLogger);
	}

	/*
	 * Create a unique relationship between two nodes. There can be only one
	 * relationship of this type from fromNode. It uses for set a property for
	 * entity.
	 * 
	 * Statuses:
	 * - RELATIONSHIP_IS_EXISTS: The relationship already exists.
	 * - RELATIONSHIP_WAS_CREATED: The relationship was created.
	 * - ERROR: An error occurred.
	 * 
	 * <img src="doc-files/test.png" alt="Test image" />
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> createUniqueTargetedRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {
		baseLogger.trace(
				"Creating unique relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("createUniqueTargetedRelationship",
					"Input parameter relationshipType is null or empty");
		}

		String query = String.format(
				"""
						OPTIONAL MATCH (source:%s {uuid: $sourceNodeUuid})
						OPTIONAL MATCH (target:%s {uuid: $targetNodeUuid})
						WITH source, target
						WHERE source IS NULL OR target IS NULL
						RETURN 'NODE_IS_NOT_FOUND' AS status
						UNION
						MATCH (source:%s {uuid: $sourceNodeUuid})
						MATCH (target:%s {uuid: $targetNodeUuid})
						OPTIONAL MATCH (source)-[r:%s]->(oldTarget:%s)
						WHERE oldTarget.uuid <> $targetNodeUuid
						CALL(source, r, oldTarget) {
						    WITH source, r, oldTarget
						    WHERE oldTarget IS NOT NULL
						    CREATE (source)-[r2:%s_DELETED]->(oldTarget)
						    SET r2 = properties(r),
						        r2.deletedAt = datetime({ timezone: '+03:00' }),
						        r2.deletedBy = $deletedBy
						    DELETE r
						}
						WITH source, target
						MERGE (source)-[nr:%s]->(target)
						ON CREATE SET nr.createdAt = datetime({ timezone: '+03:00' }),
						              nr.createdBy = $createdBy,
						              nr._created = true
						WITH CASE WHEN nr._created THEN 'RELATIONSHIP_WAS_CREATED' ELSE 'RELATIONSHIP_IS_EXISTS' END AS status, nr
						REMOVE nr._created
						RETURN status
						""",
				sourceNode.getLabel(), targetNode.getLabel(), sourceNode.getLabel(), targetNode.getLabel(),
				relationshipType, targetNode.getLabel(), relationshipType, relationshipType);

		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(), "targetNodeUuid",
				targetNode.getUuid(), "deletedBy", user != null ? user.getUuid() : "", "createdBy",
				user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while creating unique relationship between source node: "
				+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;

		return executeWriteMono(query, parameters, recordToStatusMapper(baseLogger), emptyResultError, errorMessage,
				baseLogger);
	};

	/*
	 * Get all toNode with a given relationshipType from a given fromNode.
	 * 
	 * (fromNode)-[r:relationshipType]->(toNode) RETURN toNode
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Flux<ExecutionResult<T>> getAllTargetNodesWithRelationshipType(
			S sourceNode, String relationshipType, Class<T> targetNodeClass, BaseLogger baseLogger) {
		baseLogger.trace(
				"Getting all target nodes with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<T>INPUT_PARAMETERS_ERROR_FLUX("getAllTargetNodesWithRelationshipType",
					"Input parameter relationshipType is null or empty");
		}
		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter targetNodeClass is null");
			return ExecutionResults.<T>INPUT_PARAMETERS_ERROR_FLUX("getAllTargetNodesWithRelationshipType",
					"Input parameter targetNodeClass is null");
		}

		String query = String.format("""
				MATCH (sourceNode:%s {uuid: $sourceNodeUuid})-[r:%s]->(targetNode) RETURN targetNode AS resultNode
				""",
				sourceNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " from source node: " + sourceNode.getUuid();
		String errorMessage = "Failed while query execution while finding target nodes with relationship type: "
				+ relationshipType + " from source node: " + sourceNode.getUuid();

		return executeReadFlux(query, parameters, recordToNodeMapper(targetNodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	/*
	 * Get unique toNode with a given relationshipType from a given fromNode.
	 * 
	 * Statuses:
	 * - NODE_IS_NOT_FOUND: The node was not found.
	 * - NODE_IS_FOUND: The node was found.
	 * 
	 * (fromNode)-[r:relationshipType]->(toNode) RETURN toNode
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<T>> getUniqueTargetNodeWithRelationshipType(
			S sourceNode, String relationshipType, Class<T> targetNodeClass, BaseLogger baseLogger) {
		baseLogger.trace(
				"Getting unique target node with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<T>INPUT_PARAMETERS_ERROR_MONO("getUniqueTargetNodeWithRelationshipType",
					"Input parameter relationshipType is null or empty");
		}

		String query = String.format(
				"""
						MATCH (sourceNode:%s {uuid: $sourceNodeUuid})-[r:%s]->(targetNode)
						WHERE NONE(label IN labels(targetNode) WHERE label ENDS WITH 'VERSIONED')
						RETURN
						CASE
							WHEN targetNode IS NULL THEN 'NODE_IS_NOT_FOUND'
							ELSE 'NODE_IS_FOUND'
						END AS status,
						targetNode AS resultNode
								""",
				sourceNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while finding target nodes with relationship type: "
				+ relationshipType + " from source node: " + sourceNode.getUuid();

		return executeReadMono(query, parameters, recordToNodeAndStatusMapper(targetNodeClass, baseLogger),
				emptyResultError, errorMessage, baseLogger);
	}

	/*
	 * Get all source nodes with a given relationshipType to a given targetNode.
	 * 
	 * Statuses:
	 * (sourceNode)-[r:relationshipType]->(targetNode) RETURN sourceNode
	 */
	protected <T extends AbstractNode, S extends AbstractNode> Flux<ExecutionResult<S>> getAllSourceNodesWithRelationshipType(
			T targetNode,
			String relationshipType, Class<S> sourceNodeClass, BaseLogger baseLogger) {
		baseLogger.trace(
				"Getting all source nodes with relationship type: {} to target node: {}",
				relationshipType, targetNode.getUuid());

		String query = String.format(
				"MATCH (targetNode:%s {uuid: $targetNodeUuid})<-[r:%s]-(sourceNode) RETURN sourceNode AS result",
				targetNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("targetNodeUuid", targetNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No source nodes found with relationship type: " + relationshipType
				+ " to target node: "
				+ targetNode.getUuid() + ": No source nodes found";
		String errorMessage = "Failed while query execution while finding source nodes with relationship type: "
				+ relationshipType + " to target node: " + targetNode.getUuid();

		return executeReadFlux(query, parameters, recordToNodeMapper(sourceNodeClass, baseLogger), emptyResultError,
				errorMessage, baseLogger);
	}

	/*
	 * Create unique outgoing relationships between a fromNode and a targetNodes.
	 * of
	 * toNodes.
	 * 
	 * Statuses:
	 * - RELATIONSHIP_WAS_CREATED: The relationship was created.
	 * 
	 * (fromNode)-[r:relationshipType]->(targetNodes)
	 * 
	 * @param fromNode
	 * 
	 * @param targetNodes
	 * 
	 * @param relationshipType
	 * 
	 * @param user
	 * 
	 * @return
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> createUniqueOutgoingRelationships(
			S sourceNode, List<T> targetNodes, Class<T> targetNodeClass, String relationshipType, AbstractUser user,
			BaseLogger baseLogger) {
		baseLogger.trace(
				"Creating unique outgoing relationships between source node: {} and target nodes: {}",
				sourceNode.getUuid(), targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("createUniqueOutgoingRelationships",
					"Input parameter relationshipType is null or empty");
		}
		return getAllTargetNodesWithRelationshipType(sourceNode, relationshipType, targetNodeClass, baseLogger)
				.collectList()
				.flatMap(results -> {

					List<T> existingNodes = results.stream()
							.map(result -> result.getNode())
							.filter(Objects::nonNull)
							.collect(Collectors.toList());

					List<T> toDeleteNodes = existingNodes.stream().filter(exNode -> targetNodes.stream()
							.noneMatch(toNode -> toNode.getUuid().equals(exNode.getUuid())))
							.collect(Collectors.toList());

					List<T> toCreateNodes = targetNodes.stream()
							.filter(toNode -> existingNodes.stream()
									.noneMatch(exNode -> exNode.getUuid().equals(toNode.getUuid())))
							.collect(Collectors.toList());

					Mono<ExecutionResult<AbstractNode>> firstNonBenignAfterDeletes = Flux
							.fromIterable(toDeleteNodes)
							.concatMap(node -> deleteSingleRelationship(sourceNode, node, relationshipType, user,
									baseLogger))
							.filter(er -> {

								if (er.getStatus() == ExecutionStatus.RELATIONSHIP_WAS_DELETED) {
									baseLogger.trace("Relationship was deleted: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS) {
									baseLogger.trace("Relationship does not exist: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.MORE_THAN_ONE_RELATIONSHIP_IS_FOUND) {
									baseLogger.trace("More than one relationship was found: {}", er.getNode());
								}
								return er.getStatus() != ExecutionStatus.RELATIONSHIP_WAS_DELETED;
							})
							.next();

					return firstNonBenignAfterDeletes.switchIfEmpty(Flux.fromIterable(toCreateNodes)
							.concatMap(node -> createSingleRelationship(sourceNode, node, relationshipType, user,
									baseLogger))
							.filter(er -> {
								if (er.getStatus() == ExecutionStatus.RELATIONSHIP_WAS_CREATED) {
									baseLogger.trace("Relationship was created: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS) {
									baseLogger.trace("Relationship does not exist: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.MORE_THAN_ONE_RELATIONSHIP_IS_FOUND) {
									baseLogger.trace("More than one relationship was found: {}", er.getNode());
								}
								return er.getStatus() != ExecutionStatus.RELATIONSHIP_WAS_CREATED;
							})
							.next()
							.switchIfEmpty(ExecutionResults
									.<AbstractNode>RELATIONSHIP_WAS_CREATED_MONO("Relationships were created")));
				});

	}

	/*
	 * Create unique incoming relationships between a list of toNodes and a list
	 * of
	 * toNodes.
	 * 
	 * (sourceNodes)-[r:relationshipType]->(toNode)
	 * 
	 * @param targetNode
	 * 
	 * @param sourceNodes
	 * 
	 * @param sourceNodeClass
	 * 
	 * @param relationshipType
	 * 
	 * @param user
	 * 
	 * @return
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode>> createUniqueIncomingRelationships(
			T targetNode,
			List<S> sourceNodes, Class<S> sourceNodeClass, String relationshipType, AbstractUser user,
			BaseLogger baseLogger) {
		baseLogger.trace(
				"Creating unique incoming relationships between target node: {} and source nodes: {}",
				targetNode.getUuid(), sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return ExecutionResults.<AbstractNode>INPUT_PARAMETERS_ERROR_MONO("createUniqueIncomingRelationships",
					"Input parameter relationshipType is null or empty");
		}
		return getAllSourceNodesWithRelationshipType(targetNode, relationshipType, sourceNodeClass, baseLogger)
				.collectList()
				.flatMap(results -> {

					List<S> existingNodes = results.stream()
							.map(result -> result.getNode())
							.filter(Objects::nonNull)
							.collect(Collectors.toList());

					List<S> toDeleteNodes = existingNodes.stream()
							.filter(exNode -> sourceNodes.stream()
									.noneMatch(toNode -> toNode.getUuid().equals(exNode.getUuid())))
							.collect(Collectors.toList());

					List<S> toCreateNodes = sourceNodes.stream()
							.filter(sNode -> existingNodes.stream()
									.noneMatch(exNode -> exNode.getUuid().equals(sNode.getUuid())))
							.collect(Collectors.toList());

					Mono<ExecutionResult<AbstractNode>> firstNonBenignAfterDeletes = Flux
							.fromIterable(toDeleteNodes)
							.concatMap(node -> deleteSingleRelationship(
									node,
									targetNode, relationshipType, user,
									baseLogger))
							.filter(er -> {

								if (er.getStatus() == ExecutionStatus.RELATIONSHIP_WAS_DELETED) {
									baseLogger.trace("Relationship was deleted: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS) {
									baseLogger.trace("Relationship does not exist: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.MORE_THAN_ONE_RELATIONSHIP_IS_FOUND) {
									baseLogger.trace("More than one relationship was found: {}", er.getNode());
								}
								return er.getStatus() != ExecutionStatus.RELATIONSHIP_WAS_DELETED;
							})
							.next();

					return firstNonBenignAfterDeletes.switchIfEmpty(Flux.fromIterable(toCreateNodes)
							.concatMap(node -> createSingleRelationship(node, targetNode, relationshipType, user,
									baseLogger))
							.filter(er -> {
								if (er.getStatus() == ExecutionStatus.RELATIONSHIP_WAS_CREATED) {
									baseLogger.trace("Relationship was created: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS) {
									baseLogger.trace("Relationship does not exist: {}", er.getNode());
								} else if (er.getStatus() == ExecutionStatus.MORE_THAN_ONE_RELATIONSHIP_IS_FOUND) {
									baseLogger.trace("More than one relationship was found: {}", er.getNode());
								}
								return er.getStatus() != ExecutionStatus.RELATIONSHIP_WAS_CREATED;
							})
							.next()
							.switchIfEmpty(ExecutionResults
									.<AbstractNode>RELATIONSHIP_WAS_CREATED_MONO("Relationships were created")));
				});
	}

}
