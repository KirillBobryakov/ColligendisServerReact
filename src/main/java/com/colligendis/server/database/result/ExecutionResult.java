package com.colligendis.server.database.result;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.exception.DatabaseError;
import com.colligendis.server.logger.BaseLogger;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecutionResult<T extends AbstractNode, S extends ExecutionStatuses> {

	private T node;
	private S status;
	private DatabaseError error;

	public static <T extends AbstractNode, S extends ExecutionStatuses> Builder<T, S> builder() {
		return new Builder<>();
	}

	public void logError(BaseLogger baseLogger) {
		if (error != null) {
			baseLogger.traceRed("Source Method: {}", error.sourceMethod());
			baseLogger.traceRed("Error: {}", error.message());
			baseLogger.traceRed("Metadata: {}", error.metadata());
			baseLogger.traceRed("StackTrace: {}", error.stackTrace());
			baseLogger.traceRed("Cause: {}", error.cause());
		}
	}

	public static class Builder<T extends AbstractNode, S extends ExecutionStatuses> {
		private T node;
		private S status;
		private DatabaseError error;

		public Builder<T, S> node(T node) {
			this.node = node;
			return this;
		}

		public Builder<T, S> error(DatabaseError error, S status) {
			this.status = status;
			this.error = error;
			return this;
		}

		public Builder<T, S> status(S status) {
			this.status = status;
			return this;
		}

		public ExecutionResult<T, S> build() {
			return new ExecutionResult<>(node, status, error);
		}
	}

}
