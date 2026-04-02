package com.colligendis.server.database.numista.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.model.CommemoratedEvent;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.database.numista.model.RulingAuthority;
import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.database.numista.model.techdata.Composition;
import com.colligendis.server.database.numista.model.techdata.Shape;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class NTypeService extends AbstractService {

	public Mono<ExecutionResult<NType>> create(NType nType, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(nType, colligendisUser, NType.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("NType was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("NType was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create NType: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<NType>> update(NType nType, Mono<ColligendisUser> numistaParserUserMono,
			BaseLogger baseLogger) {
		return numistaParserUserMono
				.flatMap(numistaParserUser -> super.updateNodeProperties(nType, numistaParserUser, NType.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_UPDATED)) {
						baseLogger.trace("NType was updated: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOTHING_TO_UPDATE)) {
						baseLogger.traceOrange("NType has no properties to update for uuid: {}", nType.getUuid());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("NType was not found for uuid: {}", nType.getUuid());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to update NType for uuid: {} with properties: {}", nType.getUuid(),
								nType.getPropertiesMap());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<NType>> delete(NType nType, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteNode(nType, colligendisUser, NType.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_DELETED)) {
						baseLogger.trace("NType was deleted: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("NType was not found for uuid: {}", nType.getUuid());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to delete NType for uuid: {}", nType.getUuid());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<NType>> isExists(String nid, BaseLogger baseLogger) {
		return super.isNodeExistsByUniquePropertyValue("nid", nid, NType.LABEL, NType.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_EXISTS)) {
						baseLogger.trace("NType exists: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_EXISTS)) {
						baseLogger.traceOrange("NType does not exist for nid: {}", nid);
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to check if NType exists for nid: {}", nid);
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<NType>> findByUuid(String uuid, BaseLogger baseLogger) {
		return super.findNodeByUuid(uuid, NType.LABEL, NType.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("NType found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceOrange("NType not found for uuid: {}", uuid);
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one NType found for uuid: {}", uuid);
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find NType for uuid: {}", uuid);
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<NType>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, NType.LABEL, NType.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("NType found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceOrange("NType not found for nid: {}", nid);
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one NType found for nid: {}", nid);
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find NType for nid: {}", nid);
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setCollectibleType(NType nType,
			CollectibleType collectibleType, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, collectibleType,
						NType.HAS_COLLECTIBLE_TYPE, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and CollectibleType exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and CollectibleType was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and CollectibleType: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and CollectibleType for nid: {} and collectible type code: {} and name: {}",
								nType.nid, collectibleType.getCode(), collectibleType.getName());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> isRelationshipToIssuerExists(NType nType, Issuer issuer,
			BaseLogger baseLogger) {
		return super.isRelationshipExists(nType, issuer, NType.ISSUED_BY, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.trace("Relationship between NType and Issuer exists: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS)) {
						baseLogger.trace("Relationship between NType and Issuer does not exist: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus()
							.equals(ExecutionStatus.MORE_THAN_ONE_RELATIONSHIP_IS_FOUND)) {
						baseLogger.traceRed("More than one relationship between NType and Issuer found: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to check if relationship between NType and Issuer exists: {}",
								executionResult.getNode());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setIssuer(NType nType, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, issuer, NType.ISSUED_BY,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and Issuer exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and Issuer was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and Issuer: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and Issuer for nid: {} and issuer numistaCode: {} and name: {}",
								nType.nid, issuer.getNumistaCode(), issuer.getName());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setRulingAuthorities(NType nType,
			List<RulingAuthority> rulingAuthorities,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, rulingAuthorities,
						RulingAuthority.class,
						NType.DURING_OF_RULER, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and RulingAuthority was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and RulingAuthority for nid: {} and ruling authorities numistaCodes: {}",
								nType.nid, rulingAuthorities.stream().map(RulingAuthority::getNid)
										.collect(Collectors.joining(",")));
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setIssuingEntities(NType nType,
			List<IssuingEntity> issuingEntities,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, issuingEntities,
						IssuingEntity.class,
						NType.ISSUED_BY_ISSUING_ENTITY, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and IssuingEntity was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and IssuingEntity for nid: {} and issuing entities numistaCodes: {}",
								nType.nid, issuingEntities.stream().map(IssuingEntity::getNid)
										.collect(Collectors.joining(",")));
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setCurrency(NType nType,
			Currency currency, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, currency, NType.HAS_CURRENCY,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and Currency exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and Currency was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and Currency: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and Currency for nid: {} and currency nid: {}",
								nType.nid, currency.getNid());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setDenomination(NType nType,
			Denomination denomination, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, denomination,
						NType.DENOMINATED_IN,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and Denomination exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and Denomination was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and Denomination: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and Denomination for nid: {} and denomination nid: {}",
								nType.nid, denomination.getNid());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setCommemoratedEvent(NType nType,
			CommemoratedEvent commemoratedEvent, Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, commemoratedEvent,
						NType.COMMEMORATE_FOR,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and CommemoratedEvent exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and CommemoratedEvent was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and CommemoratedEvent: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and CommemoratedEvent for nid: {} and commemorated event name: {}",
								nType.nid, commemoratedEvent.getName());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setSeries(NType nType, Series series,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, series, NType.WITH_SERIES,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and Series exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and Series was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and Series: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and Series for nid: {} and series nid: {}",
								nType.nid, series.getNid());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setCatalogueReferences(NType nType,
			List<CatalogueReference> catalogueReferences,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nType, catalogueReferences,
						CatalogueReference.class,
						NType.HAS_CATALOGUE_REFERENCES, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and CatalogueReference was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and CatalogueReference for nid: {} and catalogue references numbers: {}",
								nType.nid, catalogueReferences.stream().map(CatalogueReference::getNumber)
										.collect(Collectors.joining(",")));
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Composition>> getComposition(NType nType, BaseLogger baseLogger) {
		return super.getUniqueTargetNodeWithRelationshipType(nType, NType.HAS_COMPOSITION, Composition.class,
				baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("Composition found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceOrange("Composition not found for NType: {}", nType.getNid());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to get Composition for NType: {}", nType.getNid());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setComposition(NType nType, Composition composition,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, composition,
						NType.HAS_COMPOSITION, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and Composition exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and Composition was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and Composition: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and Composition for nid: {} and composition nid: {}",
								nType.nid, composition.getUuid());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setShape(NType nType, Shape shape,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(nType, shape, NType.HAS_SHAPE,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Relationship between NType and Shape exists: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Relationship between NType and Shape was created: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Node is null for NType and Shape: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed(
								"Failed to set relationship between NType and Shape for nid: {} and shape nid: {}",
								nType.nid, shape.getNid());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

}
