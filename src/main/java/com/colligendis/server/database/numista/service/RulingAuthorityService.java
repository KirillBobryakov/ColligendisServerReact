package com.colligendis.server.database.numista.service;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.AbstractService;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ExecutionResult;
import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.RulingAuthority;
import com.colligendis.server.database.numista.model.RulingAuthorityGroup;
import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class RulingAuthorityService extends AbstractService {

	public Mono<ExecutionResult<RulingAuthority>> create(RulingAuthority rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createNode(rulingAuthority, colligendisUser, RulingAuthority.class,
						baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_CREATED)) {
						baseLogger.trace("RulingAuthority was created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOT_CREATED)) {
						baseLogger.traceRed("RulingAuthority was not created: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to create RulingAuthority: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<RulingAuthority>> update(RulingAuthority rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.updateNodeProperties(rulingAuthority, colligendisUser,
						RulingAuthority.class, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_WAS_UPDATED)) {
						baseLogger.trace("RulingAuthority was updated: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("RulingAuthority was not updated: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_NOTHING_TO_UPDATE)) {
						baseLogger.traceOrange("RulingAuthority has no properties to update: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to update RulingAuthority: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<RulingAuthority>> findByNid(String nid, BaseLogger baseLogger) {
		return super.findNodeByUniquePropertyValue("nid", nid, RulingAuthority.LABEL, RulingAuthority.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("RulingAuthority was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("RulingAuthority was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one RulingAuthority was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find RulingAuthority: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<RulingAuthority>> findByNidWithSave(String nid, String name,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return findByNid(nid, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						return create(new RulingAuthority(nid, name), colligendisUserMono, baseLogger);
					} else {
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Flux<ExecutionResult<RulingAuthority>> findAllByIssuer(Issuer issuer, BaseLogger baseLogger) {
		return super.getAllSourceNodesWithRelationshipType(issuer, RulingAuthority.RULES_WHEN_BEEN,
				RulingAuthority.class, baseLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
						baseLogger.trace("RulingAuthority was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_NOT_FOUND)) {
						baseLogger.traceRed("RulingAuthority was not found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.MORE_THAN_ONE_NODE_IS_FOUND)) {
						baseLogger.traceRed("More than one RulingAuthority was found: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to find RulingAuthority: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setRulingAuthorityGroup(RulingAuthority rulingAuthority,
			RulingAuthorityGroup rulingAuthorityGroup,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(
						colligendisUser -> super.createUniqueTargetedRelationship(rulingAuthority, rulingAuthorityGroup,
								RulingAuthority.GROUP_BY, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("RulingAuthority group was set: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("RulingAuthority group is already set: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set RulingAuthority group: {}", executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> detachIssuerForRulingAuthority(RulingAuthority rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteAllOutgoingRelationshipsWithRelationshipType(rulingAuthority,
						RulingAuthority.RULES_WHEN_BEEN, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_DELETED)) {
						baseLogger.trace("Issuer was detached for RulingAuthority: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS)) {
						baseLogger.traceOrange("Issuer is not attached to RulingAuthority: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to detach Issuer for RulingAuthority: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> detachRulingAuthoritiesFromYears(AbstractNode rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteAllOutgoingRelationshipsWithRelationshipType(rulingAuthority,
						RulingAuthority.RULES_FROM, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_DELETED)) {
						baseLogger.trace("RulingAuthorities were detached from Years: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS)) {
						baseLogger.traceOrange("RulingAuthorities are not attached to Years: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to detach RulingAuthorities from Years: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setRulingAuthoritiesFromYears(RulingAuthority rulingAuthority,
			List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(rulingAuthority, years, Year.class,
						RulingAuthority.RULES_FROM, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("RulingAuthorities were set from Years: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set RulingAuthorities from Years: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> detachRulesTillYears(AbstractNode rulingAuthority,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.deleteAllOutgoingRelationshipsWithRelationshipType(rulingAuthority,
						RulingAuthority.RULES_TILL, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_DELETED)) {
						baseLogger.trace("Rules till years were detached from RulingAuthority: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_NOT_EXISTS)) {
						baseLogger.traceOrange("Rules till years are not attached to RulingAuthority: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to detach Rules till years from RulingAuthority: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setRulingAuthoritiesTillYears(RulingAuthority rulingAuthority,
			List<Year> years,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueOutgoingRelationships(rulingAuthority, years, Year.class,
						RulingAuthority.RULES_TILL, colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("RulingAuthorities were set till Years: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set RulingAuthorities till Years: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setIssuer(RulingAuthority rulingAuthority, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueTargetedRelationship(rulingAuthority, issuer,
						RulingAuthority.RULES_WHEN_BEEN,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Issuer was set for RulingAuthority: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						baseLogger.traceOrange("Issuer is already set for RulingAuthority: {}",
								executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set Issuer for RulingAuthority: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

	public Mono<ExecutionResult<AbstractNode>> setIssuer(List<RulingAuthority> rulingAuthorities, Issuer issuer,
			Mono<ColligendisUser> colligendisUserMono, BaseLogger baseLogger) {
		return colligendisUserMono
				.flatMap(colligendisUser -> super.createUniqueIncomingRelationships(issuer, rulingAuthorities,
						RulingAuthority.class,
						RulingAuthority.RULES_WHEN_BEEN,
						colligendisUser, baseLogger))
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						baseLogger.trace("Issuer was set for RulingAuthorities: {}", executionResult.getNode());
						return Mono.just(executionResult);
					} else {
						baseLogger.traceRed("Failed to set Issuer for RulingAuthorities: {}",
								executionResult.getStatus());
						executionResult.logError(baseLogger);
						return Mono.just(executionResult);
					}
				});
	}

}
