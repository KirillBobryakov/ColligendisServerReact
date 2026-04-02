package com.colligendis.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Record;

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
	void deleteNode_nullNode_returnsInputParameterError() {
		StepVerifier.create(service.deleteNode(null, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(ExecutionStatus.ERROR);
					assertThat(er.getError()).isNotNull();
					assertThat(er.getError().message()).isEqualTo("Input parameter node is null");
				})
				.verifyComplete();
	}

	@Test
	void deleteNode_nullUuid_returnsInputParameterError() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid(null);

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(ExecutionStatus.ERROR);
					assertThat(er.getError()).isNotNull();
					assertThat(er.getError().message()).isEqualTo("Input parameter node.uuid is null");
				})
				.verifyComplete();
	}

	@Test
	void deleteNode_writeReturnsDeleted_returnsNodeWasDeleted() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid("node-uuid");
		service.setWriteResult(Mono.just(ExecutionResult.<ColligendisUser>builder()
				.node(node)
				.status(ExecutionStatus.NODE_WAS_DELETED)
				.build()));

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(ExecutionStatus.NODE_WAS_DELETED);
					assertThat(er.getNode()).isEqualTo(node);
				})
				.verifyComplete();
	}

	@Test
	void deleteNode_writeReturnsNotFound_returnsNodeIsNotFound() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid("node-uuid");
		service.setWriteResult(Mono.just(ExecutionResult.<ColligendisUser>builder()
				.status(ExecutionStatus.NODE_IS_NOT_FOUND)
				.build()));

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(ExecutionStatus.NODE_IS_NOT_FOUND);
				})
				.verifyComplete();
	}

	@Test
	void deleteNode_writeReturnsError_propagatesError() {
		ColligendisUser node = new ColligendisUser();
		node.setUuid("node-uuid");
		service.setWriteResult(ExecutionResults.<ColligendisUser>NEO4J_INTERNAL_ERROR_MONO("neo failed",
				Map.of("test", true), new StackTraceElement[0], null));

		StepVerifier.create(service.deleteNode(node, user, ColligendisUser.class, baseLogger))
				.assertNext(er -> {
					assertThat(er.getStatus()).isEqualTo(ExecutionStatus.ERROR);
					assertThat(er.getError()).isNotNull();
					assertThat(er.getError().message()).contains("neo failed");
				})
				.verifyComplete();
	}

	/** Exercises {@link AbstractService#deleteNode} without Neo4j. */
	private static final class StubAbstractService extends AbstractService {

		private Mono<ExecutionResult<ColligendisUser>> writeResult = Mono.just(
				ExecutionResult.<ColligendisUser>builder()
						.status(ExecutionStatus.NODE_WAS_DELETED)
						.build());

		void setWriteResult(Mono<ExecutionResult<ColligendisUser>> writeResult) {
			this.writeResult = writeResult != null ? writeResult : Mono.empty();
		}

		@Override
		protected <T extends AbstractNode> Mono<ExecutionResult<T>> executeWriteMono(String query,
				Map<String, Object> parameters,
				Function<Record, ExecutionResult<T>> resultMapper, String emptyResultError, String errorMessage,
				BaseLogger baseLogger) {
			@SuppressWarnings("unchecked")
			Mono<ExecutionResult<T>> cast = (Mono<ExecutionResult<T>>) (Mono<?>) writeResult;
			return cast;
		}
	}
}
