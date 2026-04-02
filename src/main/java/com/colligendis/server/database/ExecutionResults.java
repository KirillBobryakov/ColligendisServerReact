package com.colligendis.server.database;

import java.util.Map;

import com.colligendis.server.database.exception.AbstractServiceError;
import com.colligendis.server.database.exception.DatabaseError;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ExecutionResults {
	public static final <T extends AbstractNode> Mono<ExecutionResult<T>> INPUT_PARAMETERS_ERROR_MONO(
			String sourceMethod, String message) {
		return Mono.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(new AbstractServiceError(
						sourceMethod, message, null))
				.build());
	}

	public static final <T extends AbstractNode> Flux<ExecutionResult<T>> INPUT_PARAMETERS_ERROR_FLUX(
			String sourceMethod, String message) {
		return Flux.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(new AbstractServiceError(
						sourceMethod, message, null))
				.build());
	}

	public static final <T extends AbstractNode> Mono<ExecutionResult<T>> EXECUTION_READ_EMPTY_RESULT_MONO(
			String message,
			Map<String, Object> metadata,
			DatabaseError error) {
		return Mono.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(error)
				.build());
	}

	public static final <T extends AbstractNode> Flux<ExecutionResult<T>> EXECUTION_READ_EMPTY_RESULT_FLUX(
			String message,
			Map<String, Object> metadata,
			DatabaseError error) {
		return Flux.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(error)
				.build());
	}

	public static final <T extends AbstractNode> Mono<ExecutionResult<T>> EXECUTION_WRITE_EMPTY_RESULT_MONO(
			String message,
			Map<String, Object> metadata,
			DatabaseError error) {
		return Mono.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(error)
				.build());
	}

	public static final <T extends AbstractNode> Mono<ExecutionResult<T>> CREATING_NODE_WITH_EXISTING_UUID_ERROR_MONO(
			String sourceMethod,
			String message,
			Map<String, Object> metadata) {
		return Mono.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(new AbstractServiceError(
						sourceMethod, message, metadata))
				.build());
	}

	public static final <T extends AbstractNode> Mono<ExecutionResult<T>> RELATIONSHIP_WAS_CREATED_MONO(
			String message) {
		return Mono.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.RELATIONSHIP_WAS_CREATED)
				.build());
	}

	public static final <T extends AbstractNode> Mono<ExecutionResult<T>> NEO4J_INTERNAL_ERROR_MONO(String message,
			Map<String, Object> metadata, StackTraceElement[] stackTrace, Throwable cause) {
		return Mono.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(new AbstractServiceError(message, metadata, stackTrace, cause))
				.build());
	}

	public static final <T extends AbstractNode> Flux<ExecutionResult<T>> NEO4J_INTERNAL_ERROR_FLUX(String message,
			Map<String, Object> metadata, StackTraceElement[] stackTrace, Throwable cause) {
		return Flux.just(ExecutionResult.<T>builder()
				.status(ExecutionStatus.ERROR)
				.error(new AbstractServiceError(message, metadata, stackTrace, cause))
				.build());
	}

}
