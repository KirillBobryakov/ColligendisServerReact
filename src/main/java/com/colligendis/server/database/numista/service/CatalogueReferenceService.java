package com.colligendis.server.database.numista.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CatalogueReferenceService extends AbstractService {

	public Mono<ExecutionResult<CatalogueReference>> create(String number, Catalogue catalogue,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(new CatalogueReference(number), colligendisUser,
						CatalogueReference.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("CatalogueReference was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("CatalogueReference was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create CatalogueReference: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<CatalogueReference>> findByNumberAndCatalogueCode(String number,
			String catalogueCode, BaseLogger baseLogger) {
		return Mono.defer(() -> {
			String query = """
							MATCH (n:CATALOGUE_REFERENCE)-[:REFERENCE_FROM]->(c:CATALOGUE)
							WHERE n.number = $number AND c.code = $catalogueCode
							RETURN n AS resultNode,
							CASE
								WHEN n IS NULL THEN 'NODE_IS_NOT_FOUND'
								ELSE 'NODE_IS_FOUND'
							END AS status
					""";
			Map<String, Object> parameters = Map.of("number", number, "catalogueCode", catalogueCode);

			return super.executeReadMono(query, parameters,
					recordToNodeAndStatusMapper(CatalogueReference.class, baseLogger), "CatalogueReference not found",
					"Error while finding CatalogueReference by number and catalogueCode", baseLogger)
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
							return Mono.just(executionResult);
						} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
							baseLogger.traceRed("CatalogueReference was not found: {}", executionResult.getNode());
							return Mono.just(executionResult);
						} else {
							baseLogger.traceRed("Failed to find CatalogueReference: {}", executionResult.getStatus());
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
						}
					});
		});

	}

	public Mono<ExecutionResult<AbstractNode>> createReferenceToCatalogue(
			CatalogueReference catalogueReference, Catalogue catalogue, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(catalogueReference, catalogue,
						CatalogueReference.REFERENCE_FROM, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship from CatalogueReference to Catalogue was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS)) {
						baseLogger.traceRed("Relationship from CatalogueReference to Catalogue was not created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create relationship from CatalogueReference to Catalogue: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}
}
