package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.Subject;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class IssuerService extends AbstractService {

	public Mono<ExecutionResult<Issuer, CreateNodeExecutionStatus>> create(Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(issuer, colligendisUser, Issuer.class, baseLogger));
	}

	public Mono<ExecutionResult<Issuer, UpdateExecutionStatus>> update(Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(issuer, colligendisUser, Issuer.class,
						baseLogger));
	}

	public Mono<ExecutionResult<Issuer, FindExecutionStatus>> findByNumistaCode(String numistaCode,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, Issuer.LABEL, Issuer.class, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> relateToCountry(Issuer issuer,
			Country country,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(issuer, country,
						Issuer.RELATE_TO_COUNTRY,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> relateToSubject(Issuer issuer,
			Subject subject,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(issuer, subject,
						Issuer.RELATE_TO_SUBJECT,
						colligendisUser, baseLogger));
	}

}
