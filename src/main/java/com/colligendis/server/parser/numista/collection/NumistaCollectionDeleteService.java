package com.colligendis.server.parser.numista.collection;

import org.springframework.stereotype.Service;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.NumistaCollectionItem;
import com.colligendis.server.database.numista.service.NumistaCollectionItemService;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.logger.BaseLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumistaCollectionDeleteService {

	private final NumistaCollectionClient collectionClient;
	private final NumistaCollectionItemService collectionItemService;

	public Mono<ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus>> removeFromNumistaAndDatabase(
			String coinId,
			String versionId,
			String numistaCollectionItemId,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		NumistaCollectionRemoveRequest removeRequest = NumistaCollectionRemoveRequest.builder()
				.item(numistaCollectionItemId)
				.version(versionId)
				.collectible(coinId)
				.build();

		return colligendisUserMono.flatMap(user -> collectionItemService
				.findForDelete(user, coinId, versionId, numistaCollectionItemId, baseLogger)
				.flatMap(item -> collectionClient
						.removeCollectionItem(removeRequest, user)
						.flatMap(ignored -> deleteLocalAfterNumista(item, user, baseLogger)))
				.switchIfEmpty(collectionClient
						.removeCollectionItem(removeRequest, user)
						.flatMap(ignored -> Mono.just(deletedWithoutLocalRow()))));
	}

	private Mono<ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus>> deleteLocalAfterNumista(
			NumistaCollectionItem item,
			ColligendisUser user,
			BaseLogger baseLogger) {
		return collectionItemService.deleteItem(item, Mono.just(user), baseLogger)
				.map(result -> normalizeDeleteResultAfterNumista(result, baseLogger));
	}

	/**
	 * Numista row is already removed; local {@link DeleteExecutionStatus#NOT_FOUND} is success.
	 */
	private ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> normalizeDeleteResultAfterNumista(
			ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> result,
			BaseLogger baseLogger) {
		DeleteExecutionStatus status = toDeleteStatus(result);
		if (status == null) {
			log.warn("Collection item delete returned null status for uuid={}",
					result.getNode() != null ? result.getNode().getUuid() : null);
			return result;
		}
		if (status == DeleteExecutionStatus.WAS_DELETED || status == DeleteExecutionStatus.NOT_FOUND) {
			return ExecutionResult.<NumistaCollectionItem, DeleteExecutionStatus>builder()
					.node(result.getNode())
					.status(DeleteExecutionStatus.WAS_DELETED)
					.build();
		}
		if (result.getError() != null) {
			log.warn("Collection item delete after Numista remove failed with status={}: {}",
					status, result.getError().message());
			result.logError(baseLogger);
		} else {
			log.warn("Collection item delete after Numista remove failed with status={}", status);
		}
		return result;
	}

	private static DeleteExecutionStatus toDeleteStatus(
			ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> result) {
		var raw = result.statusEnum();
		if (raw instanceof DeleteExecutionStatus deleteStatus) {
			return deleteStatus;
		}
		if (raw == null) {
			return null;
		}
		try {
			return DeleteExecutionStatus.valueOf(raw.name());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static ExecutionResult<NumistaCollectionItem, DeleteExecutionStatus> deletedWithoutLocalRow() {
		return ExecutionResult.<NumistaCollectionItem, DeleteExecutionStatus>builder()
				.status(DeleteExecutionStatus.WAS_DELETED)
				.build();
	}
}
