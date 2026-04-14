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
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.ExistsExecutionStatus;
import com.colligendis.server.database.result.ReadExecutionStatus;
import com.colligendis.server.database.result.ReadExecutionStatuses;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.database.result.WriteExecutionStatus;
import com.colligendis.server.database.result.WriteExecutionStatuses;
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

	protected <T extends AbstractNode, S extends ExecutionStatuses> Mono<ExecutionResult<T, S>> mapResultToExecutionResult(
			Record record,
			Class<T> nodeClass, BaseLogger baseLogger) {

		return Mono.just(ExecutionResult.<T, S>builder()
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
	protected <N extends AbstractNode, S extends ExecutionStatuses> Function<Record, ExecutionResult<N, S>> recordToNodeAndStatusMapper(
			Class<N> nodeClass, Function<String, S> statusResolver, BaseLogger baseLogger) {
		return (record) -> {
			try {
				N node = null;
				S status = null;

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
						return ExecutionResult.<N, S>builder()
								.error(error, status)
								.build();
					}
				}

				// Try to map status if present
				if (record.containsKey("status") && !record.get("status").isNull()) {
					try {
						String statusStr = record.get("status").asString(null);
						if (statusStr != null) {
							status = statusResolver.apply(statusStr);
						}
					} catch (Exception e) {
						baseLogger.traceOrange("Unrecognized 'status' in Neo4j record: {}",
								record.get("status").toString());
						// fallback: keep status UNKNOWN
					}
				}

				return ExecutionResult.<N, S>builder()
						.node(node)
						.status(status)
						.build();
			} catch (RuntimeException e) {
				S status = null;
				baseLogger.traceRed("Failed to map Neo4j record (resultNode + status) to {}: {}",
						nodeClass.getSimpleName(),
						e.getMessage(), e);
				AbstractServiceError error = new AbstractServiceError(
						"recordToNodeAndStatusMapper",
						"Failed to map Neo4j record (resultNode + status) to " + nodeClass.getSimpleName()
								+ ": " + e.getMessage(),
						Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
						e.getStackTrace(), e);
				return ExecutionResult.<N, S>builder()
						.error(error, status)
						.build();
			}
		};
	}

	/**
	 * Maps a Neo4j {@code Record} containing a "status" (execution status) field
	 * to an {@link ExecutionResult} with the execution status.
	 */
	protected <S extends ExecutionStatuses> Function<Record, ExecutionResult<AbstractNode, S>> recordToStatusMapper(
			Function<String, S> statusResolver, BaseLogger baseLogger) {
		return (record) -> {
			try {
				S status = null;

				// Try to map status if present
				if (record.containsKey("status") && !record.get("status").isNull()) {
					try {
						String statusStr = record.get("status").asString(null);
						if (statusStr != null) {
							status = statusResolver.apply(statusStr);
						}
					} catch (Exception e) {
						baseLogger.traceOrange("Unrecognized 'status' in Neo4j record: {}",
								record.get("status").toString());
						// fallback: keep status UNKNOWN
					}
				}

				return ExecutionResult.<AbstractNode, S>builder()
						.status(status)
						.build();
			} catch (RuntimeException e) {
				S status = null;
				baseLogger.traceRed("Failed to map Neo4j record (status) to ExecutionResult: {}",
						e.getMessage(), e);
				AbstractServiceError error = new AbstractServiceError(
						"recordToStatusMapper",
						"Failed to map Neo4j record (status) to ExecutionResult: " + e.getMessage(),
						Map.of("error", e.getClass().getName()),
						e.getStackTrace(), e);
				return ExecutionResult.<AbstractNode, S>builder()
						.error(error, status)
						.build();
			}
		};
	}

	/**
	 * Maps a Neo4j {@code Record} containing a "resultNode" (node) field
	 * to an {@link ExecutionResult} with the execution status.
	 */
	protected <N extends AbstractNode, S extends ExecutionStatuses> Function<Record, ExecutionResult<N, S>> recordToNodeMapper(
			Class<N> nodeClass,
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
						S status = null;
						baseLogger.traceRed("Failed to map 'resultNode' to {}: {}", nodeClass.getSimpleName(),
								e.getMessage(), e);
						// fallback to error result

						AbstractServiceError error = new AbstractServiceError(
								"recordToNodeMapper",
								"Failed to map Neo4j record field 'resultNode' to " + nodeClass.getSimpleName()
										+ ": " + e.getMessage(),
								Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
								e.getStackTrace(), e);
						return ExecutionResult.<N, S>builder()
								.error(error, status)
								.build();
					}
				}
				return ExecutionResult.<N, S>builder()
						.node(node)
						.build();
			} catch (RuntimeException e) {
				S status = null;
				baseLogger.traceRed("Failed to map Neo4j record (resultNode) to {}: {}", nodeClass.getSimpleName(),
						e.getMessage(), e);
				AbstractServiceError error = new AbstractServiceError(
						"recordToNodeMapper",
						"Failed to map Neo4j record (resultNode) to " + nodeClass.getSimpleName() + ": "
								+ e.getMessage(),
						Map.of("nodeClass", nodeClass.getName(), "error", e.getClass().getName()),
						e.getStackTrace(), e);
				return ExecutionResult.<N, S>builder()
						.error(error, status)
						.build();
			}
		};
	}

	// Executers for read operations
	@SuppressWarnings("unchecked")
	protected <T extends AbstractNode, S extends ReadExecutionStatuses> Mono<ExecutionResult<T, S>> executeReadMono(
			String query,
			Map<String, Object> parameters, Function<Record, ExecutionResult<T, S>> resultMapper,
			String emptyResultError,
			String errorMessage, BaseLogger baseLogger) {

		Mono<ExecutionResult<T, S>> empty = (Mono<ExecutionResult<T, S>>) (Mono<?>) Mono.just(
				ExecutionResult.<T, ReadExecutionStatus>builder()
						.error(
								new AbstractServiceError(
										"executeReadMono",
										"Empty result while reading mono: " + emptyResultError,
										Map.of("query", query, "parameters", parameters)),
								ReadExecutionStatus.EMPTY_RESULT)
						.build());

		return Flux.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> session.executeRead(tx -> Mono.fromDirect(tx.run(query, parameters))
						.flatMapMany(ReactiveResult::records)),
				ReactiveSession::close)
				.next()
				.map(resultMapper)
				.switchIfEmpty(empty)
				.doOnError(
						error -> baseLogger.traceRed("Failed while query execution while reading mono: {}",
								error.getMessage()))
				.doOnSuccess(
						result -> {
							baseLogger.trace("=== executeReadMono SUCCESS ===");
							baseLogger.trace("Query: {}", query);
							baseLogger.trace("Parameters: {}", parameters);
						})
				.onErrorResume(error -> (Mono<ExecutionResult<T, S>>) (Mono<?>) Mono.just(
						ExecutionResult.<T, ReadExecutionStatus>builder()
								.error(
										new AbstractServiceError(
												errorMessage + ": " + error.getMessage(),
												Map.of("query", query, "parameters", parameters),
												error.getStackTrace(),
												error),
										ReadExecutionStatus.INTERNAL_ERROR)
								.build()));
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

	@SuppressWarnings("unchecked")
	protected <T extends AbstractNode, S extends ReadExecutionStatuses> Flux<ExecutionResult<T, S>> executeReadFlux(
			String query,
			Map<String, Object> parameters,
			Function<Record, ExecutionResult<T, S>> resultMapper, String emptyResultError, String errorMessage,
			BaseLogger baseLogger) {

		Flux<ExecutionResult<T, S>> emptyExecutionResult = (Flux<ExecutionResult<T, S>>) (Flux<?>) Flux.just(
				ExecutionResult.<T, ReadExecutionStatus>builder()
						.error(
								new AbstractServiceError(
										"executeReadFlux",
										"Empty result while reading flux: " + emptyResultError,
										Map.of("query", query, "parameters", parameters)),
								ReadExecutionStatus.EMPTY_RESULT)
						.build());
		return Flux.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> session.executeRead(tx -> Mono.fromDirect(tx.run(query, parameters))
						.flatMapMany(ReactiveResult::records)),
				ReactiveSession::close)
				.map(resultMapper)
				.switchIfEmpty(emptyExecutionResult)
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
				.onErrorResume(error -> (Flux<ExecutionResult<T, S>>) (Flux<?>) Flux.just(
						ExecutionResult.<T, ReadExecutionStatus>builder()
								.error(
										new AbstractServiceError(
												errorMessage + ": " + error.getMessage(),
												Map.of("query", query, "parameters", parameters),
												error.getStackTrace(), error),
										ReadExecutionStatus.INTERNAL_ERROR)
								.build()));
	}

	// Executers for write operations

	@SuppressWarnings("unchecked")
	protected <T extends AbstractNode, S extends WriteExecutionStatuses> Mono<ExecutionResult<T, S>> executeWriteMono(
			String query,
			Map<String, Object> parameters,
			Function<Record, ExecutionResult<T, S>> resultMapper, String emptyResultError, String errorMessage,
			BaseLogger baseLogger) {

		Mono<ExecutionResult<T, S>> emptyExecutionResult = (Mono<ExecutionResult<T, S>>) (Mono<?>) Mono.just(
				ExecutionResult.<T, WriteExecutionStatus>builder()
						.error(
								new AbstractServiceError(
										"executeWriteMono",
										"Empty result while writing mono: " + emptyResultError,
										Map.of("query", query, "parameters", parameters)),
								WriteExecutionStatus.EMPTY_RESULT)
						.build());
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
				.switchIfEmpty(emptyExecutionResult)
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
					return (Mono<ExecutionResult<T, S>>) (Mono<?>) Mono.just(
							ExecutionResult.<T, WriteExecutionStatus>builder()
									.error(
											new AbstractServiceError(
													errorMessage + ": " + error.getMessage(),
													Map.of("query", query, "parameters", parameters),
													error.getStackTrace(), error),
											WriteExecutionStatus.INTERNAL_ERROR)
									.build());
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
	 * - NOT_EXISTS: The node does not exist.
	 * - EXISTS: The node exists.
	 * - MORE_THAN_ONE_FOUND: More than one node was found.
	 * 
	 * @param propertyName  The name of the property to check.
	 * @param propertyValue The value of the property to check.
	 * @param nodeLabel     The label of the node to check.
	 * @return Result with {@link ExistsExecutionStatus} from the query, or
	 *         {@link ExistsExecutionStatus#INTERNAL_ERROR} when input is
	 *         invalid
	 *         or the read fails.
	 */
	protected <T extends AbstractNode> Mono<ExecutionResult<T, ExistsExecutionStatus>> isNodeExistsByUniquePropertyValue(
			String propertyName,
			String propertyValue,
			String nodeLabel, Class<T> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Checking if node exists with property: {} value: {} node label: {}", propertyName,
				propertyValue,
				nodeLabel);

		if (propertyName == null || propertyName.isEmpty()) {
			return Mono.just(ExecutionResult.<T, ExistsExecutionStatus>builder()
					.error(new AbstractServiceError("isNodeExistsByUniquePropertyValue",
							"Input parameter propertyName is null or empty",
							Map.of("propertyName", propertyName)), ExistsExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (propertyValue == null || propertyValue.isEmpty()) {
			return Mono.just(ExecutionResult.<T, ExistsExecutionStatus>builder()
					.error(new AbstractServiceError("isNodeExistsByUniquePropertyValue",
							"Input parameter propertyValue is null or empty",
							Map.of("propertyValue", propertyValue)), ExistsExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (nodeLabel == null || nodeLabel.isEmpty()) {
			return Mono.just(ExecutionResult.<T, ExistsExecutionStatus>builder()
					.error(new AbstractServiceError("isNodeExistsByUniquePropertyValue",
							"Input parameter nodeLabel is null or empty",
							Map.of("nodeLabel", nodeLabel)), ExistsExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		final String query = String.format(
				"MATCH (node:%s {%s: $propertyValue}) WITH node LIMIT 2 " +
						"WITH collect(node) AS nodes " +
						"RETURN " +
						"CASE WHEN size(nodes) = 1 THEN nodes[0] ELSE NULL END AS resultNode, " +
						"CASE " +
						"WHEN size(nodes) = 0 THEN 'NOT_EXISTS' " +
						"WHEN size(nodes) = 1 THEN 'EXISTS' " +
						"ELSE 'MORE_THAN_ONE_FOUND' " +
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

		return executeReadMono(query, parameters,
				recordToNodeAndStatusMapper(nodeClass, ExistsExecutionStatus::valueOf, baseLogger),
				emptyResultError,
				errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while checking if node {} exists with property: {} value: {} node label: {}",
									nodeClass.getSimpleName(), propertyName, propertyValue, nodeLabel);
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to check if node {} exists with property: {} value: {} node label: {}",
									nodeClass.getSimpleName(), propertyName, propertyValue, nodeLabel);
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error while checking if node {} exists with property: {} value: {} node label: {}",
									nodeClass.getSimpleName(), propertyName, propertyValue, nodeLabel);
							return Mono.just(result);
						case EXISTS:
							baseLogger.traceGreen("Node {} exists with property: {} value: {} node label: {}",
									nodeClass.getSimpleName(), propertyName,
									propertyValue, nodeLabel);
							return Mono.just(result);
						case NOT_EXISTS:
							baseLogger.traceOrange("Node {} does not exist with property: {} value: {} node label: {}",
									nodeClass.getSimpleName(), propertyName, propertyValue, nodeLabel);
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one node {} found with property: {} value: {} node label: {}",
									nodeClass.getSimpleName(), propertyName, propertyValue, nodeLabel);
							return Mono.just(result);
					}
					return Mono.just(result);
				});
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
	protected <N extends AbstractNode> Mono<ExecutionResult<N, UpdateExecutionStatus>> updateNodeProperties(
			N node,
			AbstractUser user, Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Updating node properties with uuid: {} and label: {}", node.getUuid(), node.getLabel());

		if (node.getUuid() == null || node.getUuid().isEmpty()) {
			return Mono.just(ExecutionResult.<N, UpdateExecutionStatus>builder()
					.error(new AbstractServiceError("updateNodeProperties",
							"Input parameter node.uuid is null or empty",
							Map.of("node", node)), UpdateExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format(
				"OPTIONAL MATCH (node:%s {uuid: $nodeUuid}) " +
						"WITH node, $properties AS propsMap " +
						"WITH node, propsMap, " +
						"CASE WHEN node IS NULL THEN 'NOT_FOUND' ELSE 'OK' END AS nodeStatus, " +
						"CASE WHEN node IS NULL THEN [] ELSE [k IN keys(propsMap) WHERE node[k] IS NULL OR node[k] <> propsMap[k]] END AS diffKeys "
						+
						"CALL (node, nodeStatus, diffKeys, propsMap) { " +
						// --- NODE_IS_NOT_FOUND ---
						"WITH node, nodeStatus WHERE nodeStatus = 'NOT_FOUND' RETURN NULL AS resultNode, 'NOT_FOUND' AS status "
						+
						"UNION ALL " +
						// --- NODE_NOTHING_TO_UPDATE ---
						"WITH node, diffKeys, nodeStatus WHERE nodeStatus = 'OK' AND size(diffKeys) = 0 RETURN node AS resultNode, 'NOTHING_TO_UPDATE' AS status "
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
						"RETURN newNode AS resultNode, 'WAS_UPDATED' AS status " +
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
				recordToNodeAndStatusMapper(nodeClass, UpdateExecutionStatus::valueOf, baseLogger),
				emptyResultError, errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while updating node {} properties with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed("Failed to update node {} properties with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed("Failed to update node {} properties with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case WAS_UPDATED:
							baseLogger.trace("Node {} properties were updated with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceRed("Node {} was not found with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case NOTHING_TO_UPDATE:
							baseLogger.traceRed("Node {} has nothing to update with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/**
	 * Creates a new node in the database.
	 * Statuses:
	 * - NOT_CREATED: The node was not created.
	 * - WAS_CREATED: The node was created.
	 * 
	 * @param node       The node to create.
	 * @param user       The user who is creating the node.
	 * @param nodeClass  The class of the node to create.
	 * @param baseLogger The base logger to use.
	 * @return The execution result containing the created node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N, CreateNodeExecutionStatus>> createNode(N node,
			AbstractUser user,
			Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Creating node with label: {}", node.getLabel());

		if (node.getUuid() != null && !node.getUuid().isEmpty()) {
			return Mono.just(ExecutionResult.<N, CreateNodeExecutionStatus>builder()
					.error(new AbstractServiceError("createNode",
							"Input parameter node.uuid is not null or empty",
							Map.of("node", node)), CreateNodeExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format(
				"""
						CREATE (node:%s {uuid: randomUUID(), createdAt: datetime({ timezone: '+03:00' }), createdBy: $createdBy %s})
						RETURN node AS resultNode,
						CASE
							WHEN node IS NULL
							THEN 'NOT_CREATED'
							ELSE 'WAS_CREATED'
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

		return executeWriteMono(query, parameters,
				recordToNodeAndStatusMapper(nodeClass, CreateNodeExecutionStatus::valueOf, baseLogger),
				emptyResultError,
				errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed("Empty result while creating {} : {}", nodeClass.getSimpleName(),
									result.getNode());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed("Failed to create node {} : {}", nodeClass.getSimpleName(),
									result.getStatus());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed("Failed to create node {} : {}", nodeClass.getSimpleName(),
									result.getStatus());
							return Mono.just(result);
						case WAS_CREATED:
							baseLogger.trace("Node {} was created: {}", nodeClass.getSimpleName(), result.getNode());
							return Mono.just(result);
						case NOT_CREATED:
							baseLogger.traceRed("Node {} was not created: {}", nodeClass.getSimpleName(),
									result.getNode());
							return Mono.just(result);
						case NODE_ALREADY_EXISTS:
							baseLogger.traceRed("Skipping this status: {} for node {} : {}", result.getStatus(),
									nodeClass.getSimpleName(), result.getNode());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/**
	 * Deletes a node in the database.
	 * Statuses:
	 * - NOT_FOUND: The node was not found.
	 * - WAS_DELETED: The node was deleted.
	 * - MORE_THAN_ONE_FOUND: More than one node was found.
	 * 
	 * @param node       The node to delete.
	 * @param user       The user who is deleting the node.
	 *                   protected <N extends AbstractNode> Mono<ExecutionResult<N>>
	 *                   deleteNode(N node,
	 * @param nodeClass  The class of the node to delete.
	 * @param baseLogger The base logger to use.
	 * @return The execution result containing the deleted node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N, DeleteExecutionStatus>> deleteNode(N node,
			AbstractUser user, Class<N> nodeClass, BaseLogger baseLogger) {

		if (node.getUuid() == null || node.getUuid().isEmpty()) {
			return Mono.just(ExecutionResult.<N, DeleteExecutionStatus>builder()
					.error(new AbstractServiceError("deleteNode",
							"Input parameter node.uuid is null or empty",
							Map.of("node", node)), DeleteExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		// String query = String.format(
		// "OPTIONAL MATCH (n {uuid: $uuid})" +
		// "WITH n, labels(n) AS oldLabels " +
		// "CALL (n, oldLabels) { " +
		// "WITH n, oldLabels " +
		// "REMOVE n:$(oldLabels) " +
		// "WITH n, oldLabels " +
		// "WITH n, [label IN oldLabels | label + '_DELETED'] AS newLabels " +
		// "SET n:$(newLabels) " +
		// "SET n.deletedAt = datetime({ timezone: '+03:00' }), n.deletedBy = $deletedBy
		// " +
		// "RETURN n AS updatedNode } " +
		// "RETURN " +
		// "updatedNode AS resultNode, " +
		// "CASE " +
		// "WHEN n IS NULL THEN 'IS_NOT_FOUND' " +
		// "ELSE 'WAS_DELETED' " +
		// "END AS status");

		String query = String.format(
				"""
											OPTIONAL MATCH (n {uuid: $uuid})
						WITH collect(n) AS nodes, count(n) AS cnt

						CALL {
						    WITH nodes, cnt
						    WHERE cnt = 1
						    WITH nodes[0] AS n, labels(nodes[0]) AS oldLabels
						    REMOVE n:$(oldLabels)
						    WITH n, oldLabels
						    WITH n, [label IN oldLabels | label + '_DELETED'] AS newLabels
						    SET n:$(newLabels)
						    SET n.deletedAt = datetime({ timezone: '+03:00' }),
						        n.deletedBy = $deletedBy
						    RETURN n AS updatedNode
						}

						RETURN
						    CASE
						        WHEN cnt = 0 THEN 'NOT_FOUND'
						        WHEN cnt > 1 THEN 'MORE_THAN_ONE_FOUND'
						        ELSE 'WAS_DELETED'
						    END AS status,
						    CASE
						        WHEN cnt = 1 THEN nodes[0]
						        ELSE NULL
						    END AS resultNode
											""");

		Map<String, Object> parameters = Map.of("uuid", node.getUuid(), "deletedBy",
				user != null ? user.getUuid() : "");

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No node deleted with uuid: " + node.getUuid() + ": No node deleted";
		String errorMessage = "Failed while query execution while deleting node with uuid: " + node.getUuid();

		return executeWriteMono(query, parameters,
				recordToNodeAndStatusMapper(nodeClass, DeleteExecutionStatus::valueOf, baseLogger), emptyResultError,
				errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed("Empty result while deleting node {} with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed("Failed to delete node {} with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed("Failed to delete node {} with uuid: {} and label: {}",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceOrange("Node {} with uuid: {} and label: {} was not found",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case WAS_DELETED:
							baseLogger.traceGreen("Node {} with uuid: {} and label: {} was deleted",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed("More than one node {} with uuid: {} and label: {} was found",
									nodeClass.getSimpleName(), node.getUuid(), node.getLabel());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/**
	 * Finds a node by its unique property value.
	 * Statuses:
	 * - NOT_FOUND: The node was not found.
	 * - FOUND: The node was found.
	 * - MORE_THAN_ONE_FOUND: More than one node was found.
	 * 
	 * @param propertyName  The name of the property to find the node by.
	 * @param propertyValue The value of the property to find the node by.
	 * @param nodeLabel     The label of the node to find.
	 * @param nodeClass     The class of the node to find.
	 * @param baseLogger    The base logger to use.
	 * @return The execution result containing the found node and status.
	 */
	protected <N extends AbstractNode> Mono<ExecutionResult<N, FindExecutionStatus>> findNodeByUniquePropertyValue(
			String propertyName,
			Object propertyValue,
			String nodeLabel, Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Finding node with unique property: {} value: {} node label: {}", propertyName, propertyValue,
				nodeLabel);

		if (propertyName == null || propertyName.isEmpty()) {
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findNodeByUniquePropertyValue",
							"Input parameter propertyName is null or empty",
							Map.of("propertyName", propertyName)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		if (propertyValue == null) {
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findNodeByUniquePropertyValue",
							"Input parameter propertyValue is null",
							Map.of("propertyValue", propertyValue)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		if (nodeLabel == null || nodeLabel.isEmpty()) {
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findNodeByUniquePropertyValue",
							"Input parameter nodeLabel is null or empty",
							Map.of("nodeLabel", nodeLabel)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
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
						"WHEN size(nodes) = 0 THEN 'NOT_FOUND' " +
						"WHEN size(nodes) = 1 THEN 'FOUND' " +
						"ELSE 'MORE_THAN_ONE_FOUND' " +
						"END AS status",
				nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No node found with property: " + propertyName + " value: " + propertyValue
				+ " node label: " + nodeLabel + ": No node found";
		String errorMessage = "Failed while query execution while finding node with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadMono(query, parameters,
				recordToNodeAndStatusMapper(nodeClass, FindExecutionStatus::valueOf, baseLogger), emptyResultError,
				errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while finding node {} : {} by unique property value: {} and node label: {}",
									nodeClass.getSimpleName(),
									result.getNode(), propertyName, propertyValue, nodeLabel);
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to find node {} : {} by unique property value: {} and node label: {}",
									nodeClass.getSimpleName(),
									result.getNode(), propertyName, propertyValue, nodeLabel,
									result.getStatus());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Failed to find node {} : {} by unique property value: {} and node label: {}",
									nodeClass.getSimpleName(),
									result.getNode(), propertyName, propertyValue, nodeLabel,
									result.getStatus());
							return Mono.just(result);
						case FOUND:
							baseLogger.traceGreen(
									"Node {} was found: {} by unique property value: {} and node label: {}",
									nodeClass.getSimpleName(), result.getNode(), propertyName, propertyValue,
									nodeLabel);
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"Node {} was not found: {} by unique property value: {} and node label: {}",
									nodeClass.getSimpleName(),
									result.getNode(), propertyName, propertyValue, nodeLabel);
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one node {} was found: {} by unique property value: {} and node label: {}",
									nodeClass.getSimpleName(),
									result.getNode(), propertyName, propertyValue, nodeLabel,
									result.getStatus());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
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
	protected <N extends AbstractNode> Mono<ExecutionResult<N, FindExecutionStatus>> findNodeByUuid(String uuid,
			String nodeLabel, Class<N> nodeClass, BaseLogger baseLogger) {
		baseLogger.trace("Finding node with uuid: {} and label: {}", uuid, nodeLabel);
		if (uuid == null || uuid.isEmpty()) {
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findNodeByUuid",
							"Input parameter uuid is null or empty",
							Map.of("uuid", uuid)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		return findNodeByUniquePropertyValue("uuid", uuid, nodeLabel, nodeClass, baseLogger);
	}

	/**
	 * Finds a unique node by its property value and target node.
	 * Statuses:
	 * - NOT_FOUND: The node was not found.
	 * - FOUND: The node was found.
	 * - MORE_THAN_ONE_FOUND: More than one node was found.
	 * 
	 * @param propertyName     The name of the property to find the node by.
	 * @param propertyValue    The value of the property to find the node by.
	 * @param nodeLabel        The label of the node to find.
	 * @param nodeClass        The class of the node to find.
	 * @param targetNode       The target node to find the node by.
	 * @param relationshipType The type of the relationship to find the node by.
	 * @param baseLogger       The base logger to use.
	 * @return The execution result containing the found node and status.
	 */
	protected <N extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<N, FindExecutionStatus>> findUniqueNodeByPropertyValueAndTargetNode(
			String propertyName, Object propertyValue, String nodeLabel, Class<N> nodeClass, T targetNode,
			String relationshipType, BaseLogger baseLogger) {
		baseLogger.trace(
				"Finding unique node with property: {} value: {} node label: {} connected to node: {} with relationship type: {}",
				propertyName, propertyValue, nodeLabel, targetNode.getUuid(), relationshipType);

		if (propertyName == null || propertyName.isEmpty()) {
			baseLogger.traceRed("Input parameter propertyName is null or empty");
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findUniqueNodeByPropertyValueAndTargetNode",
							"Input parameter propertyName is null or empty",
							Map.of("propertyName", propertyName)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (propertyValue == null) {
			baseLogger.traceRed("Input parameter propertyValue is null");
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findUniqueNodeByPropertyValueAndTargetNode",
							"Input parameter propertyValue is null",
							Map.of("propertyValue", propertyValue)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findUniqueNodeByPropertyValueAndTargetNode",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (nodeLabel == null || nodeLabel.isEmpty()) {
			baseLogger.traceRed("Input parameter nodeLabel is null or empty");
			return Mono.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findUniqueNodeByPropertyValueAndTargetNode",
							"Input parameter nodeLabel is null or empty",
							Map.of("nodeLabel", nodeLabel)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
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
						"WHEN size(nodes) = 0 THEN 'NOT_FOUND' " +
						"WHEN size(nodes) = 1 THEN 'FOUND' " +
						"ELSE 'MORE_THAN_ONE_FOUND' " +
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

		return executeReadMono(query, parameters,
				recordToNodeAndStatusMapper(nodeClass, FindExecutionStatus::valueOf, baseLogger), emptyResultError,
				errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed("Empty result while finding {} : {}", nodeClass.getSimpleName(),
									result.getNode());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed("Failed to find {} : {}", nodeClass.getSimpleName(),
									result.getStatus());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed("Failed to find {} : {}", nodeClass.getSimpleName(),
									result.getStatus());
							return Mono.just(result);
						case FOUND:
							baseLogger.trace("{} was found: {}", nodeClass.getSimpleName(), result.getNode());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceRed("{} was not found: {}", nodeClass.getSimpleName(), result.getNode());
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed("More than one {} was found: {}", nodeClass.getSimpleName(),
									result.getNode());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/**
	 * Finds nodes by their property value.
	 * Statuses:
	 * - NOT_FOUND: The nodes were not found.
	 * - FOUND: The nodes were found.
	 * 
	 * @param propertyName  The name of the property to find the nodes by.
	 * @param propertyValue The value of the property to find the nodes by.
	 * @param nodeLabel     The label of the nodes to find.
	 * @param nodeClass     The class of the nodes to find.
	 * @param baseLogger    The base logger to use.
	 * @return The execution result containing the found nodes and status.
	 */
	protected <N extends AbstractNode> Flux<ExecutionResult<N, FindExecutionStatus>> findNodesByPropertyValue(
			String propertyName, Object propertyValue, String nodeLabel, Class<N> nodeClass, BaseLogger baseLogger) {

		baseLogger.trace("Finding nodes with property: {} value: {} node label: {}", propertyName, propertyValue,
				nodeLabel);

		if (propertyName == null || propertyName.isEmpty()) {
			baseLogger.traceRed("Input parameter propertyName is null or empty");
			return Flux.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findNodesByPropertyValue",
							"Input parameter propertyName is null or empty",
							Map.of("propertyName", propertyName)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (propertyValue == null) {
			baseLogger.traceRed("Input parameter propertyValue is null");
			return Flux.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findNodesByPropertyValue",
							"Input parameter propertyValue is null",
							Map.of("propertyValue", propertyValue)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (nodeLabel == null || nodeLabel.isEmpty()) {
			baseLogger.traceRed("Input parameter nodeLabel is null or empty");
			return Flux.just(ExecutionResult.<N, FindExecutionStatus>builder()
					.error(new AbstractServiceError("findNodesByPropertyValue",
							"Input parameter nodeLabel is null or empty",
							Map.of("nodeLabel", nodeLabel)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		final String query = String.format(
				"""
						OPTIONAL MATCH (node:%s {%s: $propertyValue})
						WITH count(node) AS cnt, collect(node) AS nodes
						RETURN
						    CASE
						        WHEN cnt = 0 THEN 'NOT_FOUND'
						        ELSE 'FOUND'
						    END AS status,
						    nodes AS resultNodes
						""",
				nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for property: " + propertyName + " value: "
				+ propertyValue
				+ " node label: " + nodeLabel + ": No nodes found";
		String errorMessage = "Failed while query execution while finding nodes with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadFlux(query, parameters,
				recordToNodeAndStatusMapper(nodeClass, FindExecutionStatus::valueOf, baseLogger), emptyResultError,
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
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode, ExistsExecutionStatus>> isRelationshipExists(
			S sourceNode, T targetNode, String relationshipType, BaseLogger baseLogger) {

		baseLogger.trace(
				"Checking if relationship exists between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, ExistsExecutionStatus>builder()
					.error(new AbstractServiceError("isRelationshipExists",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), ExistsExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format("""
				OPTIONAL MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]-(target:%s {uuid: $targetNodeUuid})
				WITH count(r) AS relCount
				RETURN
				    CASE
				        WHEN relCount = 0 THEN 'NOT_EXISTS'
				        WHEN relCount = 1 THEN 'EXISTS'
				        ELSE 'MORE_THAN_ONE_FOUND'
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
				recordToStatusMapper(ExistsExecutionStatus::valueOf, baseLogger), emptyResultError, errorMessage,
				baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while checking if relationship exists between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to check if relationship exists between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error while checking if relationship exists between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case NOT_EXISTS:
							baseLogger.traceOrange(
									"Relationship does not exist between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case EXISTS:
							baseLogger.traceGreen(
									"Relationship exists between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one relationship exists between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/**
	 * Check if any outgoing relationships exists from a node with a given
	 * relationship type.
	 * 
	 * Statuses:
	 * - NOT_EXISTS: The relationships do not exist.
	 * - EXISTS: The relationships exist.
	 * - MORE_THAN_ONE_FOUND: More than one relationship was found.
	 * 
	 * @param sourceNode       The source node.
	 * 
	 * @param relationshipType The relationship type.
	 * 
	 * @param baseLogger       The base logger.
	 * 
	 * @return The execution result containing the relationships and status.
	 */
	protected <S extends AbstractNode> Mono<ExecutionResult<AbstractNode, ExistsExecutionStatus>> isAnyOutgoingRelationshipsExists(
			S sourceNode,
			String relationshipType, BaseLogger baseLogger) {

		baseLogger.trace(
				"Checking if any outgoing relationships exists from source node: {} with relationship type: {}",
				sourceNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, ExistsExecutionStatus>builder()
					.error(new AbstractServiceError("isAnyOutgoingRelationshipsExists",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), ExistsExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format("""
				MATCH (source:%s {uuid: $sourceNodeUuid})
				OPTIONAL MATCH (source)-[r:%s]->(target)
				WITH count(r) AS relCount
				RETURN
				    CASE
				        WHEN relCount = 0 THEN 'NOT_EXISTS'
				        WHEN relCount = 1 THEN 'EXISTS'
				        ELSE 'MORE_THAN_ONE_FOUND'
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

		return executeReadMono(query, parameters, recordToStatusMapper(ExistsExecutionStatus::valueOf, baseLogger),
				emptyResultError, errorMessage,
				baseLogger);
	}

	/*
	 * Check if any incoming relationships exists to a node with a given
	 * relationship type.
	 * 
	 * Statuses:
	 * - NOT_EXISTS: The relationships do not exist.
	 * - EXISTS: The relationships exist.
	 * - MORE_THAN_ONE_FOUND: More than one relationship was found.
	 * 
	 * @param targetNode The target node.
	 * 
	 * @param relationshipType The relationship type.
	 * 
	 * @param baseLogger The base logger.
	 * 
	 * @return The execution result containing the relationships and status.
	 */
	protected <T extends AbstractNode> Mono<ExecutionResult<AbstractNode, ExistsExecutionStatus>> isIncomingRelationshipsExists(
			T targetNode,
			String relationshipType, BaseLogger baseLogger) {

		baseLogger.trace(
				"Checking if any incoming relationships exists to target node: {} with relationship type: {}",
				targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, ExistsExecutionStatus>builder()
					.error(new AbstractServiceError("isIncomingRelationshipsExists",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), ExistsExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format("""
				MATCH (target:%s {uuid: $targetNodeUuid})
				OPTIONAL MATCH (source)-[r:%s]->(target)
				WITH count(r) AS relCount
				RETURN
				    CASE
				        WHEN relCount = 0 THEN 'NOT_EXISTS'
				        WHEN relCount = 1 THEN 'EXISTS'
				        ELSE 'MORE_THAN_ONE_FOUND'
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
				recordToStatusMapper(ExistsExecutionStatus::valueOf, baseLogger), emptyResultError, errorMessage,
				baseLogger);
	}

	/*
	 * Create a single relationship between two nodes. There are many relationships
	 * from one node to anothers with the same relationship type.
	 * 
	 * Statuses:
	 * - SOURCE_OR_TARGET_NODE_IS_NOT_FOUND: The source or target node is not found.
	 * - IS_ALREADY_EXISTS: The relationship already exists.
	 * - WAS_CREATED: The relationship was created.
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
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> createSingleRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {

		baseLogger.trace(
				"Creating single relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
					.error(new AbstractServiceError("createSingleRelationship",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)),
							CreateRelationshipExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format(
				"""
						OPTIONAL MATCH (source:%s {uuid: $sourceNodeUuid})
						OPTIONAL MATCH (target:%s {uuid: $targetNodeUuid})
						WITH source, target,
						     CASE WHEN source IS NULL OR target IS NULL THEN 'SOURCE_OR_TARGET_NODE_IS_NOT_FOUND' ELSE 'OK' END AS nodeStatus
						OPTIONAL MATCH (source)-[r:%s]->(target)
						WITH source, target, r, nodeStatus,
						     CASE
						         WHEN nodeStatus = 'SOURCE_OR_TARGET_NODE_IS_NOT_FOUND' THEN 'SOURCE_OR_TARGET_NODE_IS_NOT_FOUND'
						         WHEN r IS NOT NULL THEN 'IS_ALREADY_EXISTS'
						         ELSE 'WAS_CREATED'
						     END AS status
						FOREACH (_ IN CASE WHEN status = 'WAS_CREATED' THEN [1] ELSE [] END |
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

		return executeWriteMono(query, parameters,
				recordToStatusMapper(CreateRelationshipExecutionStatus::valueOf, baseLogger),
				emptyResultError, errorMessage,
				baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while creating single relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									relationshipType, result.getNode());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to create single relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									relationshipType, result.getNode());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Failed to create single relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									relationshipType, result.getNode());
							return Mono.just(result);
						case IS_ALREADY_EXISTS:
							baseLogger.traceRed(
									"Single relationship between source node {} : {} and target node {} : {} with relationship type {} is already exists: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									relationshipType, result.getNode());
							return Mono.just(result);
						case WAS_CREATED:
							baseLogger.trace(
									"Single relationship between source node {} : {} and target node {} : {} with relationship type {} was created: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									relationshipType, result.getNode());
							return Mono.just(result);
						case SOURCE_OR_TARGET_NODE_IS_NOT_FOUND:
							baseLogger.traceRed(
									"Source or target node is not found for single relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									relationshipType, result.getNode());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/*
	 * Delete a single relationship between two nodes.
	 * 
	 * Statuses:
	 * - IS_NOT_FOUND: The relationship does not exist.
	 * - WAS_DELETED: The relationship was deleted.
	 * - MORE_THAN_ONE_FOUND: More than one relationship was found.
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
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> deleteSingleRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {

		baseLogger.trace(
				"Deleting single relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, DeleteExecutionStatus>builder()
					.error(new AbstractServiceError("deleteSingleRelationship",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), DeleteExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
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
				        WHEN size(rels) = 0 THEN 'NOT_FOUND'
				        WHEN size(rels) = 1 THEN 'WAS_DELETED'
				        ELSE 'MORE_THAN_ONE_FOUND'
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

		return executeWriteMono(query, parameters, recordToStatusMapper(DeleteExecutionStatus::valueOf, baseLogger),
				emptyResultError, errorMessage,
				baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while deleting single relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to delete single relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Failed to delete single relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"Single relationship between source node {} : {} and target node {} : {} with relationship type {} was not found: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
						case WAS_DELETED:
							baseLogger.traceGreen(
									"Single relationship between source node {} : {} and target node {} : {} with relationship type {} was deleted: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one single relationship between source node {} : {} and target node {} : {} with relationship type {} was found: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/*
	 * Delete all outgoing relationships with RelationshipType from a node
	 * 
	 * Statuses:
	 * - IS_NOT_FOUND: The relationship does not exist.
	 * - WAS_DELETED: The relationship was deleted.
	 * 
	 */
	protected <S extends AbstractNode> Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> deleteAllOutgoingRelationshipsWithRelationshipType(
			S sourceNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {
		baseLogger.trace(
				"Deleting all outgoing relationships with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, DeleteExecutionStatus>builder()
					.error(new AbstractServiceError("deleteAllOutgoingRelationshipsWithRelationshipType",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)),
							DeleteExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
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
				        WHEN count(r) > 0 THEN 'WAS_DELETED'
				        ELSE 'IS_NOT_FOUND'
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

		return executeWriteMono(query, parameters, recordToStatusMapper(DeleteExecutionStatus::valueOf, baseLogger),
				emptyResultError, errorMessage,
				baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while deleting all outgoing relationships with relationship type {} from source node {} : {}",
									relationshipType, sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to delete all outgoing relationships with relationship type {} from source node {} : {}",
									relationshipType, sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Failed to delete all outgoing relationships with relationship type {} from source node {} : {}",
									relationshipType, sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"All outgoing relationships with relationship type {} from source node {} : {} were not found",
									relationshipType, sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case WAS_DELETED:
							baseLogger.traceGreen(
									"All outgoing relationships with relationship type {} from source node {} : {} were deleted",
									relationshipType, sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one outgoing relationship with relationship type {} from source node {} : {} was found",
									relationshipType, sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/*
	 * Delete all incoming relationships with RelationshipType to a node
	 * 
	 * Statuses:
	 * - IS_NOT_FOUND: The relationship does not exist.
	 * - WAS_DELETED: The relationship was deleted.
	 * 
	 */
	protected <T extends AbstractNode> Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> deleteAllIncomingRelationshipsWithRelationshipType(
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {

		baseLogger.trace(
				"Deleting all incoming relationships with relationship type: {} to target node: {}",
				relationshipType, targetNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, DeleteExecutionStatus>builder()
					.error(new AbstractServiceError("deleteAllIncomingRelationshipsWithRelationshipType",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), DeleteExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
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
				        WHEN count(r) > 0 THEN 'WAS_DELETED'
				        ELSE 'IS_NOT_FOUND'
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

		return executeWriteMono(query, parameters, recordToStatusMapper(DeleteExecutionStatus::valueOf, baseLogger),
				emptyResultError, errorMessage,
				baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while deleting all incoming relationships with relationship type {} to target node {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to delete all incoming relationships with relationship type {} to target node {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Failed to delete all incoming relationships with relationship type {} to target node {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"All incoming relationships with relationship type {} to target node {} : {} were not found",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case WAS_DELETED:
							baseLogger.traceGreen(
									"All incoming relationships with relationship type {} to target node {} : {} were deleted",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one incoming relationship with relationship type {} to target node {} : {} was found",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
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
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> createUniqueTargetedRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user, BaseLogger baseLogger) {
		baseLogger.trace(
				"Creating unique relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
					.error(new AbstractServiceError("createUniqueTargetedRelationship",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)),
							CreateRelationshipExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format(
				"""
						OPTIONAL MATCH (source:%s {uuid: $sourceNodeUuid})
						OPTIONAL MATCH (target:%s {uuid: $targetNodeUuid})
						WITH source, target
						WHERE source IS NULL OR target IS NULL
						RETURN 'SOURCE_OR_TARGET_NODE_IS_NOT_FOUND' AS status
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
						WITH CASE WHEN nr._created THEN 'WAS_CREATED' ELSE 'IS_ALREADY_EXISTS' END AS status, nr
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

		return executeWriteMono(query, parameters,
				recordToStatusMapper(CreateRelationshipExecutionStatus::valueOf, baseLogger), emptyResultError,
				errorMessage,
				baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while creating unique relationship between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to create unique relationship between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Failed to create unique relationship between source node {} : {} and target node {} : {} with relationship type: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType);
							return Mono.just(result);
						case IS_ALREADY_EXISTS:
							baseLogger.traceOrange(
									"Unique relationship between source node {} : {} and target node {} : {} with relationship type {} is already exists: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
						case WAS_CREATED:
							baseLogger.traceGreen(
									"Unique relationship between source node {} : {} and target node {} : {} with relationship type {} was created: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
						case SOURCE_OR_TARGET_NODE_IS_NOT_FOUND:
							baseLogger.traceRed(
									"Source or target node is not found for unique relationship between source node {} : {} and target node {} : {} with relationship type {}: {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), relationshipType,
									result.getNode());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	};

	/*
	 * Get all target nodes with a given relationshipType from a given source node.
	 * 
	 * Statuses:
	 * - NOT_FOUND: The target node was not found.
	 * - FOUND: The target node was found.
	 * 
	 * (sourceNode)-[r:relationshipType]->(targetNode) RETURN targetNode
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Flux<ExecutionResult<T, FindExecutionStatus>> getAllTargetNodesWithRelationshipType(
			S sourceNode, String relationshipType, Class<T> targetNodeClass, BaseLogger baseLogger) {
		baseLogger.trace(
				"Getting all target nodes with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Flux.just(ExecutionResult.<T, FindExecutionStatus>builder()
					.error(new AbstractServiceError("getAllTargetNodesWithRelationshipType",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		if (targetNodeClass == null) {
			baseLogger.traceRed("Input parameter targetNodeClass is null");
			return Flux.just(ExecutionResult.<T, FindExecutionStatus>builder()
					.error(new AbstractServiceError("getAllTargetNodesWithRelationshipType",
							"Input parameter targetNodeClass is null",
							Map.of("targetNodeClass", targetNodeClass)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format("""
				MATCH (sourceNode:%s {uuid: $sourceNodeUuid})
				OPTIONAL MATCH (sourceNode)-[r:%s]->(targetNode)
				WITH collect(targetNode) AS nodes
				WITH [n IN nodes WHERE n IS NOT NULL AND NONE(label IN labels(n)
				      WHERE label ENDS WITH '_VERSIONED' OR label ENDS WITH '_DELETED')] AS filtered
				RETURN
				  CASE
				    WHEN size(filtered) = 0 THEN 'NOT_FOUND'
				    ELSE 'FOUND'
				  END AS status,
				  filtered AS resultNode
				""",
				sourceNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " from source node: " + sourceNode.getUuid();
		String errorMessage = "Failed while query execution while finding target nodes with relationship type: "
				+ relationshipType + " from source node: " + sourceNode.getUuid();

		return executeReadFlux(query, parameters,
				recordToNodeAndStatusMapper(targetNodeClass, FindExecutionStatus::valueOf, baseLogger),
				emptyResultError,
				errorMessage, baseLogger);
	}

	/*
	 * Get unique target node with a given relationshipType from a given source
	 * node.
	 * 
	 * Statuses:
	 * - IS_NOT_FOUND: The node was not found.
	 * - IS_FOUND: The node was found.
	 * - MORE_THAN_ONE_FOUND: More than one node was found.
	 * 
	 * (sourceNode)-[r:relationshipType]->(targetNode) RETURN targetNode
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<T, FindExecutionStatus>> getUniqueTargetNodeWithRelationshipType(
			S sourceNode, String relationshipType, Class<T> targetNodeClass, BaseLogger baseLogger) {
		baseLogger.trace(
				"Getting unique target node with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<T, FindExecutionStatus>builder()
					.error(new AbstractServiceError("getUniqueTargetNodeWithRelationshipType",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)), FindExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}

		String query = String.format(
				"""
						MATCH (sourceNode:%s {uuid: $sourceNodeUuid})
						OPTIONAL MATCH (sourceNode)-[r:%s]->(targetNode)
						WHERE NONE(label IN labels(targetNode) WHERE label ENDS WITH '_VERSIONED' OR label ENDS WITH '_DELETED')

						WITH collect(targetNode) AS nodes
						RETURN
						CASE
						  WHEN size(nodes) = 0 THEN 'NOT_FOUND'
						  WHEN size(nodes) > 1 THEN 'MORE_THAN_ONE_FOUND'
						  ELSE 'FOUND'
						END AS status,
						nodes[0] AS resultNode
								""",
				sourceNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "Query execution result is empty for source node: " + sourceNode.getUuid()
				+ " with relationship type: " + relationshipType;
		String errorMessage = "Failed while query execution while finding target nodes with relationship type: "
				+ relationshipType + " from source node: " + sourceNode.getUuid();

		return executeReadMono(query, parameters,
				recordToNodeAndStatusMapper(targetNodeClass, FindExecutionStatus::valueOf, baseLogger),
				emptyResultError, errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while finding target node {} : {} with relationship type: {} from source node {} : {}",
									targetNodeClass.getSimpleName(), result.getNode(), relationshipType,
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Failed to find target node {} : {} with relationship type: {} from source node {} : {}",
									targetNodeClass.getSimpleName(), result.getNode(), relationshipType,
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Failed to find target node {} : {} with relationship type: {} from source node {} : {}",
									targetNodeClass.getSimpleName(), result.getNode(), relationshipType,
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case FOUND:
							baseLogger.traceGreen(
									"Target node {} : {} with relationship type: {} from source node {} : {} was found: {}",
									targetNodeClass.getSimpleName(), result.getNode(), relationshipType,
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"Target node {} : {} with relationship type: {} from source node {} : {} was not found: {}",
									targetNodeClass.getSimpleName(), result.getNode(), relationshipType,
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one target node {} : {} with relationship type: {} from source node {} : {} was found: {}",
									targetNodeClass.getSimpleName(), result.getNode(), relationshipType,
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
	}

	/*
	 * Get all source nodes with a given relationshipType to a given targetNode.
	 * 
	 * Statuses:
	 * (sourceNode)-[r:relationshipType]->(targetNode) RETURN sourceNode
	 */
	protected <T extends AbstractNode, S extends AbstractNode> Flux<ExecutionResult<S, FindExecutionStatus>> getAllSourceNodesWithRelationshipType(
			T targetNode,
			String relationshipType, Class<S> sourceNodeClass, BaseLogger baseLogger) {
		baseLogger.trace(
				"Getting all source nodes with relationship type: {} to target node: {}",
				relationshipType, targetNode.getUuid());

		String query = String.format("""
				OPTIONAL MATCH (targetNode:%s {uuid: $targetNodeUuid})
				OPTIONAL MATCH (targetNode)<-[r:%s]-(sourceNode)
				RETURN
				CASE
					WHEN sourceNode IS NULL THEN 'NOT_FOUND'
					ELSE 'FOUND'
				END AS status,
				sourceNode AS resultNode
				""",
				targetNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("targetNodeUuid", targetNode.getUuid());

		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		String emptyResultError = "No source nodes found with relationship type: " + relationshipType
				+ " to target node: "
				+ targetNode.getUuid() + ": No source nodes found";
		String errorMessage = "Failed while query execution while finding source nodes with relationship type: "
				+ relationshipType + " to target node: " + targetNode.getUuid();

		return executeReadFlux(query, parameters,
				recordToNodeAndStatusMapper(sourceNodeClass, FindExecutionStatus::valueOf, baseLogger),
				emptyResultError,
				errorMessage, baseLogger)
				.flatMap(result -> {
					switch (result.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while finding source nodes {} with relationship type: {} to target node {} : {}",
									sourceNodeClass.getSimpleName(), relationshipType,
									targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Internal error while finding source nodes {} with relationship type: {} to target node {} : {}",
									sourceNodeClass.getSimpleName(), relationshipType,
									targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error while finding source nodes {} with relationship type: {} to target node {} : {}",
									sourceNodeClass.getSimpleName(), relationshipType,
									targetNode.getClass().getSimpleName(), targetNode.getUuid());
							return Mono.just(result);
						case FOUND:
							baseLogger.traceGreen(
									"Source nodes {} with relationship type: {} to target node {} : {} was found: {}",
									sourceNodeClass.getSimpleName(), relationshipType,
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), result.getNode());
							return Mono.just(result);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"Source nodes {} with relationship type: {} to target node {} : {} was not found: {}",
									sourceNodeClass.getSimpleName(), relationshipType,
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), result.getNode());
							return Mono.just(result);
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one source nodes {} with relationship type: {} to target node {} : {} was found: {}",
									sourceNodeClass.getSimpleName(), relationshipType,
									targetNode.getClass().getSimpleName(), targetNode.getUuid(), result.getNode());
							return Mono.just(result);
					}
					return Mono.just(result);
				});
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
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> createUniqueOutgoingRelationships(
			S sourceNode, List<T> targetNodes, Class<T> targetNodeClass, String relationshipType, AbstractUser user,
			BaseLogger baseLogger) {
		if (targetNodes == null) {
			baseLogger.traceRed("Input parameter targetNodes is null");
			return Mono.just(ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
					.error(new AbstractServiceError("createUniqueOutgoingRelationships",
							"Input parameter targetNodes is null", Map.of()),
							CreateRelationshipExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		baseLogger.trace(
				"Creating unique outgoing relationships between source node: {} and target nodes: {}",
				sourceNode.getUuid(), targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
					.error(new AbstractServiceError("createUniqueOutgoingRelationships",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)),
							CreateRelationshipExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		return getAllTargetNodesWithRelationshipType(sourceNode, relationshipType, targetNodeClass, baseLogger)
				.filter(result -> result.getStatus() == FindExecutionStatus.FOUND)
				.collectList()
				.flatMap(results -> {

					List<T> existingNodes = results.stream()
							.map(ExecutionResult::getNode)
							.filter(Objects::nonNull)
							.collect(Collectors.toList());

					List<T> toDeleteNodes = existingNodes.stream().filter(exNode -> targetNodes.stream()
							.noneMatch(toNode -> toNode.getUuid().equals(exNode.getUuid())))
							.collect(Collectors.toList());

					List<T> toCreateNodes = targetNodes.stream()
							.filter(toNode -> existingNodes.stream()
									.noneMatch(exNode -> exNode.getUuid().equals(toNode.getUuid())))
							.collect(Collectors.toList());

					Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> deleteProblem = Flux
							.fromIterable(toDeleteNodes)
							.concatMap(node -> deleteSingleRelationship(sourceNode, node, relationshipType, user,
									baseLogger))
							.filter(er -> (er.getStatus() != DeleteExecutionStatus.WAS_DELETED))
							.next();

					return deleteProblem
							.map(er -> ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
									.node(er.getNode())
									.error(
											er.getError() != null ? er.getError()
													: new AbstractServiceError(
															"createUniqueOutgoingRelationships",
															"Relationship delete failed with status: " + er.getStatus(),
															Map.of("deleteStatus", String.valueOf(er.getStatus()))),
											CreateRelationshipExecutionStatus.INTERNAL_ERROR)
									.build())
							.switchIfEmpty(Mono.defer(() -> Flux.fromIterable(toCreateNodes)
									.concatMap(node -> createSingleRelationship(sourceNode, node, relationshipType,
											user,
											baseLogger))
									.filter(er -> (er.getStatus() != CreateRelationshipExecutionStatus.WAS_CREATED))
									.next()
									.switchIfEmpty(Mono.just(
											ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
													.status(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)
													.build()))));
				})
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while creating unique outgoing relationships between source node: {} : {} and target nodes {} : {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNodeClass.getSimpleName(),
									targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Internal error while creating unique outgoing relationships between source node: {} : {} and target nodes {} : {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNodeClass.getSimpleName(),
									sourceNode.getUuid(),
									targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error while creating unique outgoing relationships between source node: {} : {} and target nodes {} : {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNodeClass.getSimpleName(),
									sourceNode.getUuid(),
									targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case IS_ALREADY_EXISTS:
							baseLogger.traceOrange(
									"Relationship already exists while creating unique outgoing relationships between source node: {} : {} and target nodes {} : {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNodeClass.getSimpleName(),
									sourceNode.getUuid(),
									targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case WAS_CREATED:
							baseLogger.traceGreen(
									"Relationship was created while creating unique outgoing relationships between source node: {} : {} and target nodes {} : {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNodeClass.getSimpleName(),
									sourceNode.getUuid(),
									targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case SOURCE_OR_TARGET_NODE_IS_NOT_FOUND:
							baseLogger.traceRed(
									"Source or target node is not found while creating unique outgoing relationships between source node: {} : {} and target nodes {} : {}",
									sourceNode.getClass().getSimpleName(), sourceNode.getUuid(),
									targetNodeClass.getSimpleName(),
									sourceNode.getUuid(),
									targetNodes.stream().map(T::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
					}
					return Mono.just(executionResult);
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
	 * @return Result with {@link CreateRelationshipExecutionStatus} (same pattern
	 * as
	 * {@link #createUniqueOutgoingRelationships}).
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> createUniqueIncomingRelationships(
			T targetNode,
			List<S> sourceNodes, Class<S> sourceNodeClass, String relationshipType, AbstractUser user,
			BaseLogger baseLogger) {
		if (sourceNodes == null) {
			baseLogger.traceRed("Input parameter sourceNodes is null");
			return Mono.just(ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
					.error(new AbstractServiceError("createUniqueIncomingRelationships",
							"Input parameter sourceNodes is null", Map.of()),
							CreateRelationshipExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		baseLogger.trace(
				"Creating unique incoming relationships between target node: {} and source nodes: {}",
				targetNode.getUuid(), sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));

		if (relationshipType == null || relationshipType.isEmpty()) {
			baseLogger.traceRed("Input parameter relationshipType is null or empty");
			return Mono.just(ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
					.error(new AbstractServiceError("createUniqueIncomingRelationships",
							"Input parameter relationshipType is null or empty",
							Map.of("relationshipType", relationshipType)),
							CreateRelationshipExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		return getAllSourceNodesWithRelationshipType(targetNode, relationshipType, sourceNodeClass, baseLogger)
				.filter(result -> result.getStatus() == FindExecutionStatus.FOUND)
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

					Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> deleteProblem = Flux
							.fromIterable(toDeleteNodes)
							.concatMap(node -> deleteSingleRelationship(node, targetNode, relationshipType, user,
									baseLogger))
							.filter(er -> (er.getStatus() != DeleteExecutionStatus.WAS_DELETED))
							.next();

					return deleteProblem
							.map(er -> ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
									.node(er.getNode())
									.error(
											er.getError() != null ? er.getError()
													: new AbstractServiceError(
															"createUniqueIncomingRelationships",
															"Relationship delete failed with status: " + er.getStatus(),
															Map.of("deleteStatus", String.valueOf(er.getStatus()))),
											CreateRelationshipExecutionStatus.INTERNAL_ERROR)
									.build())
							.switchIfEmpty(Mono.defer(() -> Flux.fromIterable(toCreateNodes)
									.concatMap(node -> createSingleRelationship(node, targetNode, relationshipType,
											user,
											baseLogger))
									.filter(er -> (er.getStatus() != CreateRelationshipExecutionStatus.WAS_CREATED))
									.next()
									.switchIfEmpty(Mono.just(
											ExecutionResult.<AbstractNode, CreateRelationshipExecutionStatus>builder()
													.status(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)
													.build()))));
				})
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result while creating unique incoming relationships with type {} between target node {} : {} and source nodes {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									sourceNodeClass.getSimpleName(),
									sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Internal error while creating unique incoming relationships with type {} between target node {} : {} and source nodes {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									sourceNodeClass.getSimpleName(),
									sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error while creating unique incoming relationships with type {} between target node {} : {} and source nodes {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									sourceNodeClass.getSimpleName(),
									sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case IS_ALREADY_EXISTS:
							baseLogger.traceOrange(
									"Relationship already exists while creating unique incoming relationships with type {} between target node {} : {} and source nodes {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									sourceNodeClass.getSimpleName(),
									sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case WAS_CREATED:
							baseLogger.traceGreen(
									"Relationship was created while creating unique incoming relationships with type {} between target node {} : {} and source nodes {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									sourceNodeClass.getSimpleName(),
									sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
						case SOURCE_OR_TARGET_NODE_IS_NOT_FOUND:
							baseLogger.traceRed(
									"Source or target node is not found while creating unique incoming relationships with type {} between target node {} : {} and source nodes {} : {}",
									relationshipType, targetNode.getClass().getSimpleName(), targetNode.getUuid(),
									sourceNodeClass.getSimpleName(),
									sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));
							return Mono.just(executionResult);
					}
					return Mono.just(executionResult);
				});
	}

}
