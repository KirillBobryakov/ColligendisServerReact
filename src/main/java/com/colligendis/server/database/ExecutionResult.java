package com.colligendis.server.database;

import com.colligendis.server.database.exception.DatabaseError;
import com.colligendis.server.logger.BaseLogger;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecutionResult<T extends AbstractNode> {

	private T node;
	private ExecutionStatus status;
	private DatabaseError error;

	public static <T extends AbstractNode> Builder<T> builder() {
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

	public static class Builder<T extends AbstractNode> {
		private T node;
		private ExecutionStatus status;
		private DatabaseError error;

		public Builder<T> node(T node) {
			this.node = node;
			return this;
		}

		public Builder<T> error(DatabaseError error) {
			this.status = ExecutionStatus.ERROR;
			this.error = error;
			return this;
		}

		public Builder<T> status(ExecutionStatus status) {
			this.status = status;
			return this;
		}

		public ExecutionResult<T> build() {
			return new ExecutionResult<>(node, status, error);
		}
	}

}
