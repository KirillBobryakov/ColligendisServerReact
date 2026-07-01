package com.colligendis.server.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Record;

import com.colligendis.server.database.exception.AbstractServiceError;
import com.colligendis.server.database.result.DeleteExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.WriteExecutionStatuses;
import com.colligendis.server.logger.BaseLogger;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AbstractServiceDeleteNodeTest {

	private StubAbstractService service;
	private ColligendisUser user;
	private BaseLogger baseLogger;

	@BeforeEach
	void setUp() {
		service = new StubAbstractService();
		user = new ColligendisUser();
		user.setUuid("user-uuid");
		baseLogger = new BaseLogger();
	}

	@Test
	void deleteNode_nullNode_failsWithNullPointerException() {
		assertThatThrownBy(() -> service.deleteNode(null, user, ColligendisUser.class, baseLogger))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void deleteNode_nullUuid_returnsInputParameterError() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid(null);

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(DeleteExecutionStatus.INPUT_PARAMETERS_ERROR);
					assertThat(er.getError()).isNotNull();
					assertThat(er.getError().message()).isEqualTo("Input parameter node.uuid is null or empty");
				})
				.verifyComplete();
	}

	@Test
	void deleteNode_writeReturnsDeleted_returnsNodeWasDeleted() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid("node-uuid");
		service.setWriteResult(Mono.just(ExecutionResult.<ColligendisUser, DeleteExecutionStatus>builder()
				.node(node)
				.status(DeleteExecutionStatus.WAS_DELETED)
				.build()));

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(DeleteExecutionStatus.WAS_DELETED);
					assertThat(er.getNode()).isEqualTo(node);
				})
				.verifyComplete();
	}

	@Test
	void deleteNode_writeReturnsNotFound_returnsNodeIsNotFound() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid("node-uuid");
		service.setWriteResult(Mono.just(ExecutionResult.<ColligendisUser, DeleteExecutionStatus>builder()
				.status(DeleteExecutionStatus.NOT_FOUND)
				.build()));

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(DeleteExecutionStatus.NOT_FOUND);
				})
				.verifyComplete();
	}

	@Test
	void deleteNode_writeReturnsError_propagatesError() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid("node-uuid");
		service.setWriteResult(Mono.just(ExecutionResult.<ColligendisUser, DeleteExecutionStatus>builder()
				.error(new AbstractServiceError("neo failed", Map.of("test", true), new StackTraceElement[0], null),
						DeleteExecutionStatus.INTERNAL_ERROR)
				.build()));

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(DeleteExecutionStatus.INTERNAL_ERROR);
					assertThat(er.getError()).isNotNull();
					assertThat(er.getError().message()).contains("neo failed");
				})
				.verifyComplete();
	}

	/** Exercises {@link AbstractService#deleteNode} without Neo4j. */
	private static final class StubAbstractService extends AbstractService {

		private Mono<ExecutionResult<ColligendisUser, DeleteExecutionStatus>> writeResult = Mono.just(
				ExecutionResult.<ColligendisUser, DeleteExecutionStatus>builder()
						.status(DeleteExecutionStatus.WAS_DELETED)
						.build());

		void setWriteResult(Mono<ExecutionResult<ColligendisUser, DeleteExecutionStatus>> writeResult) {
			this.writeResult = writeResult != null ? writeResult : Mono.empty();
		}

		@Override
		protected <T extends AbstractNode, S extends WriteExecutionStatuses> Mono<ExecutionResult<T, S>> executeWriteMono(
				String query,
				Map<String, Object> parameters,
				Function<Record, ExecutionResult<T, S>> resultMapper, String emptyResultError, String errorMessage,
				BaseLogger baseLogger) {
			@SuppressWarnings("unchecked")
			Mono<ExecutionResult<T, S>> cast = (Mono<ExecutionResult<T, S>>) (Mono<?>) writeResult;
			return cast;
		}
	}
}
