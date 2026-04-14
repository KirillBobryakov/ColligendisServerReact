package com.colligendis.server.database.numista.service.techdata;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.techdata.LetteringScript;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class LetteringScriptService extends AbstractService {

	public Mono<ExecutionResult<LetteringScript, CreateNodeExecutionStatus>> create(LetteringScript script,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(script, colligendisUser, LetteringScript.class,
						baseLogger));
	}

	public Mono<ExecutionResult<LetteringScript, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, LetteringScript.LABEL, LetteringScript.class,
				baseLogger);
	}
}
