package com.colligendis.server.database.numista.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.AcquisitionPlace;
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
public class AcquisitionPlaceService extends AbstractService {

	public Flux<AcquisitionPlace> listForUser(ColligendisUser user, BaseLogger baseLogger) {
		return getAllTargetNodesWithRelationshipType(
				user, ColligendisUser.HAS_ACQUISITION_PLACE, AcquisitionPlace.class, baseLogger)
				.filter(result -> result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null)
				.map(ExecutionResult::getNode)
				.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
						a.getName() != null ? a.getName() : "",
						b.getName() != null ? b.getName() : ""));
	}

	public Mono<AcquisitionPlace> createForUser(ColligendisUser user, String name, BaseLogger baseLogger) {
		return findOrCreateForUser(user, null, name, baseLogger);
	}

	/**
	 * Resolves acquisition place for a collection save: existing uuid, existing name
	 * for this user, or a new place linked via {@link ColligendisUser#HAS_ACQUISITION_PLACE}.
	 */
	public Mono<AcquisitionPlace> findOrCreateForUser(
			ColligendisUser user,
			String acquisitionPlaceUuid,
			String acquisitionPlaceName,
			BaseLogger baseLogger) {
		if (StringUtils.hasText(acquisitionPlaceUuid)) {
			return findLinkedToUserByUuid(user, acquisitionPlaceUuid, baseLogger)
					.flatMap(result -> {
						if (result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null) {
							return Mono.just(result.getNode());
						}
						if (StringUtils.hasText(acquisitionPlaceName)) {
							return resolveByNormalizedName(user, acquisitionPlaceName, baseLogger);
						}
						return Mono.error(new ResponseStatusException(
								HttpStatus.BAD_REQUEST, "Acquisition place not found for user"));
					});
		}
		if (!StringUtils.hasText(acquisitionPlaceName)) {
			return Mono.empty();
		}
		return resolveByNormalizedName(user, acquisitionPlaceName, baseLogger);
	}

	private Mono<AcquisitionPlace> resolveByNormalizedName(
			ColligendisUser user,
			String acquisitionPlaceName,
			BaseLogger baseLogger) {
		return findLinkedToUserByNormalizedName(user, acquisitionPlaceName, baseLogger)
				.flatMap(result -> {
					if (result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null) {
						return Mono.just(result.getNode());
					}
					if (result.getStatus() == FindExecutionStatus.MORE_THAN_ONE_FOUND) {
						return Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "Multiple acquisition places match name"));
					}
					AcquisitionPlace place = new AcquisitionPlace(acquisitionPlaceName.trim());
					return create(place, Mono.just(user), baseLogger)
							.flatMap(createResult -> {
								if (createResult.getStatus() != CreateNodeExecutionStatus.WAS_CREATED
										|| createResult.getNode() == null) {
									return Mono.error(new ResponseStatusException(
											HttpStatus.INTERNAL_SERVER_ERROR,
											"Failed to create acquisition place"));
								}
								AcquisitionPlace saved = createResult.getNode();
								return linkUserToPlace(user, saved, baseLogger).thenReturn(saved);
							});
				});
	}

	public Mono<NumistaCollectionSaveRequest> applyResolvedAcquisitionPlace(
			NumistaCollectionSaveRequest request,
			ColligendisUser user,
			BaseLogger baseLogger) {
		if (request == null) {
			return Mono.empty();
		}
		if (!StringUtils.hasText(request.getAcquisitionPlaceUuid())
				&& !StringUtils.hasText(request.getAcquisitionPlace())) {
			return Mono.just(request);
		}
		return findOrCreateForUser(
				user,
				request.getAcquisitionPlaceUuid(),
				request.getAcquisitionPlace(),
				baseLogger)
				.map(place -> {
					request.setAcquisitionPlace(place.getName());
					request.setLinkedAcquisitionPlace(place);
					return request;
				})
				.defaultIfEmpty(request);
	}

	public Mono<ExecutionResult<AcquisitionPlace, CreateNodeExecutionStatus>> create(
			AcquisitionPlace place,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(user -> super.createNode(place, user, AcquisitionPlace.class, baseLogger));
	}

	private Mono<ExecutionResult<AcquisitionPlace, FindExecutionStatus>> findLinkedToUserByUuid(
			ColligendisUser user,
			String placeUuid,
			BaseLogger baseLogger) {
		String query = """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_ACQUISITION_PLACE]->(p:ACQUISITION_PLACE {uuid: $placeUuid})
				WHERE NONE(l IN labels(p) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
				WITH collect(p) AS nodes
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
				"placeUuid", placeUuid);

		return executeReadMono(
				query,
				parameters,
				recordToNodeAndStatusMapper(AcquisitionPlace.class, FindExecutionStatus::valueOf, baseLogger),
				"Empty result while finding acquisition place by uuid",
				"Failed while finding acquisition place by uuid",
				baseLogger);
	}

	private Mono<ExecutionResult<AcquisitionPlace, FindExecutionStatus>> findLinkedToUserByNormalizedName(
			ColligendisUser user,
			String name,
			BaseLogger baseLogger) {
		String query = """
				MATCH (u:COLLIGENDIS_USER {uuid: $userUuid})-[:HAS_ACQUISITION_PLACE]->(p:ACQUISITION_PLACE)
				WHERE p.normalizedName = $normalizedName
				  AND NONE(l IN labels(p) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
				WITH collect(p) AS nodes
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
				recordToNodeAndStatusMapper(AcquisitionPlace.class, FindExecutionStatus::valueOf, baseLogger),
				"Empty result while finding acquisition place by name",
				"Failed while finding acquisition place by name",
				baseLogger);
	}

	private Mono<Void> linkUserToPlace(
			ColligendisUser user,
			AcquisitionPlace place,
			BaseLogger baseLogger) {
		return createSingleRelationship(user, place, ColligendisUser.HAS_ACQUISITION_PLACE, user, baseLogger)
				.flatMap(result -> {
					if (result.getStatus() == CreateRelationshipExecutionStatus.WAS_CREATED
							|| result.getStatus() == CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS) {
						return Mono.empty();
					}
					return Mono.error(new ResponseStatusException(
							HttpStatus.INTERNAL_SERVER_ERROR,
							"Failed to link acquisition place to user"));
				});
	}
}
