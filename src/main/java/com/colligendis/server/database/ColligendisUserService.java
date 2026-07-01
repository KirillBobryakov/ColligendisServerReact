package com.colligendis.server.database;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExistsExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ColligendisUserService extends AbstractService {

    private static final String NUMISTA_PARSER_USER = "NUMISTA_PARSER";

    private Mono<ColligendisUser> numistaParserUserMono;

    @PostConstruct
    public void init() {
        this.numistaParserUserMono = findByUsername(NUMISTA_PARSER_USER, new BaseLogger())
                .map(executionResult -> executionResult.getNode()).cache();
    }

    public Mono<ColligendisUser> getNumistaParserUserMono() {
        if (numistaParserUserMono == null) {
            throw new IllegalStateException(
                    "numistaParserUserMono not initialized - ensure ColligendisUserService @PostConstruct has run");
        }
        return numistaParserUserMono;
    }

    public Mono<ExecutionResult<ColligendisUser, CreateNodeExecutionStatus>> create(ColligendisUser colligendisUser,
            BaseLogger baseLogger) {
        if (!StringUtils.hasText(colligendisUser.getUsername())
                || !StringUtils.hasText(colligendisUser.getPassword())) {
            log.error("Refusing to create ColligendisUser without username/password");
            return Mono.just(ExecutionResult.<ColligendisUser, CreateNodeExecutionStatus>builder()
                    .status(CreateNodeExecutionStatus.INPUT_PARAMETERS_ERROR)
                    .build());
        }

        log.debug("Creating ColligendisUser with username: {}", colligendisUser.getUsername());
        return super.createNode(colligendisUser, null, ColligendisUser.class, baseLogger)
                .flatMap(executionResult -> Mono.just(executionResult));
    }

    public Mono<ExecutionResult<ColligendisUser, FindExecutionStatus>> findByUuid(String uuid, BaseLogger baseLogger) {
        return super.findNodeByUuid(uuid, ColligendisUser.LABEL, ColligendisUser.class, baseLogger);
    }

    public Mono<ExecutionResult<ColligendisUser, FindExecutionStatus>> findByUsername(String username,
            BaseLogger baseLogger) {
        return super.findNodeByUniquePropertyValue("username", username, ColligendisUser.LABEL,
                ColligendisUser.class, baseLogger)
                .flatMap(executionResult -> Mono.just(executionResult));
    }

    public Mono<Boolean> existsByUsername(String username, BaseLogger baseLogger) {
        return super.isNodeExistsByUniquePropertyValue("username", username, ColligendisUser.LABEL,
                ColligendisUser.class, baseLogger)
                .map(result -> result.getStatus() == ExistsExecutionStatus.EXISTS);
    }

    public Mono<ColligendisUser> findUserByUsername(String username, BaseLogger baseLogger) {
        return findByUsername(username, baseLogger)
                .flatMap(result -> {
                    if (result.getStatus() == FindExecutionStatus.FOUND && result.getNode() != null) {
                        return Mono.just(result.getNode());
                    }
                    return Mono.empty();
                });
    }

    public Mono<ColligendisUser> requireAuthenticatedUser(BaseLogger baseLogger) {
        return optionalAuthenticatedUser(baseLogger)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")));
    }

    public Mono<ColligendisUser> optionalAuthenticatedUser(BaseLogger baseLogger) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    if (securityContext.getAuthentication() == null
                            || !securityContext.getAuthentication().isAuthenticated()) {
                        return Mono.empty();
                    }
                    String username = securityContext.getAuthentication().getName();
                    return findUserByUsername(username, baseLogger);
                });
    }

    public Mono<ExecutionResult<ColligendisUser, UpdateExecutionStatus>> saveNumistaCookie(
            String username,
            String numistaCookie,
            BaseLogger baseLogger) {
        return findByUsername(username, baseLogger)
                .flatMap(findResult -> {
                    if (findResult.getStatus() != FindExecutionStatus.FOUND || findResult.getNode() == null) {
                        return Mono.just(ExecutionResult.<ColligendisUser, UpdateExecutionStatus>builder()
                                .status(UpdateExecutionStatus.NOT_FOUND)
                                .build());
                    }

                    ColligendisUser userToUpdate = new ColligendisUser();
                    userToUpdate.setUuid(findResult.getNode().getUuid());
                    userToUpdate.setNumistaCookie(numistaCookie);

                    return super.updateNodeProperties(userToUpdate, null, ColligendisUser.class, baseLogger);
                });
    }

}
