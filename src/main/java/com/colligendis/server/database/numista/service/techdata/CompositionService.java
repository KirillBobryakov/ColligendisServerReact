package com.colligendis.server.database.numista.service.techdata;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.techdata.Composition;
import com.colligendis.server.database.numista.model.techdata.CompositionType;
import com.colligendis.server.database.numista.model.techdata.Metal;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CompositionService extends AbstractService {

	public Mono<ExecutionResult<Composition>> save(Composition composition, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(composition, colligendisUser, Composition.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("Composition was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("Composition was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create Composition: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Composition>> update(Composition composition, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(composition, colligendisUser, Composition.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_UPDATED)) {
						baseLogger.trace("Composition was updated: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOTHING_TO_UPDATE)) {
						baseLogger.traceOrange("Composition has nothing to update: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Composition was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to update Composition: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setCompositionType(Composition composition,
			CompositionType compositionType, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(composition, compositionType,
						Composition.HAS_COMPOSITION_TYPE, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between Composition and CompositionType was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange(
								"Relationship between Composition and CompositionType is already exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create relationship between Composition and CompositionType: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<CompositionType>> findCompositionType(Composition composition, BaseLogger baseLogger) {
		return super.getUniqueTargetNodeWithRelationshipType(composition, Composition.HAS_COMPOSITION_TYPE,
				CompositionType.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("CompositionType was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceOrange("CompositionType was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find CompositionType: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setPartMetal(Composition composition, Metal metal, int partNumber,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		String relationshipType = switch (partNumber) {
			case 1 -> Composition.PART1_IS_MADE_OF;
			case 2 -> Composition.PART2_IS_MADE_OF;
			case 3 -> Composition.PART3_IS_MADE_OF;
			case 4 -> Composition.PART4_IS_MADE_OF;
			default -> throw new IllegalArgumentException("Invalid part number: " + partNumber);
		};

		return colligendisUserMono
				.flatMap(colligendisUser -> super.createSingleRelationship(composition, metal,
						relationshipType, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between Composition and Metal for part {} was created: {}",
								partNumber, executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange(
								"Relationship between Composition and Metal for part {} is already exists: {}",
								partNumber, executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Composition or Metal for part {} was not found: {}",
								partNumber, executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to create relationship between Composition and Metal for part {}: {}",
								partNumber, executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> removePartMetal(Composition composition, int partNumber,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {

		String relationshipType = switch (partNumber) {
			case 1 -> Composition.PART1_IS_MADE_OF;
			case 2 -> Composition.PART2_IS_MADE_OF;
			case 3 -> Composition.PART3_IS_MADE_OF;
			case 4 -> Composition.PART4_IS_MADE_OF;
			default -> throw new IllegalArgumentException("Invalid part number: " + partNumber);
		};
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteAllOutgoingRelationshipsWithRelationshipType(composition,
						relationshipType,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_DELETED)) {
						baseLogger.trace("Relationship between Composition and Metal for part {} was deleted: {}",
								partNumber, executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS)) {
						baseLogger.traceOrange(
								"Relationship between Composition and Metal for part {} is not exists: {}",
								partNumber, executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to delete relationship between Composition and Metal for part {}: {}",
								partNumber, executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public boolean compareCompositionAdditionalDetails(Composition composition, String metalDetails,
			BaseLogger baseLogger) {
		if (metalDetails == null) {
			baseLogger.traceRed("Input parameter metalDetails is null");
			return false;
		}
		if (composition.getCompositionAdditionalDetails() == null) {
			baseLogger.traceRed("Composition additional details is null");
			return false;
		}
		return metalDetails != null && metalDetails.equals(composition.getCompositionAdditionalDetails());
	}

	public Mono<ExecutionResult<Composition>> setCompositionAdditionalDetails(Composition composition,
			String metalDetails, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(colligendisUser -> {
			composition.setCompositionAdditionalDetails(metalDetails);
			return super.updateNodeProperties(composition, colligendisUser, Composition.class, baseLogger)
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_UPDATED)) {
							baseLogger.trace("Composition additional details was updated: {}",
									executionResult.getNode());
							return Mono.just(executionResult);
						} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOTHING_TO_UPDATE)) {
							baseLogger.traceOrange("Composition additional details has nothing to update: {}",
									executionResult.getNode());
							return Mono.just(executionResult);
						} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
							baseLogger.traceRed("Composition was not found: {}", executionResult.getNode());
							return Mono.just(executionResult);
						} else {
							baseLogger.traceRed("Failed to update Composition additional details: {}",
									executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
						}
					});
		});
	}
}
