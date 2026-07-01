package com.colligendis.server.parser.numista.collection;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.NumistaCollectionItem;
import com.colligendis.server.database.numista.service.AcquisitionPlaceService;
import com.colligendis.server.database.numista.service.NumistaCollectionItemService;
import com.colligendis.server.database.numista.service.StorageLocationService;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.logger.BaseLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumistaCollectionSaveService {

	private final NumistaCollectionClient collectionClient;
	private final NumistaCollectionSaveResponseParser responseParser;
	private final NumistaCollectionItemService collectionItemService;
	private final AcquisitionPlaceService acquisitionPlaceService;
	private final StorageLocationService storageLocationService;

	/**
	 * Posts the item to Numista, parses the returned HTML row, and persists request
	 * and response fields in Neo4j.
	 */
	public Mono<ExecutionResult<NumistaCollectionItem, ExecutionStatuses>> saveToNumistaAndDatabase(
			NumistaCollectionSaveRequest request,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(user -> applyResolvedPlacesOnRequest(request, user, baseLogger)
						.flatMap(resolvedRequest -> collectionClient.saveCollectionItem(resolvedRequest, user)
								.flatMap(html -> persistAfterNumistaResponse(
										resolvedRequest, html, Mono.just(user), baseLogger))));
	}

	private Mono<NumistaCollectionSaveRequest> applyResolvedPlacesOnRequest(
			NumistaCollectionSaveRequest request,
			ColligendisUser user,
			BaseLogger baseLogger) {
		return acquisitionPlaceService.applyResolvedAcquisitionPlace(request, user, baseLogger)
				.flatMap(r -> storageLocationService.applyResolvedStorageLocation(r, user, baseLogger));
	}

	private Mono<ExecutionResult<NumistaCollectionItem, ExecutionStatuses>> persistAfterNumistaResponse(
			NumistaCollectionSaveRequest request,
			String html,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(html)) {
			log.warn("Empty Numista collection save response for coinId={}", request.getCoinId());
			return resolveLinkedNodesOnItem(request, null, colligendisUserMono, baseLogger)
					.flatMap(item -> collectionItemService.saveOrUpdate(item, colligendisUserMono, baseLogger));
		}

		NumistaCollectionSaveResponse response = responseParser.parse(html);
		if (response == null) {
			log.warn("Could not parse Numista collection save response for coinId={}", request.getCoinId());
		}

		return resolveLinkedNodesOnItem(request, response, colligendisUserMono, baseLogger)
				.flatMap(item -> collectionItemService.saveOrUpdate(item, colligendisUserMono, baseLogger))
				.map(result -> {
					if (response == null && result.getNode() != null) {
						log.info("Saved collection item from request only (unparsed response), uuid={}",
								result.getNode().getUuid());
					}
					return result;
				});
	}

	private Mono<NumistaCollectionItem> resolveLinkedNodesOnItem(
			NumistaCollectionSaveRequest request,
			NumistaCollectionSaveResponse response,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		NumistaCollectionItem item = NumistaCollectionItem.fromRequestAndResponse(request, response);
		return resolveAcquisitionPlaceOnItem(item, request, response, colligendisUserMono, baseLogger)
				.flatMap(resolved -> resolveStorageLocationOnItem(resolved, request, response, colligendisUserMono, baseLogger));
	}

	private Mono<NumistaCollectionItem> resolveAcquisitionPlaceOnItem(
			NumistaCollectionItem item,
			NumistaCollectionSaveRequest request,
			NumistaCollectionSaveResponse response,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		if (item.getAcquisitionPlace() != null) {
			return Mono.just(item);
		}
		String placeName = request != null ? request.getAcquisitionPlace() : null;
		if (!StringUtils.hasText(placeName) && response != null) {
			placeName = response.getAcquisitionPlace();
		}
		if (!StringUtils.hasText(placeName)) {
			return Mono.just(item);
		}
		final String resolvedName = placeName;
		return colligendisUserMono.flatMap(user -> acquisitionPlaceService
				.findOrCreateForUser(user, null, resolvedName, baseLogger)
				.map(place -> {
					item.setAcquisitionPlace(place);
					return item;
				})
				.defaultIfEmpty(item));
	}

	private Mono<NumistaCollectionItem> resolveStorageLocationOnItem(
			NumistaCollectionItem item,
			NumistaCollectionSaveRequest request,
			NumistaCollectionSaveResponse response,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		if (item.getStorageLocation() != null) {
			return Mono.just(item);
		}
		String locationName = request != null ? request.getStorageLocation() : null;
		if (!StringUtils.hasText(locationName) && response != null) {
			locationName = response.getStorageLocation();
		}
		if (!StringUtils.hasText(locationName)) {
			return Mono.just(item);
		}
		final String resolvedName = locationName;
		return colligendisUserMono.flatMap(user -> storageLocationService
				.findOrCreateForUser(user, null, resolvedName, baseLogger)
				.map(location -> {
					item.setStorageLocation(location);
					return item;
				})
				.defaultIfEmpty(item));
	}

	public Mono<ExecutionResult<NumistaCollectionItem, ExecutionStatuses>> persistToDatabaseOnly(
			NumistaCollectionSaveRequest request,
			String responseHtml,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return persistAfterNumistaResponse(request, responseHtml, colligendisUserMono, baseLogger);
	}

	public Mono<ExecutionResult<NumistaCollectionItem, ExecutionStatuses>> persistParsedResponse(
			NumistaCollectionSaveResponse response,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		if (response == null) {
			return Mono.empty();
		}
		return resolveLinkedNodesOnItem(null, response, colligendisUserMono, baseLogger)
				.flatMap(item -> collectionItemService.saveOrUpdate(item, colligendisUserMono, baseLogger));
	}
}
