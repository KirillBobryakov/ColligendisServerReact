package com.colligendis.server.database.numista.service;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.RulingAuthority;
import com.colligendis.server.database.numista.model.RulingAuthorityGroup;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class RulingAuthorityService extends AbstractService {

	public Mono<ExecutionResult<RulingAuthority, CreateNodeExecutionStatus>> create(RulingAuthority rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(rulingAuthority, colligendisUser, RulingAuthority.class,
						baseLogger));
	}

	public Mono<ExecutionResult<RulingAuthority, UpdateExecutionStatus>> update(RulingAuthority rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(rulingAuthority, colligendisUser,
						RulingAuthority.class, baseLogger));
	}

	public Mono<ExecutionResult<RulingAuthority, FindExecutionStatus>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, RulingAuthority.LABEL, RulingAuthority.class,
				baseLogger);
	}

	public Mono<ExecutionResult<RulingAuthority, ? extends ExecutionStatuses>> findByNidWithCreate(String nid,
			String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case FOUND:
							return Mono.just(executionResult);
						case NOT_FOUND:
							return create(new RulingAuthority(nid, name), colligendisUserMono, baseLogger);
						default:
							executionResult.logError(baseLogger);
							return Mono.just(executionResult);
					}
				});
	}

	public Flux<ExecutionResult<RulingAuthority, FindExecutionStatus>> findAllByIssuer(Issuer issuer,
			BaseLogger baseLogger) {
		return super.getAllSourceNodesWithRelationshipType(issuer, RulingAuthority.RULES_WHEN_BEEN,
				RulingAuthority.class, baseLogger);
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setRulingAuthorityGroup(
			RulingAuthority rulingAuthority,
			RulingAuthorityGroup rulingAuthorityGroup,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(
						colligendisUser -> super.createUniqueTargetedRelationship(rulingAuthority, rulingAuthorityGroup,
								RulingAuthority.GROUP_BY, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> detachIssuerForRulingAuthority(
			RulingAuthority rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteAllOutgoingRelationshipsWithRelationshipType(rulingAuthority,
						RulingAuthority.RULES_WHEN_BEEN, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> detachRulingAuthoritiesFromYears(
			AbstractNode rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteAllOutgoingRelationshipsWithRelationshipType(rulingAuthority,
						RulingAuthority.RULES_FROM, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setRulingAuthoritiesFromYears(
			RulingAuthority rulingAuthority,
			List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(rulingAuthority, years, Year.class,
						RulingAuthority.RULES_FROM, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, DeleteExecutionStatus>> detachRulesTillYears(AbstractNode rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteAllOutgoingRelationshipsWithRelationshipType(rulingAuthority,
						RulingAuthority.RULES_TILL, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setRulingAuthoritiesTillYears(
			RulingAuthority rulingAuthority,
			List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(rulingAuthority, years, Year.class,
						RulingAuthority.RULES_TILL, colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setIssuer(
			RulingAuthority rulingAuthority, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(rulingAuthority, issuer,
						RulingAuthority.RULES_WHEN_BEEN,
						colligendisUser, baseLogger));
	}

	public Mono<ExecutionResult<AbstractNode, CreateRelationshipExecutionStatus>> setIssuer(
			List<RulingAuthority> rulingAuthorities, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueIncomingRelationships(issuer, rulingAuthorities,
						RulingAuthority.class,
						RulingAuthority.RULES_WHEN_BEEN,
						colligendisUser, baseLogger));
	}

}
