package com.colligendis.server.database.numista.service.techdata;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.techdata.Composition;
import com.colligendis.server.database.numista.model.techdata.CompositionType;
import com.colligendis.server.database.numista.model.techdata.Metal;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CompositionService extends AbstractService {

	public Mono<ExecutionResult<Composition, CreateNodeExecutionStatus>> create(Composition composition,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(composition, colligendisUser, Composition.class,
						baseLogger));
	}

	public Mono<ExecutionResult<Composition, UpdateExecutionStatus>> update(Composition composition,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(composition, colligendisUser, Composition.class,
						baseLogger));

	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCompositionType(
			Composition composition,
			CompositionType compositionType, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(composition, compositionType,
						Composition.HAS_COMPOSITION_TYPE, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<CompositionType, FindExecutionStatus>> findCompositionType(Composition composition,
			BaseLogger baseLogger) {
		return super.getUniqueTargetNodeWithRelationshipType(composition, Composition.HAS_COMPOSITION_TYPE,
				CompositionType.class, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setPartMetal(Composition composition,
			Metal metal, int partNumber,
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
						relationshipType, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> removePartMetal(Composition composition,
			int partNumber,
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
						colligendisUser, baseLogger));
	}

	// public boolean compareCompositionAdditionalDetails(Composition composition,
	// String metalDetails,
	// BaseLogger baseLogger) {
	// if (metalDetails == null) {
	// baseLogger.traceRed("Input parameter metalDetails is null");
	// return false;
	// }
	// if (composition.getCompositionAdditionalDetails() == null) {
	// baseLogger.traceRed("Composition additional details is null");
	// return false;
	// }
	// return metalDetails != null &&
	// metalDetails.equals(composition.getCompositionAdditionalDetails());
	// }

}
