package com.colligendis.server.database.numista.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.model.CommemoratedEvent;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.database.numista.model.NTypePart;
import com.colligendis.server.database.numista.model.RulingAuthority;
import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.database.numista.model.SpecifiedMint;
import com.colligendis.server.database.numista.model.Variant;
import com.colligendis.server.database.numista.model.techdata.Composition;
import com.colligendis.server.database.numista.model.techdata.PART_TYPE;
import com.colligendis.server.database.numista.model.techdata.Shape;
import com.colligendis.server.database.numista.model.techdata.Technique;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExistsExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class NTypeService extends AbstractService {

	private static final int LINK_NIDS_BATCH_SIZE = 500;

	public Mono<ExecutionResult<NType, CreateNodeExecutionStatus>> create(NType nType,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(nType, colligendisUser, NType.class, baseLogger));
	}

	public Mono<ExecutionResult<NType, UpdateExecutionStatus>> update(NType nType,
			Mono<ColligendisUser> numistaParserUserMono,
			BaseLogger baseLogger) {
		return numistaParserUserMono
				.flatMap(numistaParserUser -> super.updateNodeProperties(nType, numistaParserUser, NType.class,
						baseLogger));
	}

	public Mono<ExecutionResult<NType, DeleteExecutionStatus>> delete(NType nType,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteNode(nType, colligendisUser, NType.class, baseLogger));
	}

	public Mono<ExecutionResult<NType, ExistsExecutionStatus>> isExists(String nid, BaseLogger baseLogger) {
		return super.isNodeExistsByUniquePropertyValue("nid", nid, NType.LABEL, NType.class, baseLogger);
	}

	public Mono<ExecutionResult<NType, FindExecutionStatus>> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, NType.LABEL, NType.class, baseLogger);
	}

	public Mono<ExecutionResult<NType, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, NType.LABEL, NType.class, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCollectibleType(NType nType,
			CollectibleType collectibleType, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, collectibleType,
						NType.HAS_COLLECTIBLE_TYPE, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, ExistsExecutionStatus>> isRelationshipToIssuerExists(NType nType,
			Issuer issuer,
			BaseLogger baseLogger) {
		return super.isRelationshipExists(nType, issuer, NType.ISSUED_BY, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setIssuer(NType nType, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, issuer, NType.ISSUED_BY,
						colligendisUser, baseLogger));
	}

	/**
	 * Ensures {@link NType} nodes exist for each nid and links them to the issuer via
	 * {@link NType#ISSUED_BY} in batched Cypher writes (one round-trip per batch).
	 */
	public Mono<Integer> linkNidsToIssuer(Issuer issuer, List<String> nids,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		if (issuer == null || !StringUtils.hasText(issuer.getUuid()) || nids == null || nids.isEmpty()) {
			return Mono.just(0);
		}

		final List<String> sanitizedNids = nids.stream()
				.filter(nid -> nid != null && !nid.isBlank())
				.distinct()
				.toList();
		if (sanitizedNids.isEmpty()) {
			return Mono.just(0);
		}

		return colligendisUserMono.flatMap(user -> Flux.fromIterable(partition(sanitizedNids, LINK_NIDS_BATCH_SIZE))
				.concatMap(batch -> linkNidsToIssuerBatch(issuer.getUuid(), batch, user, baseLogger))
				.reduce(0, Integer::sum));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setRulingAuthorities(NType nType,
			List<RulingAuthority> rulingAuthorities,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, rulingAuthorities,
						RulingAuthority.class,
						NType.DURING_OF_RULER, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setIssuingEntities(NType nType,
			List<IssuingEntity> issuingEntities,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, issuingEntities,
						IssuingEntity.class,
						NType.ISSUED_BY_ISSUING_ENTITY, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCurrency(NType nType,
			Currency currency, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, currency, NType.HAS_CURRENCY,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setDenomination(NType nType,
			Denomination denomination, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, denomination,
						NType.DENOMINATED_IN,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCommemoratedEvent(NType nType,
			CommemoratedEvent commemoratedEvent, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, commemoratedEvent,
						NType.COMMEMORATE_FOR,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setSeries(NType nType, Series series,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, series, NType.WITH_SERIES,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCatalogueReferences(NType nType,
			List<CatalogueReference> catalogueReferences,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, catalogueReferences,
						CatalogueReference.class,
						NType.HAS_CATALOGUE_REFERENCES, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<Composition, FindExecutionStatus>> getComposition(NType nType, BaseLogger baseLogger) {
		return super.getUniqueTargetNodeWithRelationshipType(nType, NType.HAS_COMPOSITION, Composition.class,
				baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setComposition(NType nType,
			Composition composition,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, composition,
						NType.HAS_COMPOSITION, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setShape(NType nType, Shape shape,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, shape, NType.HAS_SHAPE,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<Shape, FindExecutionStatus>> getShape(NType nType, BaseLogger baseLogger) {
		return super.getUniqueTargetNodeWithRelationshipType(nType, NType.HAS_SHAPE, Shape.class, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setTechniques(NType nType,
			List<Technique> techniques,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(user -> super.createUniqueOutgoingRelationships(nType, techniques,
						Technique.class, NType.HAS_TECHNIQUES,
						user, baseLogger));
	}

	private String getNTypePartRelationshipType(PART_TYPE partType) {
		return switch (partType) {
			case OBVERSE -> NType.HAS_OBVERSE;
			case REVERSE -> NType.HAS_REVERSE;
			case EDGE -> NType.HAS_EDGE;
			case WATERMARK -> NType.HAS_WATERMARK;
		};
	}

	public Mono<ExecutionResult<NTypePart, FindExecutionStatus>> getNTypePart(NType nType, PART_TYPE partType,
			BaseLogger baseLogger) {
		return super.getUniqueTargetNodeWithRelationshipType(nType, getNTypePartRelationshipType(partType),
				NTypePart.class, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setNTypePart(NType nType,
			NTypePart nTypePart,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, nTypePart,
						getNTypePartRelationshipType(nTypePart.getPartType()), colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setSpecifiedMints(NType nType,
			List<SpecifiedMint> specifiedMints,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, specifiedMints,
						SpecifiedMint.class,
						NType.HAS_SPECIFIED_MINT, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setVariants(NType nType,
			List<Variant> variants,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, variants,
						Variant.class, NType.HAS_VARIANT, colligendisUser, baseLogger));
	}

	public Flux<ExecutionResult<NType, FindExecutionStatus>> getAllNTypesByIssuer(Issuer issuer,
			BaseLogger baseLogger) {
		return super.getAllSourceNodesWithRelationshipType(issuer, NType.ISSUED_BY, NType.class, baseLogger);
	}

	private Mono<Integer> linkNidsToIssuerBatch(String issuerUuid, List<String> nids, ColligendisUser user,
			BaseLogger baseLogger) {
		final String deletedRelationshipType = NType.ISSUED_BY + DELETED_RELATIONSHIP_TYPE_POSTFIX;
		final String query = String.format(
				"""
						MATCH (issuer:%s {uuid: $issuerUuid})
						WHERE NONE(l IN labels(issuer) WHERE l ENDS WITH '%s' OR l ENDS WITH '%s')
						WITH issuer
						UNWIND $nids AS nid
						WITH issuer, nid
						WHERE nid IS NOT NULL AND trim(toString(nid)) <> ''
						MERGE (n:%s {nid: nid})
						ON CREATE SET n.uuid = randomUUID(),
						              n.createdAt = datetime({ timezone: '+03:00' }),
						              n.createdBy = $createdBy
						WITH n, issuer
						OPTIONAL MATCH (n)-[r:%s]->(oldIssuer:%s)
						WHERE oldIssuer.uuid <> issuer.uuid
						CALL (n, r, oldIssuer) {
						    WITH n, r, oldIssuer
						    WHERE oldIssuer IS NOT NULL
						    CREATE (n)-[r2:%s]->(oldIssuer)
						    SET r2 = properties(r),
						        r2.deletedAt = datetime({ timezone: '+03:00' }),
						        r2.deletedBy = $deletedBy
						    DELETE r
						}
						WITH n, issuer
						MERGE (n)-[nr:%s]->(issuer)
						ON CREATE SET nr.createdAt = datetime({ timezone: '+03:00' }),
						              nr.createdBy = $createdBy
						RETURN count(n) AS linkedCount
						""",
				Issuer.LABEL,
				DELETED_NODE_LABEL_POSTFIX,
				VERSIONED_NODE_LABEL_POSTFIX,
				NType.LABEL,
				NType.ISSUED_BY,
				Issuer.LABEL,
				deletedRelationshipType,
				NType.ISSUED_BY);

		final Map<String, Object> parameters = new HashMap<>();
		parameters.put("issuerUuid", issuerUuid);
		parameters.put("nids", nids);
		parameters.put("createdBy", user != null ? user.getUuid() : "");
		parameters.put("deletedBy", user != null ? user.getUuid() : "");

		baseLogger.trace("Bulk linking {} nids to issuer uuid={}", nids.size(), issuerUuid);
		baseLogger.trace("Query: {}", query);
		baseLogger.trace("Parameters: {}", parameters);

		return executeWriteCountMono(query, parameters, "linkedCount", baseLogger);
	}

	private static <T> List<List<T>> partition(List<T> items, int batchSize) {
		final List<List<T>> batches = new ArrayList<>();
		for (int index = 0; index < items.size(); index += batchSize) {
			batches.add(items.subList(index, Math.min(index + batchSize, items.size())));
		}
		return batches;
	}

}
