package com.colligendis.server.database.result;

/**
 * Normalizes {@link ExecutionResult#getStatus()} when {@link WriteExecutionStatus} is stored
 * at runtime instead of a concrete write enum.
 */
public final class ExecutionStatusCoercion {

	private ExecutionStatusCoercion() {
	}

	public static CreateNodeExecutionStatus toCreateNode(ExecutionStatuses raw) {
		if (raw instanceof CreateNodeExecutionStatus createStatus) {
			return createStatus;
		}
		if (raw == null) {
			return CreateNodeExecutionStatus.INTERNAL_ERROR;
		}
		try {
			return CreateNodeExecutionStatus.valueOf(raw.name());
		} catch (IllegalArgumentException ex) {
			return CreateNodeExecutionStatus.INTERNAL_ERROR;
		}
	}

	public static UpdateExecutionStatus toUpdate(ExecutionStatuses raw) {
		if (raw instanceof UpdateExecutionStatus updateStatus) {
			return updateStatus;
		}
		if (raw == null) {
			return UpdateExecutionStatus.INTERNAL_ERROR;
		}
		try {
			return UpdateExecutionStatus.valueOf(raw.name());
		} catch (IllegalArgumentException ex) {
			return UpdateExecutionStatus.INTERNAL_ERROR;
		}
	}

	public static boolean isCollectionItemPersistSuccess(ExecutionStatuses raw) {
		CreateNodeExecutionStatus createStatus = toCreateNode(raw);
		if (createStatus == CreateNodeExecutionStatus.WAS_CREATED) {
			return true;
		}
		UpdateExecutionStatus updateStatus = toUpdate(raw);
		return updateStatus == UpdateExecutionStatus.WAS_UPDATED
				|| updateStatus == UpdateExecutionStatus.NOTHING_TO_UPDATE;
	}
}
