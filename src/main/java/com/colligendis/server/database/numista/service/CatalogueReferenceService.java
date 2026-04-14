package com.colligendis.server.database.numista.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CatalogueReferenceService extends AbstractService {

	public Mono<? extends ExecutionResult<? extends AbstractNode, ? extends ExecutionStatuses>> create(String number,
			Catalogue catalogue,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(new CatalogueReference(number), colligendisUser,
						CatalogueReference.class, baseLogger))
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case WAS_CREATED:
							return colligendisUserMono.flatMap(
									colligendisUser -> super.createUniqueTargetedRelationship(executionResult.getNode(),
											catalogue,
											CatalogueReference.REFERENCE_FROM, colligendisUser, baseLogger));
						default:
							return Mono.just(executionResult);

					}
				});
	}

	public Mono<ExecutionResult<CatalogueReference, ? extends FindExecutionStatus>> findByNumberAndCatalogueCode(
			String number,
			String catalogueCode, BaseLogger baseLogger) {
		return Mono.defer(() -> {
			String query = """
							MATCH (n:CATALOGUE_REFERENCE)-[:REFERENCE_FROM]->(c:CATALOGUE)
								WHERE n.number = $number AND c.code = $catalogueCode

							WITH collect(n) AS nodes

							RETURN
								CASE
									WHEN size(nodes) = 0 THEN NULL
									ELSE nodes[0]
								END AS resultNode,

								CASE
									WHEN size(nodes) = 0 THEN 'NOT_FOUND'
									WHEN size(nodes) = 1 THEN 'FOUND'
									ELSE 'MORE_THAN_ONE_FOUND'
								END AS status
					""";
			Map<String, Object> parameters = Map.of("number", number, "catalogueCode", catalogueCode);

			return super.executeReadMono(query, parameters,
					recordToNodeAndStatusMapper(CatalogueReference.class, FindExecutionStatus::valueOf, baseLogger),
					"CatalogueReference not found",
					"Error while finding CatalogueReference by number and catalogueCode", baseLogger)
					.flatMap(executionResult -> {
						switch (executionResult.getStatus()) {
							case EMPTY_RESULT:
								baseLogger.traceRed(
										"Empty result while finding CatalogueReference by number: {} and catalogueCode: {}",
										number, catalogueCode);
								return Mono.just(executionResult);
							case INTERNAL_ERROR:
								baseLogger.traceRed(
										"Internal error while finding CatalogueReference by number: {} and catalogueCode: {}",
										number, catalogueCode);
								return Mono.just(executionResult);
							case INPUT_PARAMETERS_ERROR:
								baseLogger.traceRed(
										"Input parameters error while finding CatalogueReference by number: {} and catalogueCode: {}",
										number, catalogueCode);
								return Mono.just(executionResult);
							case FOUND:
								baseLogger.traceGreen(
										"CatalogueReference found by number: {} and catalogueCode: {}",
										number, catalogueCode);
								return Mono.just(executionResult);
							case NOT_FOUND:
								baseLogger.traceOrange(
										"CatalogueReference not found by number: {} and catalogueCode: {}",
										number, catalogueCode);
								return Mono.just(executionResult);
							case MORE_THAN_ONE_FOUND:
								baseLogger.traceRed(
										"More than one CatalogueReference found by number: {} and catalogueCode: {}",
										number, catalogueCode);
								return Mono.just(executionResult);
						}
						return Mono.just(executionResult);
					});
		});

	}

}
