package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Mint;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;
import reactor.core.publisher.Mono;

@Service
public class MintService extends AbstractService {

	public Mono<ExecutionResult<Mint, CreateNodeExecutionStatus>> create(Mint mint,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(mint, colligendisUser, Mint.class, baseLogger));
	}

	public Mono<ExecutionResult<Mint, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, Mint.LABEL, Mint.class, baseLogger);
	}

	public Mono<ExecutionResult<Mint, UpdateExecutionStatus>> update(Mint mint,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(mint, colligendisUser, Mint.class,
						baseLogger));
	}

}
