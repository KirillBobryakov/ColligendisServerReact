package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Author;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CatalogueService extends AbstractService {

	public Mono<ExecutionResult<Catalogue, CreateNodeExecutionStatus>> create(Catalogue catalogue,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(catalogue, colligendisUser, Catalogue.class, baseLogger));
	}

	public Mono<ExecutionResult<Catalogue, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Catalogue.LABEL, Catalogue.class, baseLogger);
	}

	public Mono<ExecutionResult<Catalogue, FindExecutionStatus>> findByCode(String code, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("code", code, Catalogue.LABEL, Catalogue.class, baseLogger);
	}

	public Mono<ExecutionResult<Catalogue, ? extends ExecutionStatuses>> findByNidWithSave(String nid, String code,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(findExecutionResult -> {
					switch (findExecutionResult.getStatus()) {
						case FOUND:
							return Mono.just(findExecutionResult);
						case NOT_FOUND:
							return create(new Catalogue(nid, code), colligendisUserMono, baseLogger);
						default:
							findExecutionResult.logError(baseLogger);
							return Mono.just(findExecutionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode, ? extends CreateRelationshipExecutionStatus>> setAuthors(
			Catalogue catalogue, List<Author> authors,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(catalogue, authors, Author.class,
						Catalogue.WRITTEN_BY, colligendisUser, baseLogger));
	}
}
