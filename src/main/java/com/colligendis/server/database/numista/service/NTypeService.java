package com.colligendis.server.database.numista.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.model.CommemoratedEvent;
import com.colligendis.server.database.numista.model.Currency;
import com.colligendis.server.database.numista.model.Denomination;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.numista.model.NType;
import com.colligendis.server.database.numista.model.Ruler;
import com.colligendis.server.database.numista.model.Series;
import com.colligendis.server.util.Either;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class NTypeService extends AbstractService {

	public Mono<Either<DatabaseException, NType>> save(NType nType, ColligendisUser colligendisUser) {
		return super.createNode(nType, colligendisUser, NType.class);
	}

	public Mono<Either<DatabaseException, NType>> save(Mono<NType> nTypeMono,
			Mono<ColligendisUser> colligendisUserMono) {
		return Mono.zip(nTypeMono, colligendisUserMono)
				.flatMap(tuple -> save(tuple.getT1(), tuple.getT2()));
	}

	public Mono<Either<DatabaseException, NType>> saveWithColligendisUserMono(NType nType,
			Mono<ColligendisUser> colligendisUserMono) {
		return colligendisUserMono
				.flatMap(colligendisUser -> save(nType, colligendisUser));
	}

	public Mono<Either<DatabaseException, NType>> update(NType nType,
			Mono<ColligendisUser> colligendisUserMono) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateAllNodeProperties(nType, colligendisUser, NType.class));
	}

	public Mono<Either<DatabaseException, Boolean>> delete(NType node, ColligendisUser colligendisUser) {
		return super.deleteNode(node, colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> isExists(String nid) {
		return super.isNodeExistsByPropertyValue("nid", nid, NType.LABEL);
	}

	public Mono<Either<DatabaseException, NType>> findByUuid(String uuid) {
		return super.findNodeByUuid(uuid, NType.LABEL, NType.class);
	}

	public Mono<Either<DatabaseException, NType>> findByNid(String nid) {
		return super.findNodeByUniquePropertyValue("nid", nid, NType.LABEL, NType.class);
	}

	public Mono<Either<DatabaseException, Boolean>> setCollectibleType(NType nType,
			CollectibleType collectibleType, ColligendisUser colligendisUser) {
		return super.createSingleRelationship(nType, collectibleType, NType.HAS_COLLECTIBLE_TYPE, colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setIssuer(NType nType, Issuer issuer,
			ColligendisUser colligendisUser) {
		return super.createSingleRelationship(nType, issuer, NType.ISSUED_BY, colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setIssuerMono(Mono<NType> nTypeMono, Mono<Issuer> issuerMono,
			Mono<ColligendisUser> colligendisUserMono) {
		return Mono.zip(nTypeMono, issuerMono, colligendisUserMono)
				.flatMap(tuple -> setIssuer(tuple.getT1(), tuple.getT2(), tuple.getT3()));
	}

	public Mono<Either<DatabaseException, Boolean>> setRulersMono(Mono<NType> nTypeMono,
			Mono<List<Ruler>> rulersListMono,
			Mono<ColligendisUser> colligendisUserMono) {
		return Mono.zip(nTypeMono, rulersListMono, colligendisUserMono)
				.flatMap(tuple -> setRulers(tuple.getT1(), tuple.getT2(), tuple.getT3()));
	}

	public Mono<Either<DatabaseException, Boolean>> setRulers(NType nType, List<Ruler> rulers,
			ColligendisUser colligendisUser) {
		return super.createUniqueOutgoingRelationships(nType, rulers, Ruler.class, NType.DURING_OF_RULER,
				colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setIssuingEntitiesMono(Mono<NType> nTypeMono,
			Mono<List<IssuingEntity>> issuingEntitiesListMono,
			Mono<ColligendisUser> colligendisUserMono) {
		return Mono.zip(nTypeMono, issuingEntitiesListMono, colligendisUserMono)
				.flatMap(tuple -> setIssuingEntities(tuple.getT1(), tuple.getT2(), tuple.getT3()));
	}

	public Mono<Either<DatabaseException, Boolean>> setIssuingEntities(NType nType, List<IssuingEntity> issuingEntities,
			ColligendisUser colligendisUser) {
		return super.createUniqueOutgoingRelationships(nType, issuingEntities, IssuingEntity.class,
				NType.ISSUED_BY_ISSUING_ENTITY,
				colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setCurrency(NType nType,
			Currency currency, ColligendisUser colligendisUser) {
		return super.createUniqueTargetedRelationship(nType, currency, NType.HAS_CURRENCY,
				colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setDenomination(NType nType,
			Denomination denomination, ColligendisUser colligendisUser) {
		return super.createUniqueTargetedRelationship(nType, denomination, NType.DENOMINATED_IN,
				colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setCommemoratedEvent(NType nType,
			CommemoratedEvent commemoratedEvent, ColligendisUser colligendisUser) {
		return super.createUniqueTargetedRelationship(nType, commemoratedEvent, NType.COMMEMORATE_FOR,
				colligendisUser);
	}

	public Mono<Either<DatabaseException, Boolean>> setSeries(NType nType, Series series,
			ColligendisUser colligendisUser) {
		return super.createUniqueTargetedRelationship(nType, series, NType.WITH_SERIES, colligendisUser);
	}

	public Mono<Either<DatabaseException, NType>> setIssueDate(NType nType,
			String yearIssueDate, String monthIssueDate, String dayIssueDate, ColligendisUser colligendisUser) {
		Map<String, Object> properties = new HashMap<>();
		properties.put("yearIssueDate", yearIssueDate != null ? yearIssueDate : "");
		properties.put("monthIssueDate", monthIssueDate != null ? monthIssueDate : "");
		properties.put("dayIssueDate", dayIssueDate != null ? dayIssueDate : "");
		return super.updateNodeProperties(nType, properties, colligendisUser, NType.class);
	}

	public Mono<Either<DatabaseException, NType>> setDemonetized(NType nType,
			String demonetized, String demonetizationYear, String demonetizationMonth, String demonetizationDay,
			ColligendisUser colligendisUser) {
		Map<String, Object> properties = new HashMap<>();
		properties.put("demonetized", demonetized != null ? demonetized : "");
		properties.put("demonetizationYear", demonetizationYear != null ? demonetizationYear : "");
		properties.put("demonetizationMonth", demonetizationMonth != null ? demonetizationMonth : "");
		properties.put("demonetizationDay", demonetizationDay != null ? demonetizationDay : "");
		return super.updateNodeProperties(nType, properties, colligendisUser, NType.class);
	}

	public Mono<Either<DatabaseException, Boolean>> setCatalogueReferences(NType nType,
			List<CatalogueReference> catalogueReferences,
			ColligendisUser colligendisUser) {
		return super.createUniqueOutgoingRelationships(nType, catalogueReferences, CatalogueReference.class,
				NType.HAS_CATALOGUE_REFERENCES, colligendisUser);
	}

}
