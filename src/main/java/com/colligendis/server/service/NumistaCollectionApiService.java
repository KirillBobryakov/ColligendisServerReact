package com.colligendis.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.NumistaCollectionItem;
import com.colligendis.server.database.numista.service.NumistaCollectionItemService;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.ExecutionStatusCoercion;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.dto.DeleteNumistaCollectionRequest;
import com.colligendis.server.dto.NumistaCollectionItemResponse;
import com.colligendis.server.dto.SaveNumistaCollectionRequest;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.parser.numista.collection.NumistaCollectionDeleteService;
import com.colligendis.server.parser.numista.collection.NumistaCollectionHttpException;
import com.colligendis.server.parser.numista.collection.NumistaCollectionRefreshService;
import com.colligendis.server.parser.numista.collection.NumistaCollectionSaveRequest;
import com.colligendis.server.parser.numista.collection.NumistaCollectionSaveService;
import com.colligendis.server.parser.numista.collection.NumistaGradingDesignation;
import com.colligendis.server.parser.numista.collection.NumistaGradingMark;
import com.colligendis.server.parser.numista.collection.NumistaGradingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumistaCollectionApiService {

	private final NumistaCollectionSaveService collectionSaveService;
	private final NumistaCollectionDeleteService collectionDeleteService;
	private final NumistaCollectionRefreshService collectionRefreshService;
	private final NumistaCollectionItemService collectionItemService;

	public Mono<NumistaCollectionItemResponse> save(
			SaveNumistaCollectionRequest request,
			ColligendisUser user,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(user.getNumistaCookie())) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Numista cookie is not configured. Add it in Settings / Profile."));
		}

		normalizePlaceFields(request);
		NumistaCollectionSaveRequest numistaRequest = toNumistaRequest(request);
		return collectionSaveService
				.saveToNumistaAndDatabase(numistaRequest, Mono.just(user), baseLogger)
				.flatMap(result -> mapSaveResult(result, request))
				.onErrorMap(
						NumistaCollectionHttpException.class,
						ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex));
	}

	public Mono<Void> delete(
			DeleteNumistaCollectionRequest request,
			ColligendisUser user,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(user.getNumistaCookie())) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Numista cookie is not configured. Add it in Settings / Profile."));
		}

		return collectionDeleteService
				.removeFromNumistaAndDatabase(
						request.getCoinId().trim(),
						request.getVersionId().trim(),
						request.getNumistaCollectionItemId().trim(),
						Mono.just(user),
						baseLogger)
				.flatMap(result -> mapDeleteResult(result))
				.onErrorMap(
						NumistaCollectionHttpException.class,
						ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex));
	}

	private Mono<Void> mapDeleteResult(ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> result) {
		DeleteExecutionStatus deleteStatus = resolveDeleteStatus(result);
		if (deleteStatus == null) {
			result.logError(new BaseLogger());
			log.warn("Collection delete returned unrecognized status");
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"Failed to delete collection item"));
		}
		return switch (deleteStatus) {
			case WAS_DELETED -> Mono.empty();
			case NOT_FOUND -> Mono.error(new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Collection item not found"));
			case INPUT_PARAMETERS_ERROR -> Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Collection item does not match coin and variant"));
			case EMPTY_RESULT, INTERNAL_ERROR -> {
				result.logError(new BaseLogger());
				yield Mono.error(new ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Failed to delete collection item in database"));
			}
			case MORE_THAN_ONE_FOUND -> Mono.error(new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Multiple collection items matched for delete"));
		};
	}

	private static DeleteExecutionStatus resolveDeleteStatus(
			ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> result) {
		ExecutionStatuses status = result.statusEnum();
		if (status instanceof DeleteExecutionStatus deleteStatus) {
			return deleteStatus;
		}
		if (status == null) {
			return null;
		}
		try {
			return DeleteExecutionStatus.valueOf(status.name());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	public Flux<NumistaCollectionItemResponse> listByCoinAndVersion(
			ColligendisUser user,
			String coinId,
			String versionId,
			BaseLogger baseLogger) {
		return collectionItemService
				.findByCoinIdAndVersionId(user, coinId, versionId, baseLogger)
				.map(NumistaCollectionItemResponse::from);
	}

	public Mono<java.util.List<NumistaCollectionItemResponse>> refreshFromNumistaPage(
			String issuerNumistaCode,
			ColligendisUser user,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(user.getNumistaCookie())) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Numista cookie is not configured. Add it in Settings / Profile."));
		}
		if (!StringUtils.hasText(issuerNumistaCode)) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"issuerNumistaCode is required"));
		}

		return collectionRefreshService
				.refreshFromNumistaPage(issuerNumistaCode.trim(), Mono.just(user), baseLogger)
				.map(items -> items.stream().map(NumistaCollectionItemResponse::from).toList())
				.onErrorMap(
						NumistaCollectionHttpException.class,
						ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex));
	}

	/**
	 * Fetches ALL pages of the user's full collection from Numista (no issuer
	 * filter), checks / re-parses each NType if necessary, and persists every
	 * collection item to the database.
	 */
	public Mono<java.util.List<NumistaCollectionItemResponse>> refreshFullCollection(
			ColligendisUser user,
			BaseLogger baseLogger) {
		if (!StringUtils.hasText(user.getNumistaCookie())) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Numista cookie is not configured. Add it in Settings / Profile."));
		}

		return collectionRefreshService
				.refreshFullCollectionFromNumista(Mono.just(user), baseLogger)
				.map(items -> items.stream().map(NumistaCollectionItemResponse::from).toList())
				.onErrorMap(
						NumistaCollectionHttpException.class,
						ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex));
	}

	private Mono<NumistaCollectionItemResponse> mapSaveResult(
			ExecutionResult<NumistaCollectionItem, ExecutionStatuses> result,
			SaveNumistaCollectionRequest request) {
		if (ExecutionStatusCoercion.isCollectionItemPersistSuccess(result.statusEnum())) {
			NumistaCollectionItem node = result.getNode();
			if (node == null) {
				return Mono.error(new ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Numista save succeeded but item was not persisted"));
			}
			if (!StringUtils.hasText(node.getNumistaCollectionItemId())) {
				return Mono.error(new ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Numista did not return a collection item id. Check your Numista cookie."));
			}
			return Mono.just(NumistaCollectionItemResponse.from(node));
		}
		if (ExecutionStatusCoercion.toUpdate(result.statusEnum()) == UpdateExecutionStatus.NOT_FOUND) {
			return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		}
		result.logError(new BaseLogger());
		return Mono.error(new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Failed to save item to Numista collection"));
	}

	/**
	 * Clears stale uuids when the client sends a new free-text place/location name without
	 * a matching selection, or when the field was cleared.
	 */
	private void normalizePlaceFields(SaveNumistaCollectionRequest request) {
		if (request == null) {
			return;
		}
		if (!StringUtils.hasText(request.getAcquisitionPlace())) {
			request.setAcquisitionPlace(null);
			request.setAcquisitionPlaceUuid(null);
		}
		if (!StringUtils.hasText(request.getStorageLocation())) {
			request.setStorageLocation(null);
			request.setStorageLocationUuid(null);
		}
	}

	private NumistaCollectionSaveRequest toNumistaRequest(SaveNumistaCollectionRequest request) {
		NumistaGradingService gradingService = NumistaGradingService.fromNumistaValue(request.getGradingService())
				.or(() -> NumistaGradingService.fromLabel(request.getGradingService()))
				.orElse(null);

		NumistaGradingMark gradingMark = null;
		if (gradingService != null) {
			gradingMark = NumistaGradingMark.fromNumistaValueAndService(request.getGradingMark(), gradingService)
					.or(() -> NumistaGradingMark.fromLabelAndService(request.getGradingMark(), gradingService))
					.orElse(null);
		} else {
			gradingMark = NumistaGradingMark.fromNumistaValue(request.getGradingMark()).orElse(null);
		}

		java.util.List<NumistaGradingDesignation> designations = new java.util.ArrayList<>();
		if (request.getGradingDesignation() != null && gradingService != null) {
			for (String raw : request.getGradingDesignation()) {
				NumistaGradingDesignation.fromNumistaValueAndService(raw, gradingService).ifPresent(designations::add);
			}
		}

		String existingItemId = StringUtils.hasText(request.getItem())
				? request.getItem()
				: request.getNumistaCollectionItemId();

		return NumistaCollectionSaveRequest.builder()
				.coinId(request.getCoinId())
				.version(request.getVersionId())
				.item(existingItemId)
				.quantity(request.getQuantity() != null ? request.getQuantity() : 1)
				.grade(request.getGrade())
				.value(request.getValue())
				.comment(request.getComment())
				.forSwap(Boolean.TRUE.equals(request.getForSwap()))
				.swapComment(request.getSwapComment())
				.gradingService(gradingService)
				.gradingMark(gradingMark)
				.gradingDesignation(designations)
				.gradingStrike(request.getGradingStrike())
				.gradingSurface(request.getGradingSurface())
				.slabNumber(request.getSlabNumber())
				.cacSticker(request.getCacSticker())
				.storageLocation(request.getStorageLocation())
				.storageLocationUuid(request.getStorageLocationUuid())
				.acquisitionPlace(request.getAcquisitionPlace())
				.acquisitionPlaceUuid(request.getAcquisitionPlaceUuid())
				.acquisitionDate(request.getAcquisitionDate())
				.serialNumber(request.getSerialNumber())
				.internalId(request.getInternalId())
				.size(request.getSize())
				.build();
	}
}
