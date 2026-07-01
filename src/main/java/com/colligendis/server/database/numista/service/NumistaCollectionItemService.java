package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.AcquisitionPlace;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.database.numista.model.StorageLocation;
import com.colligendis.server.database.numista.model.NumistaCollectionItem;
import com.colligendis.server.database.numista.model.Variant;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.ExecutionStatusCoercion;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class NumistaCollectionItemService extends AbstractService {

	private final NTypeService nTypeService;
	private final VariantService variantService;

	public NumistaCollectionItemService(NTypeService nTypeService, VariantService variantService) {
		this.nTypeService = nTypeService;
		this.variantService = variantService;
	}

	public Mono<ExecutionResult<NumistaCollectionItem, CreateNodeExecutionStatus>> create(
			NumistaCollectionItem item,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(user -> super.createNode(item, user, NumistaCollectionItem.class, baseLogger));
	}

	public Mono<ExecutionResult<NumistaCollectionItem, UpdateExecutionStatus>> update(
			NumistaCollectionItem item,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(user -> super.updateNodeProperties(item, user, NumistaCollectionItem.class, baseLogger));
	}

	public Flux<NumistaCollectionItem> findByUserAndNtypeNids(
			ColligendisUser user,
			List<String> ntypeNids,
			BaseLogger baseLogger) {
		if (user == null || !StringUtils.hasText(user.getUuid()) || ntypeNids == null || ntypeNids.isEmpty()) {
			return Flux.empty();
		}
		String cypher = """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_IN_COLLECTION]->(item:NUMISTA_COLLECTION_ITEM)-[:FOR_NTYPE]->(n:NTYPE)
				WHERE n.nid IN $ntypeNids
				OPTIONAL MATCH (item)-[:ACQUISITION_IN]->(ap)
				OPTIONAL MATCH (item)-[:STORAGE_IN]->(sl)
				RETURN item AS result, ap AS acquisitionPlace, sl AS storageLocation
				ORDER BY item.createdAt DESC
				""";

		return Mono.fromCallable(() -> {
			try (Session session = openBlockingSession()) {
				Result result = session.run(cypher, java.util.Map.of(
						"userUuid", user.getUuid(),
						"ntypeNids", ntypeNids));
				return loadCollectionItemsFromRecords(result, baseLogger);
			}
		}).flatMapMany(Flux::fromIterable);
	}

	public Flux<NumistaCollectionItem> findByCoinIdAndVersionId(
			ColligendisUser user,
			String coinId,
			String versionId,
			BaseLogger baseLogger) {
		if (user == null || !StringUtils.hasText(user.getUuid())) {
			return Flux.empty();
		}
		String cypher = """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_IN_COLLECTION]->(item:NUMISTA_COLLECTION_ITEM)
				WHERE item.coinId = $coinId AND item.versionId = $versionId
				OPTIONAL MATCH (item)-[:ACQUISITION_IN]->(ap)
				OPTIONAL MATCH (item)-[:STORAGE_IN]->(sl)
				RETURN item AS result, ap AS acquisitionPlace, sl AS storageLocation
				ORDER BY item.createdAt DESC
				""";

		return Mono.fromCallable(() -> {
			try (Session session = openBlockingSession()) {
				Result result = session.run(cypher, java.util.Map.of(
						"userUuid", user.getUuid(),
						"coinId", coinId,
						"versionId", versionId));
				return loadCollectionItemsFromRecords(result, baseLogger);
			}
		}).flatMapMany(Flux::fromIterable);
	}

	/**
	 * Resolves a collection row for delete: by Numista item id, then by coin + variant + id.
	 */
	public Mono<NumistaCollectionItem> findForDelete(
			ColligendisUser user,
			String coinId,
			String versionId,
			String numistaCollectionItemId,
			BaseLogger baseLogger) {
		if (user == null || !StringUtils.hasText(user.getUuid())) {
			return Mono.empty();
		}
		String normalizedId = normalize(numistaCollectionItemId);
		return findLinkedToUserByNumistaCollectionItemId(user, normalizedId, baseLogger)
				.flatMap(item -> matchesCoinAndVersion(item, coinId, versionId)
						? Mono.just(item)
						: Mono.empty())
				.switchIfEmpty(
						findByCoinIdAndVersionId(user, coinId, versionId, baseLogger)
								.filter(item -> matchesNumistaCollectionItemId(item, normalizedId))
								.next());
	}

	public Mono<ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus>> deleteItem(
			NumistaCollectionItem item,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(item.getUuid())) {
			return Mono.just(ExecutionResult.<NumistaCollectionItem, DeleteExecutionStatus>builder()
					.status(DeleteExecutionStatus.INPUT_PARAMETERS_ERROR)
					.build());
		}
		return colligendisUserMono
				.flatMap(user -> super.deleteNode(item, user, NumistaCollectionItem.class, baseLogger))
				.map(this::coerceDeleteExecutionStatus);
	}

	public Mono<ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus>> deleteByNumistaCollectionItemId(
			String numistaCollectionItemId,
			String coinId,
			String versionId,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> findForDelete(user, coinId, versionId, numistaCollectionItemId, baseLogger)
				.flatMap(item -> deleteItem(item, Mono.just(user), baseLogger))
				.switchIfEmpty(Mono.just(ExecutionResult.<NumistaCollectionItem, DeleteExecutionStatus>builder()
						.status(DeleteExecutionStatus.NOT_FOUND)
						.build())));
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private static boolean matchesCoinAndVersion(NumistaCollectionItem item, String coinId, String versionId) {
		return Objects.equals(normalize(coinId), normalize(item.getCoinId()))
				&& Objects.equals(normalize(versionId), normalize(item.getVersionId()));
	}

	private static boolean matchesNumistaCollectionItemId(NumistaCollectionItem item, String numistaCollectionItemId) {
		return Objects.equals(normalize(numistaCollectionItemId), normalize(item.getNumistaCollectionItemId()));
	}

	private static FindExecutionStatus toFindExecutionStatus(ExecutionStatuses raw) {
		if (raw instanceof FindExecutionStatus findStatus) {
			return findStatus;
		}
		if (raw == null) {
			return FindExecutionStatus.INTERNAL_ERROR;
		}
		try {
			return FindExecutionStatus.valueOf(raw.name());
		} catch (IllegalArgumentException ex) {
			return FindExecutionStatus.INTERNAL_ERROR;
		}
	}

	private ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> coerceDeleteExecutionStatus(
			ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> result) {
		var raw = result.statusEnum();
		if (raw instanceof DeleteExecutionStatus deleteStatus) {
			return result;
		}
		DeleteExecutionStatus coerced;
		if (raw == null) {
			coerced = DeleteExecutionStatus.INTERNAL_ERROR;
		} else {
			try {
				coerced = DeleteExecutionStatus.valueOf(raw.name());
			} catch (IllegalArgumentException ex) {
				coerced = DeleteExecutionStatus.INTERNAL_ERROR;
			}
		}
		if (result.getError() != null) {
			return ExecutionResult.<NumistaCollectionItem, DeleteExecutionStatus>builder()
					.node(result.getNode())
					.error(result.getError(), coerced)
					.build();
		}
		return ExecutionResult.<NumistaCollectionItem, DeleteExecutionStatus>builder()
				.node(result.getNode())
				.status(coerced)
				.build();
	}

	public Mono<ExecutionResult<NumistaCollectionItem, FindExecutionStatus>> findByNumistaCollectionItemId(
			String numistaCollectionItemId,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue(
				"numistaCollectionItemId",
				numistaCollectionItemId,
				NumistaCollectionItem.LABEL,
				NumistaCollectionItem.class,
				baseLogger);
	}

	public Mono<ExecutionResult<NumistaCollectionItem, CreateNodeExecutionStatus>> createWithRelationships(
			NumistaCollectionItem item,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return create(item, colligendisUserMono, baseLogger)
				.flatMap(createResult -> {
					CreateNodeExecutionStatus status = ExecutionStatusCoercion.toCreateNode(createResult.statusEnum());
					if (status != CreateNodeExecutionStatus.WAS_CREATED || createResult.getNode() == null) {
						return Mono.just(replaceCreateStatus(createResult, status));
					}
					NumistaCollectionItem saved = createResult.getNode();
					copyLinkedNodesForRelationshipLinking(item, saved);
					return linkRelatedNodes(saved, colligendisUserMono, baseLogger)
							.thenReturn(replaceCreateStatus(createResult, status));
				});
	}

	private static ExecutionResult<NumistaCollectionItem, CreateNodeExecutionStatus> replaceCreateStatus(
			ExecutionResult<NumistaCollectionItem, CreateNodeExecutionStatus> result,
			CreateNodeExecutionStatus status) {
		if (result.getError() != null) {
			return ExecutionResult.<NumistaCollectionItem, CreateNodeExecutionStatus>builder()
					.node(result.getNode())
					.error(result.getError(), status)
					.build();
		}
		return ExecutionResult.<NumistaCollectionItem, CreateNodeExecutionStatus>builder()
				.node(result.getNode())
				.status(status)
				.build();
	}

	public Mono<ExecutionResult<NumistaCollectionItem, UpdateExecutionStatus>> updateWithRelationships(
			NumistaCollectionItem item,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return update(item, colligendisUserMono, baseLogger)
				.flatMap(updateResult -> {
					UpdateExecutionStatus status = ExecutionStatusCoercion.toUpdate(updateResult.statusEnum());
					if (updateResult.getNode() == null) {
						return Mono.just(replaceUpdateStatus(updateResult, status));
					}
					if (status != UpdateExecutionStatus.WAS_UPDATED
							&& status != UpdateExecutionStatus.NOTHING_TO_UPDATE) {
						return Mono.just(replaceUpdateStatus(updateResult, status));
					}
					NumistaCollectionItem saved = updateResult.getNode();
					copyLinkedNodesForRelationshipLinking(item, saved);
					return linkRelatedNodes(saved, colligendisUserMono, baseLogger)
							.thenReturn(replaceUpdateStatus(updateResult, status));
				});
	}

	/**
	 * Linked place/location nodes are not Neo4j properties; {@code createNode} /
	 * {@code updateNodeProperties} return a fresh node without those in-memory
	 * references. Copy them from the input item before creating relationships.
	 */
	private static void copyLinkedNodesForRelationshipLinking(
			NumistaCollectionItem source,
			NumistaCollectionItem target) {
		if (source.getAcquisitionPlace() != null) {
			target.setAcquisitionPlace(source.getAcquisitionPlace());
		}
		if (source.getStorageLocation() != null) {
			target.setStorageLocation(source.getStorageLocation());
		}
	}

	private static ExecutionResult<NumistaCollectionItem, UpdateExecutionStatus> replaceUpdateStatus(
			ExecutionResult<NumistaCollectionItem, UpdateExecutionStatus> result,
			UpdateExecutionStatus status) {
		if (result.getError() != null) {
			return ExecutionResult.<NumistaCollectionItem, UpdateExecutionStatus>builder()
					.node(result.getNode())
					.error(result.getError(), status)
					.build();
		}
		return ExecutionResult.<NumistaCollectionItem, UpdateExecutionStatus>builder()
				.node(result.getNode())
				.status(status)
				.build();
	}

	public Mono<ExecutionResult<NumistaCollectionItem, ExecutionStatuses>> saveOrUpdate(
			NumistaCollectionItem item,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(item.getNumistaCollectionItemId())) {
			return createWithRelationships(item, colligendisUserMono, baseLogger)
					.map(this::widenExecutionStatus);
		}
		return findByNumistaCollectionItemId(item.getNumistaCollectionItemId(), baseLogger)
				.flatMap(findResult -> {
					if (findResult.getStatus() == FindExecutionStatus.FOUND && findResult.getNode() != null) {
						item.setUuid(findResult.getNode().getUuid());
						return updateWithRelationships(item, colligendisUserMono, baseLogger)
								.map(this::widenExecutionStatus);
					}
					return createWithRelationships(item, colligendisUserMono, baseLogger)
							.map(this::widenExecutionStatus);
				});
	}

	private ExecutionResult<NumistaCollectionItem, ExecutionStatuses> widenExecutionStatus(
			ExecutionResult<NumistaCollectionItem, ? extends ExecutionStatuses> source) {
		ExecutionStatuses raw = source.statusEnum();
		ExecutionStatuses normalized = raw instanceof CreateNodeExecutionStatus
				? ExecutionStatusCoercion.toCreateNode(raw)
				: raw instanceof UpdateExecutionStatus
						? ExecutionStatusCoercion.toUpdate(raw)
						: raw;
		return new ExecutionResult<>(source.getNode(), normalized, source.getError());
	}

	private Mono<Void> linkRelatedNodes(
			NumistaCollectionItem item,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		Mono<Void> linkNType = StringUtils.hasText(item.getCoinId())
				? nTypeService.findByNid(item.getCoinId(), baseLogger)
						.flatMap(result -> {
							if (result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null) {
								return linkNType(item, result.getNode(), colligendisUserMono, baseLogger);
							}
							return Mono.empty();
						})
						.then()
				: Mono.empty();

		Mono<Void> linkVariant = StringUtils.hasText(item.getVersionId())
				? variantService.findByNid(item.getVersionId(), baseLogger)
						.flatMap(result -> {
							if (result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null) {
								return linkVariant(item, result.getNode(), colligendisUserMono, baseLogger);
							}
							return Mono.empty();
						})
						.then()
				: Mono.empty();

		Mono<Void> linkAcquisitionPlaceMono = item.getAcquisitionPlace() != null
				&& StringUtils.hasText(item.getAcquisitionPlace().getUuid())
				? linkAcquisitionPlace(item, item.getAcquisitionPlace(), colligendisUserMono, baseLogger)
						.then()
				: Mono.empty();

		Mono<Void> linkStorageLocationMono = item.getStorageLocation() != null
				&& StringUtils.hasText(item.getStorageLocation().getUuid())
				? linkStorageLocation(item, item.getStorageLocation(), colligendisUserMono, baseLogger)
						.then()
				: Mono.empty();

		Mono<Void> linkUserMono = colligendisUserMono
				.flatMap(user -> linkUserCollection(user, item, baseLogger))
				.then();

		return Mono.when(linkNType, linkVariant, linkAcquisitionPlaceMono, linkStorageLocationMono, linkUserMono);
	}

	private Mono<NumistaCollectionItem> findLinkedToUserByNumistaCollectionItemId(
			ColligendisUser user,
			String numistaCollectionItemId,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(numistaCollectionItemId)) {
			return Mono.empty();
		}
		String cypher = """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_IN_COLLECTION]->(item:NUMISTA_COLLECTION_ITEM)
				WHERE item.numistaCollectionItemId = $numistaCollectionItemId
				RETURN item AS result
				LIMIT 1
				""";
		return Mono.fromCallable(() -> {
			try (Session session = openBlockingSession()) {
				Result result = session.run(cypher, java.util.Map.of(
						"userUuid", user.getUuid(),
						"numistaCollectionItemId", numistaCollectionItemId));
				List<NumistaCollectionItem> items = loadCollectionItemsFromRecords(result, baseLogger);
				return items.isEmpty() ? null : items.get(0);
			}
		}).flatMap(item -> item == null ? Mono.empty() : Mono.just(item));
	}

	private List<NumistaCollectionItem> loadCollectionItemsFromRecords(Result result, BaseLogger baseLogger) {
		List<NumistaCollectionItem> items = new ArrayList<>();
		for (Record record : result.list()) {
			NumistaCollectionItem item = mapRecordToNode(record, NumistaCollectionItem.class, baseLogger);
			var acquisitionNode = record.get("acquisitionPlace");
			if (acquisitionNode != null && !acquisitionNode.isNull()) {
				item.setAcquisitionPlace(AbstractNode.fromPropertiesMap(
						AcquisitionPlace.class,
						acquisitionNode.asNode().asMap(value -> value.asObject())));
			}
			var storageNode = record.get("storageLocation");
			if (storageNode != null && !storageNode.isNull()) {
				item.setStorageLocation(AbstractNode.fromPropertiesMap(
						StorageLocation.class,
						storageNode.asNode().asMap(value -> value.asObject())));
			}
			items.add(item);
		}
		return items;
	}

	private Mono<Void> linkUserCollection(
			ColligendisUser user,
			NumistaCollectionItem item,
			BaseLogger baseLogger) {
		return super.createSingleRelationship(
				user, item, ColligendisUser.HAS_IN_COLLECTION, user, baseLogger)
				.flatMap(result -> {
					if (result.getStatus() == CreateRelationshipExecutionStatus.WAS_CREATED
							|| result.getStatus() == CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS) {
						return Mono.empty();
					}
					baseLogger.traceRed(
							"Failed to link collection item {} to user {}",
							item.getUuid(),
							user.getUuid());
					return Mono.empty();
				});
	}

	private Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> linkNType(
			NumistaCollectionItem item,
			NType nType,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> super.createUniqueTargetedRelationship(
				item, nType, NumistaCollectionItem.FOR_NTYPE, user, baseLogger));
	}

	private Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> linkVariant(
			NumistaCollectionItem item,
			Variant variant,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> super.createUniqueTargetedRelationship(
				item, variant, NumistaCollectionItem.FOR_VARIANT, user, baseLogger));
	}

	private Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> linkAcquisitionPlace(
			NumistaCollectionItem item,
			AcquisitionPlace acquisitionPlace,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> super.createUniqueTargetedRelationship(
				item, acquisitionPlace, NumistaCollectionItem.ACQUISITION_IN, user, baseLogger));
	}

	private Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> linkStorageLocation(
			NumistaCollectionItem item,
			StorageLocation storageLocation,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> super.createUniqueTargetedRelationship(
				item, storageLocation, NumistaCollectionItem.STORAGE_IN, user, baseLogger));
	}
}
