package com.colligendis.server.database.numista.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Mint;
import com.colligendis.server.database.numista.model.Mintmark;
import com.colligendis.server.database.numista.model.SpecifiedMint;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class SpecifiedMintService extends AbstractService {

	/**
	 * Locates an existing {@link SpecifiedMint} for this contribution row, or
	 * creates one and links {@link SpecifiedMint#WITH_MINT} /
	 * {@link SpecifiedMint#WITH_MINTMARK} as on the page.
	 */
	public Mono<ExecutionResult<SpecifiedMint, ? extends ExecutionStatuses>> findOrCreateWithMintLinks(
			String rowIdentifier,
			Mint mint,
			Mintmark mintmarkOrNull, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		Mono<ExecutionResult<SpecifiedMint, FindExecutionStatus>> findMono = mintmarkOrNull != null
				? findByRowIdentifierMintAndMintmark(rowIdentifier, mint.getUuid(), mintmarkOrNull.getUuid(),
						baseLogger)
				: findByRowIdentifierAndMint(rowIdentifier, mint.getUuid(), baseLogger);

		return findMono.flatMap(findResult -> {
			if (findResult.getStatus() == FindExecutionStatus.FOUND) {
				return ensureMintLinks(findResult.getNode(), mint, mintmarkOrNull, colligendisUserMono, baseLogger)
						.thenReturn(findResult);
			}
			if (findResult.getStatus() == FindExecutionStatus.NOT_FOUND) {
				return createWithMintLinks(rowIdentifier, mint, mintmarkOrNull, colligendisUserMono, baseLogger);
			}
			return Mono.just(findResult);
		});
	}

	public Mono<ExecutionResult<SpecifiedMint, FindExecutionStatus>> findByRowIdentifierMintAndMintmark(
			String rowIdentifier,
			String mintUuid, String mintmarkUuid, BaseLogger baseLogger) {
		String query = """
				MATCH (sm:SPECIFIED_MINT {identifier: $identifier})-[:WITH_MINT]->(m:MINT {uuid: $mintUuid})
				MATCH (sm)-[:WITH_MINTMARK]->(mm:MINTMARK {uuid: $mintmarkUuid})
				WITH collect(sm) AS nodes
				RETURN
					CASE WHEN size(nodes) = 0 THEN NULL ELSE nodes[0] END AS resultNode,
					CASE
						WHEN size(nodes) = 0 THEN 'NODE_IS_NOT_FOUND'
						WHEN size(nodes) = 1 THEN 'NODE_IS_FOUND'
						ELSE 'MORE_THAN_ONE_NODE_IS_FOUND'
					END AS status
				""";
		Map<String, Object> parameters = Map.of("identifier", rowIdentifier, "mintUuid", mintUuid, "mintmarkUuid",
				mintmarkUuid);
		return executeReadMono(query, parameters,
				recordToNodeAndStatusMapper(SpecifiedMint.class, FindExecutionStatus::valueOf, baseLogger),
				"SpecifiedMint not found", "Error finding SpecifiedMint by row, mint and mintmark", baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result finding SpecifiedMint by row identifier: {}, mint uuid: {}, mintmark uuid: {}",
									rowIdentifier, mintUuid, mintmarkUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.NOT_FOUND)
									.build());
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Internal error finding SpecifiedMint by row identifier: {}, mint uuid: {}, mintmark uuid: {}",
									rowIdentifier, mintUuid, mintmarkUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.INTERNAL_ERROR)
									.build());
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error finding SpecifiedMint by row identifier: {}, mint uuid: {}, mintmark uuid: {}",
									rowIdentifier, mintUuid, mintmarkUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.INPUT_PARAMETERS_ERROR)
									.build());
						case FOUND:
							baseLogger.traceGreen(
									"SpecifiedMint found by row identifier: {}, mint uuid: {}, mintmark uuid: {}",
									rowIdentifier, mintUuid, mintmarkUuid);
							return Mono.just(executionResult);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"SpecifiedMint not found by row identifier: {}, mint uuid: {}, mintmark uuid: {}",
									rowIdentifier, mintUuid, mintmarkUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.NOT_FOUND)
									.build());
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one SpecifiedMint found by row identifier: {}, mint uuid: {}, mintmark uuid: {}",
									rowIdentifier, mintUuid, mintmarkUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.MORE_THAN_ONE_FOUND)
									.build());
					}
					return Mono.just(executionResult);
				});
	}

	public Mono<ExecutionResult<SpecifiedMint, FindExecutionStatus>> findByRowIdentifierAndMint(String rowIdentifier,
			String mintUuid,
			BaseLogger baseLogger) {
		String query = """
				MATCH (sm:SPECIFIED_MINT {identifier: $identifier})-[:WITH_MINT]->(m:MINT {uuid: $mintUuid})
				WITH collect(sm) AS nodes
				RETURN
					CASE WHEN size(nodes) = 0 THEN NULL ELSE nodes[0] END AS resultNode,
					CASE
						WHEN size(nodes) = 0 THEN 'NODE_IS_NOT_FOUND'
						WHEN size(nodes) = 1 THEN 'NODE_IS_FOUND'
						ELSE 'MORE_THAN_ONE_NODE_IS_FOUND'
					END AS status
				""";
		Map<String, Object> parameters = Map.of("identifier", rowIdentifier, "mintUuid", mintUuid);
		return executeReadMono(query, parameters,
				recordToNodeAndStatusMapper(SpecifiedMint.class, FindExecutionStatus::valueOf, baseLogger),
				"SpecifiedMint not found", "Error finding SpecifiedMint by row and mint", baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result finding SpecifiedMint by row identifier: {}, mint uuid: {}",
									rowIdentifier, mintUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.NOT_FOUND)
									.build());
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Internal error finding SpecifiedMint by row identifier: {}, mint uuid: {}",
									rowIdentifier, mintUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.INTERNAL_ERROR)
									.build());
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error finding SpecifiedMint by row identifier: {}, mint uuid: {}",
									rowIdentifier, mintUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.INPUT_PARAMETERS_ERROR)
									.build());
						case FOUND:
							baseLogger.traceGreen(
									"SpecifiedMint found by row identifier: {}, mint uuid: {}",
									rowIdentifier, mintUuid);
							return Mono.just(executionResult);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"SpecifiedMint not found by row identifier: {}, mint uuid: {}",
									rowIdentifier, mintUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.NOT_FOUND)
									.build());
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one SpecifiedMint found by row identifier: {}, mint uuid: {}",
									rowIdentifier, mintUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.MORE_THAN_ONE_FOUND)
									.build());
					}
					return Mono.just(executionResult);
				});
	}

	private Mono<ExecutionResult<SpecifiedMint, CreateNodeExecutionStatus>> createWithMintLinks(String rowIdentifier,
			Mint mint,
			Mintmark mintmarkOrNull, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> {
			SpecifiedMint specifiedMint = new SpecifiedMint();
			specifiedMint.setIdentifier(rowIdentifier);
			return super.createNode(specifiedMint, user, SpecifiedMint.class, baseLogger).flatMap(created -> {
				if (created.getStatus() != CreateNodeExecutionStatus.WAS_CREATED) {
					return Mono.just(created);
				}
				SpecifiedMint createdNode = created.getNode();
				return createUniqueTargetedRelationship(createdNode, mint, SpecifiedMint.WITH_MINT, user, baseLogger)
						.flatMap(rel -> {
							CreateRelationshipExecutionStatus relStatus = rel.getStatus();
							if (relStatus != CreateRelationshipExecutionStatus.WAS_CREATED
									&& relStatus != CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS) {
								return Mono.just(created);
							}
							if (mintmarkOrNull == null) {
								return Mono.just(created);
							}
							return createUniqueTargetedRelationship(createdNode, mintmarkOrNull,
									SpecifiedMint.WITH_MINTMARK, user, baseLogger).thenReturn(created);
						});
			});
		});
	}

	/**
	 * Finds a {@link SpecifiedMint} linked to the given NType by contribution
	 * row {@code identifier} (e.g. {@code atelier} input value).
	 */
	public Mono<ExecutionResult<SpecifiedMint, FindExecutionStatus>> findByIdentifierLinkedToNType(String identifier,
			String nTypeUuid,
			BaseLogger baseLogger) {
		String query = """
				MATCH (nt:NTYPE {uuid: $nTypeUuid})-[:HAS_SPECIFIED_MINT]->(sm:SPECIFIED_MINT {identifier: $identifier})
				WITH collect(sm) AS nodes
				RETURN
					CASE WHEN size(nodes) = 0 THEN NULL ELSE nodes[0] END AS resultNode,
					CASE
						WHEN size(nodes) = 0 THEN 'NODE_IS_NOT_FOUND'
						WHEN size(nodes) = 1 THEN 'NODE_IS_FOUND'
						ELSE 'MORE_THAN_ONE_NODE_IS_FOUND'
					END AS status
				""";
		Map<String, Object> parameters = Map.of("identifier", identifier, "nTypeUuid", nTypeUuid);
		return executeReadMono(query, parameters,
				recordToNodeAndStatusMapper(SpecifiedMint.class, FindExecutionStatus::valueOf, baseLogger),
				"SpecifiedMint not found for NType",
				"Error finding SpecifiedMint by identifier for NType", baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case EMPTY_RESULT:
							baseLogger.traceRed(
									"Empty result finding SpecifiedMint for NType by identifier: {}, nType uuid: {}",
									identifier, nTypeUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.NOT_FOUND)
									.build());
						case INTERNAL_ERROR:
							baseLogger.traceRed(
									"Internal error finding SpecifiedMint for NType by identifier: {}, nType uuid: {}",
									identifier, nTypeUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.INTERNAL_ERROR)
									.build());
						case INPUT_PARAMETERS_ERROR:
							baseLogger.traceRed(
									"Input parameters error finding SpecifiedMint for NType by identifier: {}, nType uuid: {}",
									identifier, nTypeUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.INPUT_PARAMETERS_ERROR)
									.build());
						case FOUND:
							baseLogger.traceGreen(
									"SpecifiedMint found for NType by identifier: {}, nType uuid: {}",
									identifier, nTypeUuid);
							return Mono.just(executionResult);
						case NOT_FOUND:
							baseLogger.traceOrange(
									"SpecifiedMint not found for NType by identifier: {}, nType uuid: {}",
									identifier, nTypeUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.NOT_FOUND)
									.build());
						case MORE_THAN_ONE_FOUND:
							baseLogger.traceRed(
									"More than one SpecifiedMint found for NType by identifier: {}, nType uuid: {}",
									identifier, nTypeUuid);
							return Mono.just(ExecutionResult.<SpecifiedMint, FindExecutionStatus>builder()
									.status(FindExecutionStatus.MORE_THAN_ONE_FOUND)
									.build());
					}
					return Mono.just(executionResult);
				});
	}

	private Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> ensureMintLinks(
			SpecifiedMint specifiedMint, Mint mint,
			Mintmark mintmarkOrNull, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono.flatMap(user -> createUniqueTargetedRelationship(specifiedMint, mint,
				SpecifiedMint.WITH_MINT, user, baseLogger)
				.flatMap(rel -> {
					if (mintmarkOrNull == null) {
						return Mono.just(rel);
					}
					return createUniqueTargetedRelationship(specifiedMint, mintmarkOrNull, SpecifiedMint.WITH_MINTMARK,
							user, baseLogger);
				}));
	}

}
