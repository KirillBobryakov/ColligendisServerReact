package com.colligendis.server.database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.InputParametersError;
import com.colligendis.server.database.exception.Neo4jInternalError;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.util.Either;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
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

	private final SessionConfig sessionConfig = SessionConfig.builder().withDatabase("neo4j").build();

	// Executers for read operations
	protected <T> Mono<Either<DatabaseException, T>> executeReadMono(String query, Map<String, Object> parameters,
			Function<Record, Either<DatabaseException, T>> resultMapper, String emptyResultError, String errorMessage) {
		return Flux.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> session.executeRead(tx -> Flux.from(tx.run(query, parameters))
						.flatMap(result -> Flux.from(result.records()))),
				ReactiveSession::close)
				.next()
				.map(resultMapper)
				.switchIfEmpty(
						Mono.just(Either.left(new NotFoundError(
								emptyResultError))))
				.doOnError(
						error -> log.error("Failed while query execution while reading mono: {}", error.getMessage()))
				.doOnSuccess(
						result -> log.debug("=== executeReadMono SUCCESS === Result: {}",
								result.isRight() ? "OK" : "ERROR"))
				.onErrorResume(error -> Mono.just(Either.left(new Neo4jInternalError(
						errorMessage + ": " + error.getMessage()))));
	}

	protected <T extends AbstractNode> Mono<Either<DatabaseException, T>> readMonoQuery(String query,
			Map<String, Object> parameters,
			Class<T> nodeClass, String emptyResultError, String errorMessage) {
		return executeReadMono(query, parameters, nodeResultEitherMapper(nodeClass), emptyResultError, errorMessage);
	}

	protected <T> Flux<Either<DatabaseException, T>> executeReadFlux(String query, Map<String, Object> parameters,
			Function<Record, Either<DatabaseException, T>> resultMapper, String emptyResultError, String errorMessage) {
		return Flux.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> session.executeRead(tx -> Flux.from(tx.run(query, parameters))
						.flatMap(result -> Flux.from(result.records()))),
				ReactiveSession::close)
				.map(resultMapper)
				.switchIfEmpty(
						Mono.just(Either.left(new NotFoundError(
								emptyResultError))))
				.doOnError(
						error -> log.error("Failed while query execution while reading flux: {}", error.getMessage()))
				.doOnComplete(
						() -> log.debug("=== executeReadFlux COMPLETED === Query: {}", query))
				.onErrorResume(error -> Mono.just(Either.left(new Neo4jInternalError(
						errorMessage + ": " + error.getMessage()))));
	}

	// Executers for write operations

	protected <T> Mono<Either<DatabaseException, T>> executeWriteMono(String query, Map<String, Object> parameters,
			Function<Record, Either<DatabaseException, T>> resultMapper, String emptyResultError, String errorMessage) {
		return Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig)),
				(ReactiveSession session) -> Flux.from(session.executeWrite(tx -> Flux.from(tx.run(query, parameters))
						.flatMap(result -> Flux.from(result.records()))))
						.collectList()
						.flatMap(records -> {
							if (records.isEmpty()) {
								return Mono.empty();
							}
							return Mono.just(records.get(0));
						}),
				(ReactiveSession session) -> Mono.from(session.close()))
				.map(resultMapper)
				.switchIfEmpty(Mono.just(Either.<DatabaseException, T>left(new NotFoundError(emptyResultError))))
				.doOnError(
						error -> log.error("Failed while query execution while writing mono: {}", error.getMessage()))
				.doOnSuccess(
						result -> log.debug("=== executeWriteMono SUCCESS === Result: {}",
								result.isRight() ? "OK" : "ERROR"))
				.onErrorResume(error -> Mono.just(Either.<DatabaseException, T>left(new Neo4jInternalError(
						errorMessage + ": " + error.getMessage()))));
	}

	// Mappers

	private Function<Record, String> stringResultMapper = (record) -> record.get("result").asString();
	private Function<Record, List<String>> listStringResultMapper = (record) -> record.get("result")
			.asList(v -> v.asString());

	private Function<Record, Boolean> booleanResultMapper = (record) -> record.get("result").asBoolean();
	private Function<Record, Integer> integerResultMapper = (record) -> record.get("result").asInt();
	private Function<Record, Long> longResultMapper = (record) -> record.get("result").asLong();
	private Function<Record, Double> doubleResultMapper = (record) -> record.get("result").asDouble();

	private <N extends AbstractNode> BiFunction<Record, Class<N>, Either<DatabaseException, N>> recordToNodeMapper() {
		return (record, nodeClass) -> {
			Map<String, Object> map = record.get("result").asMap();
			N n = AbstractNode.fromPropertiesMap(nodeClass, map);
			return Either.<DatabaseException, N>right(n);
		};
	}

	protected <N extends AbstractNode> Function<Record, Either<DatabaseException, N>> nodeResultEitherMapper(
			Class<N> nodeClass) {
		return (record) -> this.<N>recordToNodeMapper().apply(record, nodeClass);
	}

	protected Function<Record, Either<DatabaseException, Boolean>> booleanResultEitherMapper = (record) -> Either
			.<DatabaseException, Boolean>right(booleanResultMapper.apply(record));

	// Queries

	/**
	 * Checks if a node exists in the database with a given property value and
	 * label. Can be found many nodes with the same property value and label, but
	 * only one node with the same property value and label is expected to be found.
	 * Try to use unique property value if possible.
	 * 
	 * @param propertyName  The name of the property to check.
	 * @param propertyValue The value of the property to check.
	 * @param nodeLabel     The label of the node to check.
	 * @return A Mono containing either a DatabaseException or a Boolean indicating
	 *         if the node exists.
	 */
	protected Mono<Either<DatabaseException, Boolean>> isNodeExistsByPropertyValue(
			String propertyName,
			String propertyValue,
			String nodeLabel) {

		log.debug("Checking if node exists with property: {} value: {} node label: {}", propertyName, propertyValue,
				nodeLabel);

		final String query = (nodeLabel == null)
				? String.format("RETURN EXISTS {(node: {%s: $propertyValue})} AS result", propertyName)
				: String.format("RETURN EXISTS {(node:%s {%s: $propertyValue})} AS result",
						nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No node found with property: " + propertyName + " value: " + propertyValue
				+ " node label: " + nodeLabel + ": No node found";
		String errorMessage = "Failed while query execution while checking if node exists with property: "
				+ propertyName + " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	}

	/**
	 * Checks if a node exists in the database with a given uuid and label. Only one
	 * node with the same uuid and label is expected to be found.
	 * 
	 * @param node The node to check.
	 * @return A Mono containing either a DatabaseException or a Boolean indicating
	 *         if the node exists.
	 */
	protected <N extends AbstractNode> Mono<Either<DatabaseException, Boolean>> isNodeExists(N node) {
		return isNodeExistsByPropertyValue("uuid", node.getUuid(), node.getLabel());
	}

	protected <N extends AbstractNode> Mono<Either<DatabaseException, N>> updateAllNodeProperties(N node,
			AbstractUser user, Class<N> nodeClass) {
		return updateNodeProperties(node, node.getPropertiesMap(), user, nodeClass);
	}

	protected <N extends AbstractNode> Mono<Either<DatabaseException, N>> updateNodeProperties(N node,
			Map<String, Object> properties, AbstractUser user, Class<N> nodeClass) {

		if (node == null) {
			return Mono.just(Either.left(new InputParametersError("Input parameter node is null")));
		}
		log.debug("Updating node properties with uuid: {} and label: {}", node.getUuid(), node.getLabel());

		if (node.getUuid() == null) {
			return Mono.just(Either.left(new InputParametersError("Input parameter node.uuid is null")));
		}
		if (node.getLabel() == null) {
			return Mono.just(Either.left(new InputParametersError("Input parameter node.label is null")));
		}
		if (properties == null || properties.isEmpty()) {
			return Mono.just(Either.left(new InputParametersError("Input parameter properties is null or empty")));
		}

		String query = String.format("MATCH (node:%s {uuid: $nodeUuid}) WITH node, $properties AS propsMap " +
				" WITH node, propsMap, [key IN keys(propsMap) WHERE node[key] IS NULL OR node[key] <> propsMap[key]] AS diffKeys "
				+ "CALL(node, diffKeys) {WITH node, diffKeys WHERE size(diffKeys) = 0 RETURN node AS result} RETURN result "
				+ "UNION ALL "
				+ "MATCH (node:%s {uuid: $nodeUuid}) WITH node, $properties AS propsMap "
				+ " WITH node, propsMap, [key IN keys(propsMap) WHERE node[key] IS NULL OR node[key] <> propsMap[key]] AS diffKeys "
				+ "CALL(node, propsMap, diffKeys) {WITH node, propsMap, diffKeys "
				+ "WHERE size(diffKeys) > 0 "
				+ "CALL apoc.refactor.cloneNodes([node], TRUE, [\"uuid\"]) YIELD output AS newNode "
				+ "FOREACH (key IN diffKeys | SET newNode[key] = propsMap[key]) SET newNode.uuid = randomUUID(), newNode.updatedAt = datetime({ timezone: '+03:00' }), newNode.updatedBy = $updatedBy "
				+ "CREATE (newNode)-[:PREVIOUS_VERSION]->(node) "
				+ "WITH node, labels(node) AS oldLabels, newNode REMOVE node:$(oldLabels) WITH node, newNode, [label IN oldLabels | label + '_VERSIONED'] AS newLabels SET node:$(newLabels) RETURN newNode AS result} RETURN result",
				node.getLabel(), node.getLabel());

		Map<String, Object> parameters = Map.of("nodeUuid", node.getUuid(), "properties", node.getPropertiesMap(),
				"updatedBy",
				user != null ? user.getUuid() : "");
		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);
		String emptyResultError = "No properties updated for node with uuid: " + node.getUuid() + " and label: "
				+ node.getLabel() + ": No properties updated";
		String errorMessage = "Failed while query execution while updating node properties with uuid: " + node.getUuid()
				+ " and label: " + node.getLabel();

		return executeWriteMono(query, parameters, nodeResultEitherMapper(nodeClass), emptyResultError, errorMessage);
	}

	protected <N extends AbstractNode> Mono<Either<DatabaseException, N>> createNode(N node, AbstractUser user,
			Class<N> nodeClass) {

		// Fast-fail validation (non-reactive)
		if (node == null) {
			return Mono.just(Either.left(new InputParametersError("Input parameter node is null")));
		}
		if (node.getUuid() != null) {
			return Mono.just(Either.left(new InputParametersError(
					"Input parameter node.uuid is not null. Use updateNodeProperties instead of createNode to update node properties.")));
		}
		if (node.getLabel() == null) {
			return Mono.just(Either.left(new InputParametersError("Input parameter node.label is null")));
		}

		String query = String.format(
				"CREATE (node:%s {uuid: randomUUID(), createdAt: datetime({ timezone: '+03:00' }), createdBy: $createdBy %s}) RETURN node AS result",
				node.getLabel(),
				node.getPropertiesQuery());

		Map<String, Object> parameters = new HashMap<>(node.getPropertiesMap());
		parameters.put("createdBy", user != null ? user.getUuid() : "");

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No node created with uuid: " + node.getUuid() + " and label: " + node.getLabel()
				+ ": No node created";
		String errorMessage = "Failed while query execution while creating node with uuid: " + node.getUuid()
				+ " and label: " + node.getLabel();

		return executeWriteMono(query, parameters, nodeResultEitherMapper(nodeClass), emptyResultError, errorMessage);
	}

	protected <N extends AbstractNode> Mono<Either<DatabaseException, Boolean>> deleteNode(N node, AbstractUser user) {
		// Check if the node is null
		if (node == null)
			return Mono.just(Either.left(new InputParametersError("Input parameter node is null")));
		if (node.getUuid() == null)
			return Mono.just(Either.left(new InputParametersError("Input parameter node.uuid is null")));

		log.debug("Deleting node with uuid: {} and label: {}", node.getUuid(), node.getLabel());

		String query = String.format(
				"MATCH (n {uuid: $uuid}) WITH n, labels(n) AS oldLabels "
						+
						"REMOVE n:$(oldLabels) WITH n, oldLabels, [label IN oldLabels | label + '_DELETED'] AS newLabels "
						+
						" SET n:$(newLabels) SET n.deletedAt = datetime({ timezone: '+03:00' }), n.deletedBy = $deletedBy "
						+
						" RETURN labels(n) AS result");

		Map<String, Object> parameters = Map.of("uuid", node.getUuid(), "deletedBy",
				user != null ? user.getUuid() : "");
		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No node deleted with uuid: " + node.getUuid() + ": No node deleted";
		String errorMessage = "Failed while query execution while deleting node with uuid: " + node.getUuid();

		Function<Record, Either<DatabaseException, Boolean>> mapper = record -> {
			List<String> labels = listStringResultMapper.apply(record);
			return Either.<DatabaseException, Boolean>right(
					labels.stream().allMatch(label -> label.endsWith(DELETED_NODE_LABEL_POSTFIX)));
		};
		return executeWriteMono(query, parameters, mapper, emptyResultError, errorMessage);
	}

	protected <N extends AbstractNode> Mono<Either<DatabaseException, N>> findNodeByUniquePropertyValue(
			String propertyName,
			Object propertyValue,
			String nodeLabel, Class<N> nodeClass) {

		log.debug("Finding node with property: {} value: {} node label: {}", propertyName, propertyValue, nodeLabel);

		final String query = nodeLabel == null
				? String.format("MATCH (node {%s: $propertyValue}) RETURN node AS result", propertyName)
				: String.format("MATCH (node:%s {%s: $propertyValue}) RETURN node AS result", nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No node found with property: " + propertyName + " value: " + propertyValue
				+ " node label: " + nodeLabel + ": No node found";
		String errorMessage = "Failed while query execution while finding node with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadMono(query, parameters, nodeResultEitherMapper(nodeClass), emptyResultError, errorMessage);
	}

	protected <N extends AbstractNode> Mono<Either<DatabaseException, N>> findNodeByUuid(String uuid,
			String nodeLabel, Class<N> nodeClass) {
		log.debug("Finding node with uuid: {} and label: {}", uuid, nodeLabel);
		return findNodeByUniquePropertyValue("uuid", uuid, nodeLabel, nodeClass);
	}

	protected <N extends AbstractNode, T extends AbstractNode> Mono<Either<DatabaseException, N>> findNodeByPropertyValueAndTargetNode(
			String propertyName, Object propertyValue, String nodeLabel, Class<N> nodeClass, T targetNode,
			String relationshipType) {
		log.debug(
				"Finding node with property: {} value: {} node label: {} connected to node: {} with relationship type: {}",
				propertyName, propertyValue, nodeLabel, targetNode.getUuid(), relationshipType);
		final String query = nodeLabel == null ? String.format(
				"MATCH (node {%s: $propertyValue})-[:%s]->(connectedToNode {uuid: $connectedToNodeUuid}) RETURN node AS result",
				propertyName, relationshipType)
				: String.format(
						"MATCH (node:%s {%s: $propertyValue})-[:%s]->(connectedToNode {uuid: $connectedToNodeUuid}) RETURN node AS result",
						nodeLabel, propertyName, relationshipType);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue, "connectedToNodeUuid",
				targetNode.getUuid());

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No node found with property: " + propertyName + " value: " + propertyValue
				+ " node label: " + nodeLabel + " connected to node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType + ": No node found";
		String errorMessage = "Failed while query execution while finding node with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel + " connected to node: "
				+ targetNode.getUuid() + " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters, nodeResultEitherMapper(nodeClass), emptyResultError, errorMessage);
	}

	protected <N extends AbstractNode> Flux<Either<DatabaseException, N>> findNodesByPropertyValue(
			String propertyName, Object propertyValue, String nodeLabel, Class<N> nodeClass) {
		log.debug("Finding nodes with property: {} value: {} node label: {}", propertyName, propertyValue, nodeLabel);

		final String query = nodeLabel == null
				? String.format("MATCH (node {%s: $propertyValue}) RETURN node AS result", propertyName)
				: String.format("MATCH (node:%s {%s: $propertyValue}) RETURN node AS result", nodeLabel, propertyName);

		Map<String, Object> parameters = Map.of("propertyValue", propertyValue);

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No nodes found with property: " + propertyName + " value: " + propertyValue
				+ " node label: " + nodeLabel + ": No nodes found";
		String errorMessage = "Failed while query execution while finding nodes with property: " + propertyName
				+ " value: " + propertyValue + " node label: " + nodeLabel;

		return executeReadFlux(query, parameters, nodeResultEitherMapper(nodeClass), emptyResultError, errorMessage);
	}

	// RELATIONSHIPS

	// Unique relationship is relationship from one node to another node, there can
	// be only one relationship of this type between the two nodes.

	protected <S extends AbstractNode, T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> isRelationshipExists(
			S sourceNode, T targetNode, String relationshipType) {
		log.debug(
				"Checking if relationship exists between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);
		if (sourceNode == null || targetNode == null || relationshipType == null)
			return Mono.just(Either
					.left(new InputParametersError("Input parameters fromNode, toNode or relationshipType are null")));

		String query = String.format(
				"MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target:%s {uuid: $targetNodeUuid}) RETURN COALESCE(count(r), 0)>0 as result",
				sourceNode.getLabel(), relationshipType, targetNode.getLabel());
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(), "targetNodeUuid",
				targetNode.getUuid());

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No relationship found between source node: " + sourceNode.getUuid()
				+ " and target node: " + targetNode.getUuid() + " with relationship type: " + relationshipType
				+ ": No relationship found";
		String errorMessage = "Failed while query execution while checking if relationship exists between source node: "
				+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	}

	protected <S extends AbstractNode> Mono<Either<DatabaseException, Boolean>> isAnyOutgoingRelationshipsExists(
			S sourceNode,
			String relationshipType) {
		if (sourceNode == null || relationshipType == null)
			return Mono.just(Either
					.left(new InputParametersError("Input parameters sourceNode or relationshipType are null")));

		log.debug("Checking if any outgoing relationships exists from source node: {} with relationship type: {}",
				sourceNode.getUuid(), relationshipType);

		String query = String.format(
				"MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target) RETURN COALESCE(count(r), 0)>0 as result",
				sourceNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid());

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);
		String emptyResultError = "No any outgoing relationships found from source node: " + sourceNode.getUuid()
				+ " with relationship type: " + relationshipType + ": No any outgoing relationships found";
		String errorMessage = "Failed while query execution while checking if any outgoing relationships exists from source node: "
				+ sourceNode.getUuid() + " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	}

	protected <T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> isAnyIncomingRelationshipsExists(
			T targetNode,
			String relationshipType) {
		if (targetNode == null || relationshipType == null)
			return Mono.just(Either
					.left(new InputParametersError("Input parameters targetNode or relationshipType are null")));

		log.debug("Checking if any incoming relationships exists to target node: {} with relationship type: {}",
				targetNode.getUuid(), relationshipType);

		String query = String.format(
				"MATCH (target:%s {uuid: $targetNodeUuid})<-[r:%s]-(source) RETURN COALESCE(count(r), 0)>0 as result",
				targetNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("targetNodeUuid", targetNode.getUuid());

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No any incoming relationships found to target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType + ": No any incoming relationships found";
		String errorMessage = "Failed while query execution while checking if any incoming relationships exists to target node: "
				+ targetNode.getUuid() + " with relationship type: " + relationshipType;

		return executeReadMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	}

	/*
	 * Create a single relationship between two nodes. There are many relationships
	 * from one node to anothers with the same relationship type.
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> createSingleRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user) {

		if (sourceNode == null || targetNode == null || relationshipType == null)
			return Mono.just(Either
					.left(new InputParametersError(
							"Input parameters sourceNode, targetNode or relationshipType are null")));

		log.debug("Creating single relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		String query = String.format(
				"MATCH (source:%s {uuid: $sourceNodeUuid}) MATCH (target:%s {uuid: $targetNodeUuid}) WHERE NOT EXISTS {(source)-[:%s]->(target)} CREATE (source)-[r:%s {createdAt: datetime({ timezone: '+03:00' }), createdBy: $createdBy}]->(target) RETURN COALESCE(count(r), 0)>0 as result",
				sourceNode.getLabel(), targetNode.getLabel(), relationshipType, relationshipType);

		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(), "targetNodeUuid",
				targetNode.getUuid(),
				"createdBy", user != null ? user.getUuid() : "");

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No single relationship created between source node: " + sourceNode.getUuid()
				+ " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType
				+ ": No single relationship created";
		String errorMessage = "Failed while query execution while creating single relationship between source node: "
				+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;

		return executeWriteMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	}

	/*
	 * Delete a single relationship between two nodes.
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> deleteSingleRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user) {
		return Mono.defer(() -> isRelationshipExists(sourceNode, targetNode, relationshipType))
				.flatMap(existsResult -> existsResult.fold(
						error -> Mono.just(Either.left(error)),
						exists -> {
							if (!exists) {
								return Mono.just(Either.right(true));
							}
							log.debug(
									"Deleting single relationship between source node: {} and target node: {} with relationship type: {}",
									sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

							String query = String.format(
									"MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target:%s {uuid: $targetNodeUuid}) CALL (source, r, target) { WITH source, r, target CREATE (source)-[r2:%s] ->(target) SET r2 = properties(r), r2.deletedAt = datetime({ timezone: '+03:00' }), r2.deletedBy = $deletedBy DELETE r } RETURN COALESCE(count(*), 0)>0 as result",
									sourceNode.getLabel(), relationshipType, targetNode.getLabel(),
									relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX);
							Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(),
									"targetNodeUuid",
									targetNode.getUuid(),
									"deletedBy", user != null ? user.getUuid() : "");

							log.debug("Query: {}", query);
							log.debug("Parameters: {}", parameters);

							String emptyResultError = "No single relationship deleted between source node: "
									+ sourceNode.getUuid()
									+ " and target node: " + targetNode.getUuid()
									+ " with relationship type: " + relationshipType
									+ ": No single relationship deleted";
							String errorMessage = "Failed while query execution while deleting single relationship between source node: "
									+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
									+ " with relationship type: " + relationshipType;
							return executeWriteMono(query, parameters, booleanResultEitherMapper, emptyResultError,
									errorMessage);
						}));
	}

	/*
	 * Delete all outgoing relationships with RelationshipType from a node
	 */
	protected <S extends AbstractNode> Mono<Either<DatabaseException, Boolean>> deleteAllOutgoingRelationshipsWithRelationshipType(
			S sourceNode, String relationshipType, AbstractUser user) {
		log.debug("Deleting all outgoing relationships with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		String query = String.format(
				"MATCH (source:%s {uuid: $sourceNodeUuid})-[r:%s]->(target) CALL (source, r, target) { WITH source, r, target CREATE (source)-[r2:%s]->(target) SET r2 = properties(r), r2.deletedAt = datetime({ timezone: '+03:00' }), r2.deletedBy = $deletedBy DELETE r } RETURN COALESCE(count(*), 0)>0 as result",
				sourceNode.getLabel(), relationshipType, relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(),
				"deletedBy", user != null ? user.getUuid() : "");

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No all outgoing relationships deleted from source node: " + sourceNode.getUuid()
				+ " with relationship type: " + relationshipType + ": No all outgoing relationships deleted";
		String errorMessage = "Failed while query execution while deleting all outgoing relationships with relationship type: "
				+ relationshipType + " from source node: " + sourceNode.getUuid();

		return executeWriteMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	}

	/*
	 * Delete all outgoing relationships with RelationshipType from a node
	 */
	protected <T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> deleteAllIncomingRelationshipsWithRelationshipType(
			T targetNode, String relationshipType, AbstractUser user) {
		log.debug("Deleting all incoming relationships with relationship type: {} to target node: {}",
				relationshipType, targetNode.getUuid());

		String query = String.format(
				"MATCH (source)-[r:%s]->(target:%s {uuid: $targetNodeUuid}) CALL (source, r, target) { WITH source, r, target CREATE (source)-[r2:%s]->(target) SET r2 = properties(r), r2.deletedAt = datetime({ timezone: '+03:00' }), r2.deletedBy = $deletedBy DELETE r } RETURN COALESCE(count(*), 0)>0 as result",
				relationshipType, targetNode.getLabel(), relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX);
		Map<String, Object> parameters = Map.of("targetNodeUuid", targetNode.getUuid(),
				"deletedBy", user != null ? user.getUuid() : "");

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No all incoming relationships deleted to target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType + ": No all incoming relationships deleted";
		String errorMessage = "Failed while query execution while deleting all incoming relationships with relationship type: "
				+ relationshipType + " to target node: " + targetNode.getUuid();

		return executeWriteMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	}

	/*
	 * Create a unique relationship between two nodes. There can be only one
	 * relationship of this type from fromNode. It uses for set a property for
	 * entity.
	 * 
	 * <img src="doc-files/test.png" alt="Test image" />
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> createUniqueTargetedRelationship(
			S sourceNode,
			T targetNode, String relationshipType, AbstractUser user) {
		log.debug("Creating unique relationship between source node: {} and target node: {} with relationship type: {}",
				sourceNode.getUuid(), targetNode.getUuid(), relationshipType);

		String query = String.format(
				"MATCH (source:%s {uuid: $sourceNodeUuid}) "
						+ "OPTIONAL MATCH (source)-[r:%s]->(target:%s) WHERE target.uuid <> $targetNodeUuid "
						+ "WITH source, r, target "
						+ "CALL (source, r, target) { WITH source, r, target WHERE target IS NOT NULL "
						+ "CREATE (source)-[r2:%s]->(target) SET r2 = properties(r), r2.deletedAt = datetime({ timezone: '+03:00' }), r2.deletedBy = $deletedBy DELETE r } "
						+ "WITH (source) MATCH (t:%s {uuid:$targetNodeUuid}) "
						+ "MERGE (source)-[nr:%s]->(t) ON CREATE SET nr.createdAt = datetime({timezone: '+03:00'}), nr.createdBy = $createdBy "
						+ "RETURN COALESCE(count(nr), 0)>0 as result",
				sourceNode.getLabel(), relationshipType, targetNode.getLabel(),
				relationshipType + DELETED_RELATIONSHIP_TYPE_POSTFIX,
				targetNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid(), "targetNodeUuid",
				targetNode.getUuid(), "deletedBy", user != null ? user.getUuid() : "", "createdBy",
				user != null ? user.getUuid() : "");

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No unique relationship created between source node: " + sourceNode.getUuid()
				+ " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType
				+ ": No unique relationship created";
		String errorMessage = "Failed while query execution while creating unique relationship between source node: "
				+ sourceNode.getUuid() + " and target node: " + targetNode.getUuid()
				+ " with relationship type: " + relationshipType;

		return executeWriteMono(query, parameters, booleanResultEitherMapper, emptyResultError, errorMessage);
	};

	/*
	 * Get all toNode with a given relationshipType from a given fromNode.
	 * 
	 * (fromNode)-[r:relationshipType]->(toNode) RETURN toNode
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Flux<Either<DatabaseException, T>> getAllTargetNodesWithRelationshipType(
			S sourceNode, String relationshipType, Class<T> targetNodeClass) {
		log.debug("Getting all target nodes with relationship type: {} from source node: {}",
				relationshipType, sourceNode.getUuid());

		String query = String.format(
				"MATCH (sourceNode:%s {uuid: $sourceNodeUuid})-[r:%s]->(targetNode) RETURN targetNode AS result",
				sourceNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("sourceNodeUuid", sourceNode.getUuid());

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No target nodes found with relationship type: " + relationshipType
				+ " from source node: "
				+ sourceNode.getUuid() + ": No target nodes found";
		String errorMessage = "Failed while query execution while finding target nodes with relationship type: "
				+ relationshipType + " from source node: " + sourceNode.getUuid();

		return executeReadFlux(query, parameters, nodeResultEitherMapper(targetNodeClass), emptyResultError,
				errorMessage);
	}

	protected <T extends AbstractNode, S extends AbstractNode> Flux<Either<DatabaseException, S>> getAllSourceNodesWithRelationshipType(
			T targetNode,
			String relationshipType, Class<S> sourceNodeClass) {

		log.debug("Getting all source nodes with relationship type: {} to target node: {}",
				relationshipType, targetNode.getUuid());

		String query = String.format(
				"MATCH (targetNode:%s {uuid: $targetNodeUuid})<-[r:%s]-(sourceNode) RETURN sourceNode AS result",
				targetNode.getLabel(), relationshipType);
		Map<String, Object> parameters = Map.of("targetNodeUuid", targetNode.getUuid());

		log.debug("Query: {}", query);
		log.debug("Parameters: {}", parameters);

		String emptyResultError = "No source nodes found with relationship type: " + relationshipType
				+ " to target node: "
				+ targetNode.getUuid() + ": No source nodes found";
		String errorMessage = "Failed while query execution while finding source nodes with relationship type: "
				+ relationshipType + " to target node: " + targetNode.getUuid();

		return executeReadFlux(query, parameters, nodeResultEitherMapper(sourceNodeClass), emptyResultError,
				errorMessage);
	}

	/*
	 * Create unique outgoing relationships between a list of fromNodes and a list
	 * of
	 * toNodes.
	 * 
	 * (fromNode)-[r:relationshipType]->(toNode)
	 * 
	 * @param fromNode
	 * 
	 * @param toNodes
	 * 
	 * @param relationshipType
	 * 
	 * @param user
	 * 
	 * @return
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> createUniqueOutgoingRelationships(
			S sourceNode, List<T> targetNodes, Class<T> targetNodeClass, String relationshipType, AbstractUser user) {

		if (sourceNode == null || targetNodes == null || relationshipType == null)
			return Mono.just(Either.left(
					new InputParametersError("Input parameters sourceNode, targetNodes or relationshipType are null")));

		return getAllTargetNodesWithRelationshipType(sourceNode, relationshipType, targetNodeClass)
				.flatMap(e -> e.fold(err -> Mono.empty(), Mono::just))
				.collectList()
				.flatMap(existingNodes -> {

					List<T> toDeleteNodes = existingNodes.stream()
							.filter(exNode -> targetNodes.stream()
									.noneMatch(toNode -> toNode.getUuid().equals(exNode.getUuid())))
							.collect(Collectors.toList());

					List<T> toCreateNodes = targetNodes.stream()
							.filter(toNode -> existingNodes.stream()
									.noneMatch(exNode -> exNode.getUuid().equals(toNode.getUuid())))
							.collect(Collectors.toList());

					Mono<Void> deleteMono = Flux.fromIterable(toDeleteNodes)
							.flatMap(node -> deleteSingleRelationship(sourceNode, node, relationshipType, user)
									.flatMap(result -> result.<Mono<Boolean>>fold(
											err -> Mono.error(new RuntimeException(err.message())),
											ok -> Mono.just(ok))))
							.then();
					Mono<Void> createMono = Flux.fromIterable(toCreateNodes)
							.flatMap(node -> createSingleRelationship(sourceNode, node, relationshipType, user)
									.flatMap(result -> result.<Mono<Boolean>>fold(
											err -> Mono.error(new RuntimeException(err.message())),
											ok -> Mono.just(ok))))
							.then();

					return deleteMono.then(createMono).thenReturn(Either.<DatabaseException, Boolean>right(
							true))
							.onErrorResume(err -> Mono
									.just(Either.left(new Neo4jInternalError(err.getMessage()))));
				});
	}

	/*
	 * Create unique incoming relationships between a list of toNodes and a list
	 * of
	 * toNodes.
	 * 
	 * (fromNode)-[r:relationshipType]->(toNode)
	 * 
	 * @param fromNode
	 * 
	 * @param toNodes
	 * 
	 * @param relationshipType
	 * 
	 * @param user
	 * 
	 * @return
	 */
	protected <S extends AbstractNode, T extends AbstractNode> Mono<Either<DatabaseException, Boolean>> createUniqueIncomingRelationships(
			T targetNode,
			List<S> sourceNodes, Class<S> sourceNodeClass, String relationshipType, AbstractUser user) {

		if (targetNode == null || sourceNodes == null || relationshipType == null)
			return Mono.just(Either.left(
					new InputParametersError("Input parameters targetNode, sourceNodes or relationshipType are null")));

		return getAllSourceNodesWithRelationshipType(targetNode, relationshipType, sourceNodeClass)
				.flatMap(e -> e.fold(err -> Mono.empty(), Mono::just))
				.collectList()
				.flatMap(existingNodes -> {

					List<S> toDeleteNodes = existingNodes.stream()
							.filter(exNode -> sourceNodes.stream()
									.noneMatch(toNode -> toNode.getUuid().equals(exNode.getUuid())))
							.collect(Collectors.toList());

					List<S> toCreateNodes = sourceNodes.stream()
							.filter(sNode -> existingNodes.stream()
									.noneMatch(exNode -> exNode.getUuid().equals(sNode.getUuid())))
							.collect(Collectors.toList());

					Mono<Void> deleteMono = Flux.fromIterable(toDeleteNodes)
							.flatMap(node -> deleteSingleRelationship(node, targetNode, relationshipType, user)
									.flatMap(result -> result.<Mono<Boolean>>fold(
											err -> Mono.error(new RuntimeException(err.message())),
											ok -> Mono.just(ok))))
							.then();
					Mono<Void> createMono = Flux.fromIterable(toCreateNodes)
							.flatMap(node -> createSingleRelationship(node, targetNode, relationshipType, user)
									.flatMap(result -> result.<Mono<Boolean>>fold(
											err -> Mono.error(new RuntimeException(err.message())),
											ok -> Mono.just(ok))))
							.then();

					return deleteMono.then(createMono).thenReturn(Either.<DatabaseException, Boolean>right(
							true))
							.onErrorResume(err -> Mono
									.just(Either.left(new Neo4jInternalError(err.getMessage()))));
				});
	}

}
