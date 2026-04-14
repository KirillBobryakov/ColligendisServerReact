package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.model.Mark;
import com.colligendis.server.database.numista.model.Signature;
import com.colligendis.server.database.numista.model.SpecifiedMint;
import com.colligendis.server.database.numista.model.Variant;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class VariantService extends AbstractService {

	public Mono<ExecutionResult<Variant, CreateNodeExecutionStatus>> create(Variant variant,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(variant, colligendisUser, Variant.class, baseLogger));
	}

	public Mono<ExecutionResult<Variant, UpdateExecutionStatus>> update(Variant variant,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(variant, colligendisUser, Variant.class,
						baseLogger));
	}

	public Mono<ExecutionResult<Variant, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Variant.LABEL, Variant.class, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setSignatures(Variant variant,
			List<Signature> signatures,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(variant, signatures,
						Signature.class,
						Variant.WITH_SIGNATURE, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setSpecifiedMint(Variant variant,
			SpecifiedMint specifiedMint,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(variant, specifiedMint,
						Variant.WITH_SPECIFIED_MINT, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setMarks(Variant variant,
			List<Mark> marks,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(variant, marks, Mark.class,
						Variant.WITH_MARK, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setCatalogueReferences(
			Variant variant,
			List<CatalogueReference> catalogueReferences,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(variant, catalogueReferences,
						CatalogueReference.class, Variant.HAS_CATALOGUE_REFERENCES, colligendisUser, baseLogger));
	}
}
