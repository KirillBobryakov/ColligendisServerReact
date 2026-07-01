package com.colligendis.server.database.numista.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.StorageLocation;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.collection.NumistaCollectionSaveRequest;
import com.colligendis.server.util.UnicodeNormalizer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class StorageLocationService extends AbstractService {

	public Flux<StorageLocation> listForUser(ColligendisUser user, BaseLogger baseLogger) {
		return getAllTargetNodesWithRelationshipType(
				user, ColligendisUser.HAS_STORAGE_LOCATION, StorageLocation.class, baseLogger)
				.filter(result -> result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null)
				.map(ExecutionResult::getNode)
				.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
						a.getName() != null ? a.getName() : "",
						b.getName() != null ? b.getName() : ""));
	}

	public Mono<StorageLocation> createForUser(ColligendisUser user, String name, BaseLogger baseLogger) {
		return findOrCreateForUser(user, null, name, baseLogger);
	}

	/**
	 * Resolves storage location for a collection save: existing uuid, existing name
	 * for this user, or a new location linked via {@link ColligendisUser#HAS_STORAGE_LOCATION}.
	 */
	public Mono<StorageLocation> findOrCreateForUser(
			ColligendisUser user,
			String storageLocationUuid,
			String storageLocationName,
			BaseLogger baseLogger) {
		if (StringUtils.hasText(storageLocationUuid)) {
			return findLinkedToUserByUuid(user, storageLocationUuid, baseLogger)
					.flatMap(result -> {
						if (result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null) {
							return Mono.just(result.getNode());
						}
						if (StringUtils.hasText(storageLocationName)) {
							return resolveByNormalizedName(user, storageLocationName, baseLogger);
						}
						return Mono.error(new ResponseStatusException(
								HttpStatus.BAD_REQUEST, "Storage location not found for user"));
					});
		}
		if (!StringUtils.hasText(storageLocationName)) {
			return Mono.empty();
		}
		return resolveByNormalizedName(user, storageLocationName, baseLogger);
	}

	private Mono<StorageLocation> resolveByNormalizedName(
			ColligendisUser user,
			String storageLocationName,
			BaseLogger baseLogger) {
		return findLinkedToUserByNormalizedName(user, storageLocationName, baseLogger)
				.flatMap(result -> {
					if (result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null) {
						return Mono.just(result.getNode());
					}
					if (result.getStatus() == FindExecutionStatus.MORE_THAN_ONE_FOUND) {
						return Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "Multiple storage locations match name"));
					}
					StorageLocation location = new StorageLocation(storageLocationName.trim());
					return create(location, Mono.just(user), baseLogger)
							.flatMap(createResult -> {
								if (createResult.getStatus() != CreateNodeExecutionStatus.WAS_CREATED
										|| createResult.getNode() == null) {
									return Mono.error(new ResponseStatusException(
											HttpStatus.INTERNAL_SERVER_ERROR,
											"Failed to create storage location"));
								}
								StorageLocation saved = createResult.getNode();
								return linkUserToLocation(user, saved, baseLogger).thenReturn(saved);
							});
				});
	}

	public Mono<NumistaCollectionSaveRequest> applyResolvedStorageLocation(
			NumistaCollectionSaveRequest request,
			ColligendisUser user,
			BaseLogger baseLogger) {
		if (request == null) {
			return Mono.empty();
		}
		if (!StringUtils.hasText(request.getStorageLocationUuid())
				&& !StringUtils.hasText(request.getStorageLocation())) {
			return Mono.just(request);
		}
		return findOrCreateForUser(
				user,
				request.getStorageLocationUuid(),
				request.getStorageLocation(),
				baseLogger)
				.map(location -> {
					request.setStorageLocation(location.getName());
					request.setLinkedStorageLocation(location);
					return request;
				})
				.defaultIfEmpty(request);
	}

	public Mono<ExecutionResult<StorageLocation, CreateNodeExecutionStatus>> create(
			StorageLocation location,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(user -> super.createNode(location, user, StorageLocation.class, baseLogger));
	}

	private Mono<ExecutionResult<StorageLocation, FindExecutionStatus>> findLinkedToUserByUuid(
			ColligendisUser user,
			String locationUuid,
			BaseLogger baseLogger) {
		String query = """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_STORAGE_LOCATION]->(s:STORAGE_LOCATION {uuid: $locationUuid})
				WHERE NONE(l IN labels(s) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
				WITH collect(s) AS nodes
				RETURN
				  CASE
				    WHEN size(nodes) = 0 THEN 'NOT_FOUND'
				    WHEN size(nodes) = 1 THEN 'FOUND'
				    ELSE 'MORE_THAN_ONE_FOUND'
				  END AS status,
				  CASE WHEN size(nodes) = 1 THEN nodes[0] ELSE null END AS resultNode
				""";
		Map<String, Object> parameters = Map.of(
				"userUuid", user.getUuid(),
				"locationUuid", locationUuid);

		return executeReadMono(
				query,
				parameters,
				recordToNodeAndStatusMapper(StorageLocation.class, FindExecutionStatus::valueOf, baseLogger),
				"Empty result while finding storage location by uuid",
				"Failed while finding storage location by uuid",
				baseLogger);
	}

	private Mono<ExecutionResult<StorageLocation, FindExecutionStatus>> findLinkedToUserByNormalizedName(
			ColligendisUser user,
			String name,
			BaseLogger baseLogger) {
		String query = """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_STORAGE_LOCATION]->(s:STORAGE_LOCATION)
				WHERE s.normalizedName = $normalizedName
				  AND NONE(l IN labels(s) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
				WITH collect(s) AS nodes
				RETURN
				  CASE
				    WHEN size(nodes) = 0 THEN 'NOT_FOUND'
				    WHEN size(nodes) = 1 THEN 'FOUND'
				    ELSE 'MORE_THAN_ONE_FOUND'
				  END AS status,
				  CASE WHEN size(nodes) = 1 THEN nodes[0] ELSE null END AS resultNode
				""";
		Map<String, Object> parameters = Map.of(
				"userUuid", user.getUuid(),
				"normalizedName", UnicodeNormalizer.normalize(name));

		return executeReadMono(
				query,
				parameters,
				recordToNodeAndStatusMapper(StorageLocation.class, FindExecutionStatus::valueOf, baseLogger),
				"Empty result while finding storage location by name",
				"Failed while finding storage location by name",
				baseLogger);
	}

	private Mono<Void> linkUserToLocation(
			ColligendisUser user,
			StorageLocation location,
			BaseLogger baseLogger) {
		return createSingleRelationship(user, location, ColligendisUser.HAS_STORAGE_LOCATION, user, baseLogger)
				.flatMap(result -> {
					if (result.getStatus() == CreateRelationshipExecutionStatus.WAS_CREATED
							|| result.getStatus() == CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS) {
						return Mono.empty();
					}
					return Mono.error(new ResponseStatusException(
							HttpStatus.INTERNAL_SERVER_ERROR,
							"Failed to link storage location to user"));
				});
	}
}
