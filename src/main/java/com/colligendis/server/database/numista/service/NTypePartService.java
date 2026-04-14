package com.colligendis.server.database.numista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Artist;
import com.colligendis.server.database.numista.model.NTypePart;
import com.colligendis.server.database.numista.model.techdata.LetteringScript;
import com.colligendis.server.database.numista.model.techdata.PART_TYPE;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class NTypePartService extends AbstractService {

	public Mono<ExecutionResult<NTypePart, CreateNodeExecutionStatus>> create(PART_TYPE partType,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(new NTypePart(partType), colligendisUser, NTypePart.class,
						baseLogger));
	}

	public Mono<ExecutionResult<NTypePart, DeleteExecutionStatus>> delete(NTypePart nTypePart,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteNode(nTypePart, colligendisUser, NTypePart.class, baseLogger));
	}

	public Mono<ExecutionResult<NTypePart, UpdateExecutionStatus>> update(NTypePart nTypePart,
			Mono<ColligendisUser> numistaParserUserMono,
			BaseLogger baseLogger) {
		return numistaParserUserMono
				.flatMap(numistaParserUser -> super.updateNodeProperties(nTypePart, numistaParserUser, NTypePart.class,
						baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setEngravers(NTypePart nTypePart,
			List<Artist> engravers,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nTypePart, engravers,
						Artist.class,
						NTypePart.ENGRAVING_WAS_DONE_BY, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setDesigners(NTypePart nTypePart,
			List<Artist> designers,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nTypePart, designers,
						Artist.class,
						NTypePart.DESIGN_WAS_DONE_BY, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setLetteringScripts(
			NTypePart nTypePart,
			List<LetteringScript> letteringScripts, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(nTypePart, letteringScripts,
						LetteringScript.class, NTypePart.WRITE_ON_SCRIPT, colligendisUser, baseLogger));
	}
}
