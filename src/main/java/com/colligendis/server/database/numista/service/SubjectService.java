package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Subject;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class SubjectService extends AbstractService {

	public Mono<ExecutionResult<Subject, CreateNodeExecutionStatus>> create(Subject subject,
			Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(subject, colligendisUser, Subject.class, baseLogger));
	}

	public Mono<ExecutionResult<Subject, FindExecutionStatus>> findByNumistaCode(String numistaCode,
			BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, Subject.LABEL, Subject.class,
				baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> relateToCountry(Subject subject,
			Country country,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(subject, country,
						Subject.RELATE_TO_COUNTRY,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> relateToParentSubject(Subject subject,
			Subject parentSubject,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(subject, parentSubject,
						Subject.PARENT_SUBJECT,
						colligendisUser, baseLogger));
	}

}
