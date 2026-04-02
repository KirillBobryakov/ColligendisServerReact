// package com.colligendis.server.database;

// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.function.BiFunction;
// import java.util.function.Function;
// import java.util.stream.Collectors;

// import org.neo4j.driver.Driver;
// import org.neo4j.driver.Record;
// import org.neo4j.driver.Session;
// import org.neo4j.driver.SessionConfig;
// import org.neo4j.driver.reactivestreams.ReactiveResult;
// import org.neo4j.driver.reactivestreams.ReactiveSession;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;

// import jakarta.annotation.PostConstruct;

// import com.colligendis.server.database.exception.DatabaseError;
// import com.colligendis.server.database.exception.ErrorCodes;
// import com.colligendis.server.database.exception.Errors;
// import com.colligendis.server.util.Either;

// import lombok.extern.slf4j.Slf4j;
// import reactor.core.publisher.Flux;
// import reactor.core.publisher.Mono;

// @Slf4j
// @Service
// public abstract class AbstractService2 {

// protected static final String DELETED_NODE_LABEL_POSTFIX = "_DELETED";
// protected static final String VERSIONED_NODE_LABEL_POSTFIX = "_VERSIONED";

// protected static final String DELETED_RELATIONSHIP_TYPE_POSTFIX = "_DELETED";

// protected static final String CREATED_AT_PROPERTY = "createdAt";
// protected static final String CREATED_BY_PROPERTY = "createdBy";
// protected static final String UPDATED_AT_PROPERTY = "updatedAt";
// protected static final String UPDATED_BY_PROPERTY = "updatedBy";
// protected static final String DELETED_AT_PROPERTY = "deletedAt";
// protected static final String DELETED_BY_PROPERTY = "deletedBy";

// @Autowired
// protected Driver driver;

// @Value("${spring.neo4j.database:neo4j}")
// private String neo4jDatabase;

// private SessionConfig sessionConfig;

// @PostConstruct
// void initNeo4jSessionConfig() {
// this.sessionConfig =
// SessionConfig.builder().withDatabase(neo4jDatabase).build();
// log.info("Neo4j Bolt sessions use database: {}", neo4jDatabase);
// }

// /**
// * Blocking session (same DB as reactive); use from
// * {@code Mono.fromCallable(...).subscribeOn(boundedElastic())}.
// */
// protected Session openBlockingSession() {
// return driver.session(sessionConfig);
// }

// /**
// * Maps a {@code RETURN node AS result} (or equivalent) row to a domain node.
// * Shared by reactive reads and
// * blocking lookups.
// */
// protected <N extends AbstractNode> N mapRecordToNode(Record record, Class<N>
// nodeClass) {
// try {
// Map<String, Object> map;
// try {
// map = record.get("result").asNode().asMap(v -> v.asObject());
// } catch (Exception notNode) {
// map = record.get("result").asMap();
// }
// return AbstractNode.fromPropertiesMap(nodeClass, map);
// } catch (Exception e) {
// log.error("Failed to map Neo4j record to {}: {}", nodeClass.getSimpleName(),
// e.getMessage(), e);
// throw new RuntimeException("Failed to map Neo4j record to " +
// nodeClass.getSimpleName(), e);
// }
// }

// private ExecutionStatus stringToExecutionStatus(String stringStatus) {
// switch (stringStatus) {
// case "RELATIONSHIP_WAS_CREATED":
// return ExecutionStatus.RELATIONSHIP_WAS_CREATED;
// default:
// return ExecutionStatus.UNKNOWN;
// }
// }

// // Executers for read operations
// protected <T> Mono<Either<DatabaseError, T>> executeReadMono(String query,
// Map<String, Object> parameters,
// Function<Record, Either<DatabaseError, T>> resultMapper, String
// emptyResultError, String errorMessage) {
// return Flux.usingWhen(
// Mono.fromCallable(() -> driver.session(ReactiveSession.class,
// sessionConfig)),
// (ReactiveSession session) -> session.executeRead(tx ->
// Mono.fromDirect(tx.run(query, parameters))
// .flatMapMany(ReactiveResult::records)),
// ReactiveSession::close)
// .next()
// .map(resultMapper)
// .switchIfEmpty(
// Mono.just(Either.left(Errors.EXECUTION_READ_EMPTY_RESULT(emptyResultError,
// Map.of("query", query, "parameters", parameters)))))
// .doOnError(
// error -> log.error("Failed while query execution while reading mono: {}",
// error.getMessage()))
// .doOnSuccess(
// result -> {
// log.debug("=== executeReadMono SUCCESS ===");
// result.simpleFold(log);
// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);
// })
// .onErrorResume(error -> Mono
// .just(Either.left(Errors.NEO4J_INTERNAL_ERROR(errorMessage + ": " +
// error.getMessage(),
// Map.of("query", query, "parameters", parameters),
// error.getStackTrace(), error))));
// }

// protected <T extends AbstractNode> Mono<Either<DatabaseError, T>>
// readMonoQuery(String query,
// Map<String, Object> parameters,
// Class<T> nodeClass, String emptyResultError, String errorMessage) {
// return executeReadMono(query, parameters, nodeResultEitherMapper(nodeClass),
// emptyResultError, errorMessage);
// }

// protected <T> Flux<Either<DatabaseError, T>> executeReadFlux(String query,
// Map<String, Object> parameters,
// Function<Record, Either<DatabaseError, T>> resultMapper, String
// emptyResultError, String errorMessage) {
// return Flux.usingWhen(
// Mono.fromCallable(() -> driver.session(ReactiveSession.class,
// sessionConfig)),
// (ReactiveSession session) -> session.executeRead(tx ->
// Mono.fromDirect(tx.run(query, parameters))
// .flatMapMany(ReactiveResult::records)),
// ReactiveSession::close)
// .map(resultMapper)
// .switchIfEmpty(
// Flux.just(Either.left(Errors.EXECUTION_READ_EMPTY_RESULT(emptyResultError,
// Map.of("query", query, "parameters", parameters)))))
// .doOnError(
// error -> {
// log.error("Failed while query execution while reading flux: {}",
// error.getMessage());
// log.error("Query: {}", query);
// log.error("Parameters: {}", parameters.toString());
// })
// .doOnComplete(
// () -> {
// log.debug("=== executeReadFlux COMPLETED ===");
// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters.toString());
// })
// .onErrorResume(error -> Flux.just(Either.left(Errors.NEO4J_INTERNAL_ERROR(
// errorMessage + ": " + error.getMessage(),
// Map.of("query", query, "parameters", parameters),
// error.getStackTrace(), error))));
// }

// // Executers for write operations

// protected <T> Mono<Either<DatabaseError, T>> executeWriteMono(String query,
// Map<String, Object> parameters,
// Function<Record, Either<DatabaseError, T>> resultMapper, String
// emptyResultError, String errorMessage) {
// return Mono.usingWhen(
// Mono.fromCallable(() -> driver.session(ReactiveSession.class,
// sessionConfig)),
// (ReactiveSession session) -> Flux
// .from(session.executeWrite(tx -> Mono.fromDirect(tx.run(query, parameters))
// .flatMapMany(ReactiveResult::records)))
// .collectList()
// .flatMap(records -> {
// log.debug("query: {} result: {}", query, records);
// if (records.isEmpty()) {
// return Mono.empty();
// }
// return Mono.just(records.get(0));
// }),
// (ReactiveSession session) -> Mono.from(session.close()))
// .map(resultMapper)
// .switchIfEmpty(
// Mono.just(Either.<DatabaseError,
// T>left(Errors.EXECUTION_WRITE_EMPTY_RESULT(emptyResultError,
// Map.of("query", query, "parameters", parameters)))))
// .doOnError(
// error -> {
// log.error("Failed while query execution while writing mono: {}",
// error.getMessage());
// log.error("Query: {}", query);
// log.error("Parameters: {}", parameters.toString());
// })
// .doOnSuccess(
// result -> {
// log.debug("=== executeWriteMono SUCCESS ===");
// result.simpleFold(log);
// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters.toString());

// })
// .onErrorResume(error -> {
// DatabaseError databaseError = Errors.NEO4J_INTERNAL_ERROR(
// errorMessage + ": " + error.getMessage(), Map.of("query", query,
// "parameters", parameters),
// error.getStackTrace(), error);
// return Mono.just(Either.left(databaseError));
// });
// }

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

// protected <N extends AbstractNode> Function<Record, Either<DatabaseError, N>>
// nodeResultEitherMapper(
// Class<N> nodeClass) {
// return (record) -> this.<N>recordToNodeMapper().apply(record, nodeClass);
// }

// protected Function<Record, Either<DatabaseError, Boolean>>
// booleanResultEitherMapper = (record) -> Either
// .<DatabaseError, Boolean>right(booleanResultMapper.apply(record));

// protected Function<Record, Either<DatabaseError, String>>
// stringResultEitherMapper = (record) -> Either
// .<DatabaseError, String>right(stringResultMapper.apply(record));

// // Queries

// /**
// * Checks if a node exists in the database with a given property value and
// * label. Can be found many nodes with the same property value and label, but
// * only one node with the same property value and label is expected to be
// found.
// * Try to use unique property value if possible.
// *
// * @param propertyName The name of the property to check.
// * @param propertyValue The value of the property to check.
// * @param nodeLabel The label of the node to check.
// * @return A Mono containing either a DatabaseError or a Boolean indicating
// * if the node exists.
// */
// protected Mono<Either<DatabaseError, ExecutionStatus>>
// isNodeExistsByPropertyValue(
// String propertyName,
// String propertyValue,
// String nodeLabel) {

// log.debug("Checking if node exists with property: {} value: {} node label:
// {}", propertyName, propertyValue,
// nodeLabel);

// if (propertyName == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Property name is
// null", null)));
// }
// if (propertyValue == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Property value is
// null", null)));
// }
// if (propertyName.isEmpty()) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Property name is
// empty", null)));
// }
// if (propertyValue.isEmpty()) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Property value is
// empty", null)));
// }

// final String query = (nodeLabel == null || nodeLabel.isEmpty())
// ? String.format("RETURN EXISTS {(node: {%s: $propertyValue})} AS result",
// propertyName)
// : String.format("RETURN EXISTS {(node:%s {%s: $propertyValue})} AS result",
// nodeLabel, propertyName);

// Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No node found with property: " + propertyName + "
// value: " + propertyValue
// + " node label: " + nodeLabel + ": No node found";
// String errorMessage = "Failed while query execution while checking if node
// exists with property: "
// + propertyName + " value: " + propertyValue + " node label: " + nodeLabel;

// return executeReadMono(query, parameters, booleanResultEitherMapper,
// emptyResultError, errorMessage)
// .map(result -> result.fold(
// err -> Either.<DatabaseError, ExecutionStatus>left(err),
// exists -> {
// if (exists) {
// return Either.<DatabaseError,
// ExecutionStatus>right(ExecutionStatus.NODE_IS_EXISTS);
// } else {
// return Either.<DatabaseError,
// ExecutionStatus>right(ExecutionStatus.NODE_IS_NOT_EXISTS);
// }
// }));
// }

// protected <N extends AbstractNode> Mono<Either<DatabaseError, N>>
// updateNodeProperties(N node,
// AbstractUser user, Class<N> nodeClass) {

// if (node == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// node is null", null)));
// }

// if (node.getUuid() == null) {
// return Mono.just(Either.left(Errors.UPDATING_NOT_EXISTING_NODE_ERROR("Input
// parameter node.uuid is null")));
// }
// if (node.getLabel() == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// node.label is null", null)));
// }
// if (node.getPropertiesMap() == null || node.getPropertiesMap().isEmpty()) {
// return Mono.just(
// Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter node.propertiesMap
// is null or empty",
// null)));
// }

// log.debug("Updating node properties with uuid: {} and label: {}",
// node.getUuid(), node.getLabel());

// String query = String.format("MATCH (node:%s {uuid: $nodeUuid}) WITH node,
// $properties AS propsMap " +
// " WITH node, propsMap, [key IN keys(propsMap) WHERE node[key] IS NULL OR
// node[key] <> propsMap[key]] AS diffKeys "
// + "CALL(node, diffKeys) {WITH node, diffKeys WHERE size(diffKeys) = 0 RETURN
// node AS result} RETURN result "
// + "UNION ALL "
// + "MATCH (node:%s {uuid: $nodeUuid}) WITH node, $properties AS propsMap "
// + " WITH node, propsMap, [key IN keys(propsMap) WHERE node[key] IS NULL OR
// node[key] <> propsMap[key]] AS diffKeys "
// + "CALL(node, propsMap, diffKeys) {WITH node, propsMap, diffKeys "
// + "WHERE size(diffKeys) > 0 "
// + "CALL apoc.refactor.cloneNodes([node], TRUE, [\"uuid\"]) YIELD output AS
// newNode "
// + "FOREACH (key IN diffKeys | SET newNode[key] = propsMap[key]) SET
// newNode.uuid = randomUUID(), newNode.updatedAt = datetime({ timezone:
// '+03:00' }), newNode.updatedBy = $updatedBy "
// + "CREATE (newNode)-[:PREVIOUS_VERSION]->(node) "
// + "WITH node, labels(node) AS oldLabels, newNode REMOVE node:$(oldLabels)
// WITH node, newNode, [label IN oldLabels | label + '_VERSIONED'] AS newLabels
// SET node:$(newLabels) RETURN newNode AS result} RETURN result",
// node.getLabel(), node.getLabel());

// Map<String, Object> parameters = Map.of("nodeUuid", node.getUuid(),
// "properties", node.getPropertiesMap(),
// "updatedBy",
// user != null ? user.getUuid() : "");
// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);
// String emptyResultError = "No properties updated for node with uuid: " +
// node.getUuid() + " and label: "
// + node.getLabel() + ": No properties updated";
// String errorMessage = "Failed while query execution while updating node
// properties with uuid: " + node.getUuid()
// + " and label: " + node.getLabel();

// return executeWriteMono(query, parameters, nodeResultEitherMapper(nodeClass),
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// err -> {
// if (err.code().equals(ErrorCodes.EXECUTION_WRITE_EMPTY_RESULT)) {
// return Mono.just(Either.<DatabaseError, N>right(node));
// }
// return Mono.just(Either.<DatabaseError, N>left(err));
// },
// nodeUpdated -> Mono.just(Either.<DatabaseError, N>right(nodeUpdated))));
// }

// protected <N extends AbstractNode> Mono<Either<DatabaseError, N>>
// createNode(N node, AbstractUser user,
// Class<N> nodeClass) {

// // Fast-fail validation (non-reactive)
// if (node == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// node is null", null)));
// }
// if (node.getUuid() != null) {
// return Mono.just(Either.left(Errors.CREATING_NODE_WITH_EXISTING_UUID_ERROR(
// "Input parameter node.uuid is not null. Use updateNodeProperties instead of
// createNode to update node properties.")));
// }
// if (node.getLabel() == null || node.getLabel().isEmpty()) {
// return Mono.just(
// Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter node.label is null
// or empty", null)));
// }

// String query = String.format(
// "CREATE (node:%s {uuid: randomUUID(), createdAt: datetime({ timezone:
// '+03:00' }), createdBy: $createdBy %s}) RETURN node AS result",
// node.getLabel(),
// node.getPropertiesQuery());

// Map<String, Object> parameters = new HashMap<>(node.getPropertiesMap());
// parameters.put("createdBy", user != null ? user.getUuid() : "");

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No node created with uuid: " + node.getUuid() + "
// and label: " + node.getLabel()
// + ": No node created";
// String errorMessage = "Failed while query execution while creating node with
// uuid: " + node.getUuid()
// + " and label: " + node.getLabel();

// return executeWriteMono(query, parameters, nodeResultEitherMapper(nodeClass),
// emptyResultError, errorMessage);
// }

// protected <N extends AbstractNode> Mono<Either<DatabaseError,
// ExecutionStatus>> deleteNode(N node,
// AbstractUser user) {

// if (node == null)
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// node is null", null)));
// if (node.getUuid() == null)
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// node.uuid is null", null)));

// log.debug("Deleting node with uuid: {} and label: {}", node.getUuid(),
// node.getLabel());

// String query = String.format(
// "MATCH (n {uuid: $uuid}) WITH n, labels(n) AS oldLabels "
// + "REMOVE n:$(oldLabels) WITH n, oldLabels, [label IN oldLabels | label +
// '_DELETED'] AS newLabels "
// + "SET n:$(newLabels) SET n.deletedAt = datetime({ timezone: '+03:00' }),
// n.deletedBy = $deletedBy "
// + "RETURN labels(n) AS result");

// Map<String, Object> parameters = Map.of("uuid", node.getUuid(), "deletedBy",
// user != null ? user.getUuid() : "");
// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No node deleted with uuid: " + node.getUuid() + ":
// No node deleted";
// String errorMessage = "Failed while query execution while deleting node with
// uuid: " + node.getUuid();

// Function<Record, Either<DatabaseError, Boolean>> mapper = record -> {
// List<String> labels = listStringResultMapper.apply(record);
// return Either.<DatabaseError, Boolean>right(
// labels.stream().allMatch(label ->
// label.endsWith(DELETED_NODE_LABEL_POSTFIX)));
// };
// return executeWriteMono(query, parameters, mapper, emptyResultError,
// errorMessage)
// .flatMap(result -> result.fold(
// err -> Mono.just(Either.<DatabaseError, ExecutionStatus>left(err)),
// status -> {
// if (status) {
// return Mono.just(
// Either.<DatabaseError,
// ExecutionStatus>right(ExecutionStatus.NODE_WAS_DELETED));
// } else {
// return Mono.just(Either
// .<DatabaseError, ExecutionStatus>right(ExecutionStatus.NODE_IS_NOT_DELETED));
// }
// }));
// }

// protected <N extends AbstractNode> Mono<Either<DatabaseError, N>>
// findNodeByUniquePropertyValue(
// String propertyName,
// Object propertyValue,
// String nodeLabel, Class<N> nodeClass) {

// if (propertyName == null || propertyName.isEmpty()) {
// return Mono.just(
// Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter propertyName is
// null or empty", null)));
// }
// if (propertyValue == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// propertyValue is null", null)));
// }
// log.debug("Finding node with property: {} value: {} node label: {}",
// propertyName, propertyValue, nodeLabel);

// final String query = nodeLabel == null || nodeLabel.isEmpty()
// ? String.format("MATCH (node {%s: $propertyValue}) RETURN node AS result",
// propertyName)
// : String.format("MATCH (node:%s {%s: $propertyValue}) RETURN node AS result",
// nodeLabel, propertyName);

// log.debug("Property value class: {}", propertyValue.getClass());
// Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No node found with property: " + propertyName + "
// value: " + propertyValue
// + " node label: " + nodeLabel + ": No node found";
// String errorMessage = "Failed while query execution while finding node with
// property: " + propertyName
// + " value: " + propertyValue + " node label: " + nodeLabel;

// return executeReadMono(query, parameters, nodeResultEitherMapper(nodeClass),
// emptyResultError, errorMessage);
// }

// protected <N extends AbstractNode> Mono<Either<DatabaseError, N>>
// findNodeByUuid(String uuid,
// String nodeLabel, Class<N> nodeClass) {
// log.debug("Finding node with uuid: {} and label: {}", uuid, nodeLabel);
// return findNodeByUniquePropertyValue("uuid", uuid, nodeLabel, nodeClass);
// }

// protected <N extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, N>> findNodeByPropertyValueAndTargetNode(
// String propertyName, Object propertyValue, String nodeLabel, Class<N>
// nodeClass, T targetNode,
// String relationshipType) {

// if (propertyName == null || propertyName.isEmpty()) {
// return Mono.just(
// Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter propertyName is
// null or empty", null)));
// }
// if (propertyValue == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// propertyValue is null", null)));
// }
// if (targetNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }
// log.debug(
// "Finding node with property: {} value: {} node label: {} connected to node:
// {} with relationship type: {}",
// propertyName, propertyValue, nodeLabel, targetNode.getUuid(),
// relationshipType);
// final String query = nodeLabel == null ? String.format(
// "MATCH (node {%s: $propertyValue})-[:%s]->(connectedToNode {uuid:
// $connectedToNodeUuid}) RETURN node AS result",
// propertyName, relationshipType)
// : String.format(
// "MATCH (node:%s {%s: $propertyValue})-[:%s]->(connectedToNode {uuid:
// $connectedToNodeUuid}) RETURN node AS result",
// nodeLabel, propertyName, relationshipType);

// Map<String, Object> parameters = Map.of("propertyValue", propertyValue,
// "connectedToNodeUuid",
// targetNode.getUuid());

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No node found with property: " + propertyName + "
// value: " + propertyValue
// + " node label: " + nodeLabel + " connected to node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType + ": No node found";
// String errorMessage = "Failed while query execution while finding node with
// property: " + propertyName
// + " value: " + propertyValue + " node label: " + nodeLabel + " connected to
// node: "
// + targetNode.getUuid() + " with relationship type: " + relationshipType;

// return executeReadMono(query, parameters, nodeResultEitherMapper(nodeClass),
// emptyResultError, errorMessage);
// }

// protected <N extends AbstractNode> Flux<Either<DatabaseError, N>>
// findNodesByPropertyValue(
// String propertyName, Object propertyValue, String nodeLabel, Class<N>
// nodeClass) {

// if (propertyName == null || propertyName.isEmpty()) {
// return Flux.just(
// Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter propertyName is
// null or empty", null)));
// }
// if (propertyValue == null) {
// return Flux.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// propertyValue is null", null)));
// }
// log.debug("Finding nodes with property: {} value: {} node label: {}",
// propertyName, propertyValue, nodeLabel);

// final String query = nodeLabel == null
// ? String.format("MATCH (node {%s: $propertyValue}) RETURN node AS result",
// propertyName)
// : String.format("MATCH (node:%s {%s: $propertyValue}) RETURN node AS result",
// nodeLabel, propertyName);

// Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No nodes found with property: " + propertyName + "
// value: " + propertyValue
// + " node label: " + nodeLabel + ": No nodes found";
// String errorMessage = "Failed while query execution while finding nodes with
// property: " + propertyName
// + " value: " + propertyValue + " node label: " + nodeLabel;

// return executeReadFlux(query, parameters, nodeResultEitherMapper(nodeClass),
// emptyResultError, errorMessage);
// }

// // RELATIONSHIPS

// // Unique relationship is relationship from one node to another node, there
// can
// // be only one relationship of this type between the two nodes.

// protected <S extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, ExecutionStatus>> isRelationshipExists(
// S sourceNode, T targetNode, String relationshipType) {

// if (sourceNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (targetNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }

// log.debug(
// "Checking if relationship exists between source node: {} and target node: {}
// with relationship type: {}",
// sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

// String query = String.format(
// "MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target:%s {uuid:
// $targetNodeUuid}) RETURN COALESCE(count(r), 0)>0 as result",
// sourceNode.getLabel(), relationshipType, targetNode.getLabel());
// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid(), "targetNodeUuid",
// targetNode.getUuid());

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No relationship found between source node: " +
// sourceNode.getUuid()
// + " and target node: " + targetNode.getUuid() + " with relationship type: " +
// relationshipType
// + ": No relationship found";
// String errorMessage = "Failed while query execution while checking if
// relationship exists between source node: "
// + sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType;

// return executeReadMono(query, parameters, booleanResultEitherMapper,
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// err -> Mono.just(Either.<DatabaseError, ExecutionStatus>left(err)),
// exists -> {
// if (exists) {
// return Mono.just(Either
// .<DatabaseError,
// ExecutionStatus>right(ExecutionStatus.RELATIONSHIP_IS_EXISTS));
// } else {
// return Mono.just(Either.<DatabaseError, ExecutionStatus>right(
// ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS));
// }
// }));
// }

// /*
// * Check if any outgoing relationships exists from a node with a given
// * relationship type.
// */
// protected <S extends AbstractNode> Mono<Either<DatabaseError,
// ExecutionStatus>> isAnyOutgoingRelationshipsExists(
// S sourceNode,
// String relationshipType) {

// if (sourceNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }

// log.debug("Checking if any outgoing relationships exists from source node: {}
// with relationship type: {}",
// sourceNode.getUuid(), relationshipType);

// String query = String.format(
// "MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target) RETURN
// COALESCE(count(r), 0)>0 as result",
// sourceNode.getLabel(), relationshipType);
// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid());

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);
// String emptyResultError = "No any outgoing relationships found from source
// node: " + sourceNode.getUuid()
// + " with relationship type: " + relationshipType + ": No any outgoing
// relationships found";
// String errorMessage = "Failed while query execution while checking if any
// outgoing relationships exists from source node: "
// + sourceNode.getUuid() + " with relationship type: " + relationshipType;

// return executeReadMono(query, parameters, booleanResultEitherMapper,
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// err -> Mono.just(Either.<DatabaseError, ExecutionStatus>left(err)),
// exists -> {
// if (exists) {
// return Mono.just(Either
// .<DatabaseError,
// ExecutionStatus>right(ExecutionStatus.RELATIONSHIP_IS_EXISTS));
// } else {
// return Mono.just(Either.<DatabaseError, ExecutionStatus>right(
// ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS));
// }
// }));
// }

// /*
// * Check if any incoming relationships exists to a node with a given
// * relationship type.
// */
// protected <T extends AbstractNode> Mono<Either<DatabaseError,
// ExecutionStatus>> isAnyIncomingRelationshipsExists(
// T targetNode,
// String relationshipType) {

// if (targetNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }

// log.debug("Checking if any incoming relationships exists to target node: {}
// with relationship type: {}",
// targetNode.getUuid(), relationshipType);

// String query = String.format(
// "MATCH (target:%s {uuid: $targetNodeUuid})<-[r:%s]-(source) RETURN
// COALESCE(count(r), 0)>0 as result",
// targetNode.getLabel(), relationshipType);
// Map<String, Object> parameters = Map.of("targetNodeUuid",
// targetNode.getUuid());

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No any incoming relationships found to target
// node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType + ": No any incoming
// relationships found";
// String errorMessage = "Failed while query execution while checking if any
// incoming relationships exists to target node: "
// + targetNode.getUuid() + " with relationship type: " + relationshipType;

// return executeReadMono(query, parameters, booleanResultEitherMapper,
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// err -> Mono.just(Either.<DatabaseError, ExecutionStatus>left(err)),
// exists -> {
// if (exists) {
// return Mono.just(Either
// .<DatabaseError,
// ExecutionStatus>right(ExecutionStatus.RELATIONSHIP_IS_EXISTS));
// } else {
// return Mono.just(Either.<DatabaseError, ExecutionStatus>right(
// ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS));
// }
// }));
// }

// /*
// * Create a single relationship between two nodes. There are many
// relationships
// * from one node to anothers with the same relationship type.
// */
// protected <S extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, ExecutionStatus>> createSingleRelationship(
// S sourceNode,
// T targetNode, String relationshipType, AbstractUser user) {

// if (sourceNode == null)
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Source node is
// null", null)));
// if (targetNode == null)
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Target node is
// null", null)));
// if (relationshipType == null)
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Relationship type
// is null", null)));

// log.debug("Creating single relationship between source node: {} and target
// node: {} with relationship type: {}",
// sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

// String query = String.format(
// "OPTIONAL MATCH (source:%s {uuid: $sourceNodeUuid}) " +
// "OPTIONAL MATCH (target:%s {uuid: $targetNodeUuid}) " +
// "WITH source, target, " +
// "CASE WHEN source IS NULL OR target IS NULL THEN 'NODE_IS_NULL' ELSE 'OK' END
// AS nodeStatus " +
// "OPTIONAL MATCH (source)-[r:%s]->(target) " +
// "WITH source, target, r, nodeStatus, CASE WHEN nodeStatus = 'NODE_IS_NULL'
// THEN 'NODE_IS_NULL' WHEN r IS NOT NULL THEN 'RELATIONSHIP_IS_EXISTS' ELSE
// 'RELATIONSHIP_WAS_CREATED' END AS result "
// +
// "FOREACH (_ IN CASE WHEN result = 'RELATIONSHIP_WAS_CREATED' THEN [1] ELSE []
// END | " +
// "CREATE (source)-[:%s {createdAt: datetime({ timezone: '+03:00' }),
// createdBy: $createdBy}]->(target)) RETURN result",
// sourceNode.getLabel(), targetNode.getLabel(), relationshipType,
// relationshipType);

// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid(), "targetNodeUuid",
// targetNode.getUuid(),
// "createdBy", user != null ? user.getUuid() : "");

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No single relationship created between source
// node: " + sourceNode.getUuid()
// + " and target node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType
// + ": No single relationship created";
// String errorMessage = "Failed while query execution while creating single
// relationship between source node: "
// + sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType;

// return executeWriteMono(query, parameters, stringResultEitherMapper,
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// error -> Mono.just(Either.left(error)),
// res -> {
// switch (res) {
// case "RELATIONSHIP_WAS_CREATED":
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_WAS_CREATED));
// case "NODE_IS_NULL":
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Source node or target node is null",
// Map.of("query", query, "parameters", parameters))));
// case "RELATIONSHIP_IS_EXISTS":
// log.debug(
// "Relationship already exists between source node: {} and target node: {} with
// relationship type: {}",
// sourceNode.getUuid(), targetNode.getUuid(), relationshipType);
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_IS_EXISTS));
// default:
// return Mono.just(Either.left(Errors.UNKNOWN_EXECUTION_STATUS(res,
// Map.of("query", query, "parameters", parameters))));
// }
// }));
// }

// /*
// * Delete a single relationship between two nodes.
// */
// protected <S extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, ExecutionStatus>> deleteSingleRelationship(
// S sourceNode,
// T targetNode, String relationshipType, AbstractUser user) {
// if (sourceNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (targetNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }
// return Mono.defer(() -> isRelationshipExists(sourceNode, targetNode,
// relationshipType))
// .flatMap(existsResult -> existsResult.fold(
// error -> Mono.just(Either.left(error)),
// exists -> {
// if (exists == ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS) {
// log.debug(
// "Relationship between source node: {} and target node: {} with relationship
// type: {} does not exist",
// sourceNode.getUuid(), targetNode.getUuid(), relationshipType);
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_WAS_DELETED));
// }
// log.debug(
// "Deleting single relationship between source node: {} and target node: {}
// with relationship type: {}",
// sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

// String query = String.format(
// "MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target:%s {uuid:
// $targetNodeUuid}) CALL (source, r, target) { WITH source, r, target CREATE
// (source)-[r2:%s] ->(target) SET r2 = properties(r), r2.deletedAt = datetime({
// timezone: '+03:00' }), r2.deletedBy = $deletedBy DELETE r } RETURN
// COALESCE(count(*), 0)>0 as result",
// sourceNode.getLabel(), relationshipType, targetNode.getLabel(),
// relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX);
// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid(),
// "targetNodeUuid",
// targetNode.getUuid(),
// "deletedBy", user != null ? user.getUuid() : "");

// String emptyResultError = "No single relationship deleted between source
// node: "
// + sourceNode.getUuid()
// + " and target node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType
// + ": No single relationship deleted";
// String errorMessage = "Failed while query execution while deleting single
// relationship between source node: "
// + sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType;
// return executeWriteMono(query, parameters, booleanResultEitherMapper,
// emptyResultError,
// errorMessage).flatMap(
// result -> result.fold(
// error -> Mono.just(Either.left(error)),
// deleted -> {
// if (deleted) {
// return Mono.just(Either
// .right(ExecutionStatus.RELATIONSHIP_WAS_DELETED));
// } else {
// return Mono.just(Either
// .right(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS));
// }
// }));
// }));
// }

// /*
// * Delete all outgoing relationships with RelationshipType from a node
// */
// protected <S extends AbstractNode> Mono<Either<DatabaseError,
// ExecutionStatus>> deleteAllOutgoingRelationshipsWithRelationshipType(
// S sourceNode, String relationshipType, AbstractUser user) {
// if (sourceNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }

// log.debug("Deleting all outgoing relationships with relationship type: {}
// from source node: {}",
// relationshipType, sourceNode.getUuid());

// String query = String.format(
// "MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target) CALL (source, r,
// target) { WITH source, r, target CREATE (source)-[r2:%s]->(target) SET r2 =
// properties(r), r2.deletedAt = datetime({ timezone: '+03:00' }), r2.deletedBy
// = $deletedBy DELETE r } RETURN COALESCE(count(*), 0)>0 as result",
// sourceNode.getLabel(), relationshipType, relationshipType +
// DELETED_RELATIONSHIP_TYPE_POSTFIX);
// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid(),
// "deletedBy", user != null ? user.getUuid() : "");

// String emptyResultError = "No all outgoing relationships deleted from source
// node: " + sourceNode.getUuid()
// + " with relationship type: " + relationshipType + ": No all outgoing
// relationships deleted";
// String errorMessage = "Failed while query execution while deleting all
// outgoing relationships with relationship type: "
// + relationshipType + " from source node: " + sourceNode.getUuid();

// return executeWriteMono(query, parameters, booleanResultEitherMapper,
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// error -> Mono.just(Either.left(error)),
// deleted -> {
// if (deleted) {
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_WAS_DELETED));
// } else {
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS));
// }
// }));
// }

// /*
// * Delete all incoming relationships with RelationshipType to a node
// */
// protected <T extends AbstractNode> Mono<Either<DatabaseError,
// ExecutionStatus>> deleteAllIncomingRelationshipsWithRelationshipType(
// T targetNode, String relationshipType, AbstractUser user) {
// if (targetNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }
// log.debug("Deleting all incoming relationships with relationship type: {} to
// target node: {}",
// relationshipType, targetNode.getUuid());

// String query = String.format(
// "MATCH (source)-[r:%s]->(target:%s {uuid: $targetNodeUuid}) CALL (source, r,
// target) { WITH source, r, target CREATE (source)-[r2:%s]->(target) SET r2 =
// properties(r), r2.deletedAt = datetime({ timezone: '+03:00' }), r2.deletedBy
// = $deletedBy DELETE r } RETURN COALESCE(count(*), 0)>0 as result",
// relationshipType, targetNode.getLabel(), relationshipType +
// DELETED_RELATIONSHIP_TYPE_POSTFIX);
// Map<String, Object> parameters = Map.of("targetNodeUuid",
// targetNode.getUuid(),
// "deletedBy", user != null ? user.getUuid() : "");

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No all incoming relationships deleted to target
// node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType + ": No all incoming
// relationships deleted";
// String errorMessage = "Failed while query execution while deleting all
// incoming relationships with relationship type: "
// + relationshipType + " to target node: " + targetNode.getUuid();

// return executeWriteMono(query, parameters, booleanResultEitherMapper,
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// error -> Mono.just(Either.left(error)),
// deleted -> {
// if (deleted) {
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_WAS_DELETED));
// } else {
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS));
// }
// }));
// }

// /*
// * Create a unique relationship between two nodes. There can be only one
// * relationship of this type from fromNode. It uses for set a property for
// * entity.
// *
// * <img src="doc-files/test.png" alt="Test image" />
// */
// protected <S extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, ExecutionStatus>>
// createUniqueTargetedRelationship(
// S sourceNode,
// T targetNode, String relationshipType, AbstractUser user) {
// if (sourceNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (targetNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }

// log.debug("Creating unique relationship between source node: {} and target
// node: {} with relationship type: {}",
// sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

// // String query = String.format(
// // "MATCH (source:%s {uuid: $sourceNodeUuid}) "
// // + "OPTIONAL MATCH (source)-[r:%s]->(target:%s) WHERE target.uuid <>
// // $targetNodeUuid "
// // + "WITH source, r, target "
// // + "CALL (source, r, target) { WITH source, r, target WHERE target IS NOT
// NULL
// // "
// // + "CREATE (source)-[r2:%s]->(target) SET r2 = properties(r), r2.deletedAt
// =
// // datetime({ timezone: '+03:00' }), r2.deletedBy = $deletedBy DELETE r } "
// // + "WITH (source) MATCH (t:%s {uuid:$targetNodeUuid}) "
// // + "MERGE (source)-[nr:%s]->(t) ON CREATE SET nr.createdAt =
// // datetime({timezone: '+03:00'}), nr.createdBy = $createdBy "
// // + "RETURN COALESCE(count(nr), 0)>0 as result",
// // sourceNode.getLabel(), relationshipType, targetNode.getLabel(),
// // relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX,
// // targetNode.getLabel(), relationshipType);

// String query = String.format("OPTIONAL MATCH (source:%s {uuid:
// $sourceNodeUuid}) "
// + "OPTIONAL MATCH (target:%s {uuid: $targetNodeUuid}) "
// + "WITH source, target "
// + "WHERE source IS NULL OR target IS NULL "
// + "RETURN 'ERROR' AS result "
// + "UNION "
// + "MATCH (source:%s {uuid: $sourceNodeUuid}) "
// + "MATCH (target:%s {uuid: $targetNodeUuid}) "
// + "OPTIONAL MATCH (source)-[r:%s]->(oldTarget:%s) "
// + "WHERE oldTarget.uuid <> $targetNodeUuid "
// + "CALL(source, r, oldTarget) { "
// + "WITH source, r, oldTarget WHERE oldTarget IS NOT NULL "
// + "CREATE (source)-[r2:%s_DELETED]->(oldTarget) "
// + "SET r2 = properties(r), r2.deletedAt = datetime({ timezone: '+03:00'
// }),r2.deletedBy = $deletedBy "
// + "DELETE r "
// + "} "
// + "WITH source, target "
// + "MERGE (source)-[nr:%s]->(target) "
// + "ON CREATE SET nr.createdAt = datetime({ timezone: '+03:00' }),
// nr.createdBy = $createdBy, nr._created = true "
// + "WITH CASE WHEN nr._created THEN 'RELATIONSHIP_WAS_CREATED' ELSE
// 'RELATIONSHIP_IS_EXISTS' END AS result, nr "
// + "REMOVE nr._created RETURN result ",
// sourceNode.getLabel(), targetNode.getLabel(), sourceNode.getLabel(),
// targetNode.getLabel(),
// relationshipType, targetNode.getLabel(), relationshipType, relationshipType);

// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid(), "targetNodeUuid",
// targetNode.getUuid(), "deletedBy", user != null ? user.getUuid() : "",
// "createdBy",
// user != null ? user.getUuid() : "");

// String emptyResultError = "No unique relationship created between source
// node: " + sourceNode.getUuid()
// + " and target node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType
// + ": No unique relationship created";
// String errorMessage = "Failed while query execution while creating unique
// relationship between source node: "
// + sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
// + " with relationship type: " + relationshipType;

// return executeWriteMono(query, parameters, stringResultEitherMapper,
// emptyResultError, errorMessage)
// .flatMap(result -> result.fold(
// error -> Mono.just(Either.left(error)),
// created -> {
// if (created.equals("RELATIONSHIP_WAS_CREATED")) {
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_WAS_CREATED));
// } else if (created.equals("RELATIONSHIP_IS_EXISTS")) {
// return Mono.just(Either.right(ExecutionStatus.RELATIONSHIP_IS_EXISTS));
// } else if (created.equals("ERROR")) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR(errorMessage,
// Map.of("query", query, "parameters", parameters))));
// }
// return Mono.just(Either.left(Errors.UNKNOWN_EXECUTION_STATUS("Unknown result:
// " + created,
// Map.of("query", query, "parameters", parameters))));

// }));
// };

// /*
// * Get all toNode with a given relationshipType from a given fromNode.
// *
// * (fromNode)-[r:relationshipType]->(toNode) RETURN toNode
// */
// protected <S extends AbstractNode, T extends AbstractNode>
// Flux<Either<DatabaseError, T>> getAllTargetNodesWithRelationshipType(
// S sourceNode, String relationshipType, Class<T> targetNodeClass) {
// if (sourceNode == null) {
// return Flux.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Flux.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }
// log.debug("Getting all target nodes with relationship type: {} from source
// node: {}",
// relationshipType, sourceNode.getUuid());

// String query = String.format(
// "MATCH (sourceNode:%s {uuid: $sourceNodeUuid})-[r:%s]->(targetNode) RETURN
// targetNode AS result",
// sourceNode.getLabel(), relationshipType);
// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid());

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No target nodes found with relationship type: " +
// relationshipType
// + " from source node: "
// + sourceNode.getUuid() + ": No target nodes found";
// String errorMessage = "Failed while query execution while finding target
// nodes with relationship type: "
// + relationshipType + " from source node: " + sourceNode.getUuid();

// return executeReadFlux(query, parameters,
// nodeResultEitherMapper(targetNodeClass), emptyResultError,
// errorMessage);
// }

// /*
// * Get unique toNode with a given relationshipType from a given fromNode.
// *
// * (fromNode)-[r:relationshipType]->(toNode) RETURN toNode
// */
// protected <S extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, T>> getUniqueTargetNodeWithRelationshipType(
// S sourceNode, String relationshipType, Class<T> targetNodeClass) {
// if (sourceNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }

// log.debug("Getting unique target node with relationship type: {} from source
// node: {}",
// relationshipType, sourceNode.getUuid());

// String query = String.format(
// "MATCH (sourceNode:%s {uuid: $sourceNodeUuid})-[r:%s]->(targetNode) WHERE
// NONE(label IN labels(targetNode) WHERE label ENDS WITH 'VERSIONED') RETURN
// targetNode AS result",
// sourceNode.getLabel(), relationshipType);
// Map<String, Object> parameters = Map.of("sourceNodeUuid",
// sourceNode.getUuid());

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No target nodes found with relationship type: " +
// relationshipType
// + " from source node: "
// + sourceNode.getUuid() + ": No target nodes found";
// String errorMessage = "Failed while query execution while finding target
// nodes with relationship type: "
// + relationshipType + " from source node: " + sourceNode.getUuid();

// return executeReadMono(query, parameters,
// nodeResultEitherMapper(targetNodeClass), emptyResultError,
// errorMessage);
// }

// protected <T extends AbstractNode, S extends AbstractNode>
// Flux<Either<DatabaseError, S>> getAllSourceNodesWithRelationshipType(
// T targetNode,
// String relationshipType, Class<S> sourceNodeClass) {

// log.debug("Getting all source nodes with relationship type: {} to target
// node: {}",
// relationshipType, targetNode.getUuid());

// String query = String.format(
// "MATCH (targetNode:%s {uuid: $targetNodeUuid})<-[r:%s]-(sourceNode) RETURN
// sourceNode AS result",
// targetNode.getLabel(), relationshipType);
// Map<String, Object> parameters = Map.of("targetNodeUuid",
// targetNode.getUuid());

// log.debug("Query: {}", query);
// log.debug("Parameters: {}", parameters);

// String emptyResultError = "No source nodes found with relationship type: " +
// relationshipType
// + " to target node: "
// + targetNode.getUuid() + ": No source nodes found";
// String errorMessage = "Failed while query execution while finding source
// nodes with relationship type: "
// + relationshipType + " to target node: " + targetNode.getUuid();

// return executeReadFlux(query, parameters,
// nodeResultEitherMapper(sourceNodeClass), emptyResultError,
// errorMessage);
// }

// /*
// * Create unique outgoing relationships between a fromNode and a targetNodes.
// * of
// * toNodes.
// *
// * (fromNode)-[r:relationshipType]->(targetNodes)
// *
// * @param fromNode
// *
// * @param targetNodes
// *
// * @param relationshipType
// *
// * @param user
// *
// * @return
// */
// protected <S extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, ExecutionStatus>>
// createUniqueOutgoingRelationships(
// S sourceNode, List<T> targetNodes, Class<T> targetNodeClass, String
// relationshipType, AbstractUser user) {

// if (sourceNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNode is null", null)));
// }
// if (targetNodes == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNodes is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }
// return getAllTargetNodesWithRelationshipType(sourceNode, relationshipType,
// targetNodeClass)
// .flatMap(e -> e.fold(err -> Mono.empty(), Mono::just))
// .collectList()
// .flatMap(existingNodes -> {

// List<T> toDeleteNodes = existingNodes.stream()
// .filter(exNode -> targetNodes.stream()
// .noneMatch(toNode -> toNode.getUuid().equals(exNode.getUuid())))
// .collect(Collectors.toList());

// List<T> toCreateNodes = targetNodes.stream()
// .filter(toNode -> existingNodes.stream()
// .noneMatch(exNode -> exNode.getUuid().equals(toNode.getUuid())))
// .collect(Collectors.toList());

// Mono<Void> deleteMono = Flux.fromIterable(toDeleteNodes)
// .flatMap(node -> deleteSingleRelationship(sourceNode, node, relationshipType,
// user)
// .flatMap(result -> result.<Mono<ExecutionStatus>>fold(
// err -> Mono.error(new RuntimeException(err.message())),
// ok -> Mono.just(ok))))
// .then();
// Mono<Void> createMono = Flux.fromIterable(toCreateNodes)
// .flatMap(node -> createSingleRelationship(sourceNode, node, relationshipType,
// user)
// .flatMap(result -> result.<Mono<ExecutionStatus>>fold(
// err -> Mono.error(new RuntimeException(err.message())),
// ok -> Mono.just(ok))))
// .then();

// return deleteMono.then(createMono).thenReturn(Either.<DatabaseError,
// ExecutionStatus>right(
// ExecutionStatus.RELATIONSHIP_WAS_CREATED))
// .onErrorResume(err -> Mono
// .just(Either.left(Errors.NEO4J_INTERNAL_ERROR(err.getMessage(), null, null,
// err))));
// });
// }

// /*
// * Create unique incoming relationships between a list of toNodes and a list
// * of
// * toNodes.
// *
// * (sourceNodes)-[r:relationshipType]->(toNode)
// *
// * @param targetNode
// *
// * @param sourceNodes
// *
// * @param sourceNodeClass
// *
// * @param relationshipType
// *
// * @param user
// *
// * @return
// */
// protected <S extends AbstractNode, T extends AbstractNode>
// Mono<Either<DatabaseError, ExecutionStatus>>
// createUniqueIncomingRelationships(
// T targetNode,
// List<S> sourceNodes, Class<S> sourceNodeClass, String relationshipType,
// AbstractUser user) {
// if (targetNode == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// targetNode is null", null)));
// }
// if (sourceNodes == null) {
// return Mono.just(Either.left(Errors.INPUT_PARAMETERS_ERROR("Input parameter
// sourceNodes is null", null)));
// }
// if (relationshipType == null || relationshipType.isEmpty()) {
// return Mono.just(Either
// .left(Errors.INPUT_PARAMETERS_ERROR("Input parameter relationshipType is null
// or empty", null)));
// }

// log.debug("Creating unique incoming relationships between target node: {} and
// source nodes: {}",
// targetNode.getUuid(),
// sourceNodes.stream().map(S::getUuid).collect(Collectors.toList()));

// return getAllSourceNodesWithRelationshipType(targetNode, relationshipType,
// sourceNodeClass)
// .flatMap(e -> e.fold(err -> Mono.empty(), Mono::just))
// .collectList()
// .flatMap(existingNodes -> {

// List<S> toDeleteNodes = existingNodes.stream()
// .filter(exNode -> sourceNodes.stream()
// .noneMatch(toNode -> toNode.getUuid().equals(exNode.getUuid())))
// .collect(Collectors.toList());

// List<S> toCreateNodes = sourceNodes.stream()
// .filter(sNode -> existingNodes.stream()
// .noneMatch(exNode -> exNode.getUuid().equals(sNode.getUuid())))
// .collect(Collectors.toList());

// Mono<Void> deleteMono = Flux.fromIterable(toDeleteNodes)
// .flatMap(node -> deleteSingleRelationship(node, targetNode, relationshipType,
// user)
// .flatMap(result -> result.fold(
// err -> Mono.just(Either.left(err)),
// deleted -> Mono.just(Either.right(deleted)))))
// .then();
// Mono<Void> createMono = Flux.fromIterable(toCreateNodes)
// .flatMap(node -> createSingleRelationship(node, targetNode, relationshipType,
// user)
// .flatMap(result -> result.<Mono<ExecutionStatus>>fold(
// err -> Mono.error(new RuntimeException(err.message())),
// ok -> Mono.just(ok))))
// .then();

// return deleteMono.then(createMono).thenReturn(Either.<DatabaseError,
// ExecutionStatus>right(
// ExecutionStatus.RELATIONSHIP_WAS_CREATED))
// .onErrorResume(err -> Mono
// .just(Either.left(Errors.NEO4J_INTERNAL_ERROR(err.getMessage(), null, null,
// err))));
// });
// }

// }
