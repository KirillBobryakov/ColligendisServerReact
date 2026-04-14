package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

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
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class NTypeService extends AbstractService {

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

}
