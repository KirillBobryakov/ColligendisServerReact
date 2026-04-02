package com.colligendis.server.database.numista.service;

import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Subject;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class SubjectService extends AbstractService {

	public Mono<ExecutionResult<Subject>> create(Subject subject, Mono<ColligendisUser> colligendisUserMono,
			BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(subject, colligendisUser, Subject.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("Subject was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("Subject was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create Subject: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<Subject>> findByNumistaCode(String numistaCode, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("numistaCode", numistaCode, Subject.LABEL, Subject.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("Subject was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("Subject was not found: {}", executionResult.getStatus());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one Subject was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find Subject: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> relateToCountry(Subject subject, Country country,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(subject, country,
						Subject.RELATE_TO_COUNTRY,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Subject was related to Country: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to relate Subject to Country: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> relateToParentSubject(Subject subject, Subject parentSubject,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(subject, parentSubject,
						Subject.PARENT_SUBJECT,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Subject was related to Parent Subject: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to relate Subject to Parent Subject: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

}
