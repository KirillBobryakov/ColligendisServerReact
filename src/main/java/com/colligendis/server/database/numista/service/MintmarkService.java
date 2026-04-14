package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Mintmark;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;

@Service
public class MintmarkService extends AbstractService {

	public Mono<ExecutionResult<Mintmark, CreateNodeExecutionStatus>> create(Mintmark mintmark,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(mintmark, colligendisUser, Mintmark.class, baseLogger));
	}

	public Mono<ExecutionResult<Mintmark, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Mintmark.LABEL, Mintmark.class, baseLogger);
	}
}
