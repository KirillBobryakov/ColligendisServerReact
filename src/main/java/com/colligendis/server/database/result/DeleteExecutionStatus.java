package com.colligendis.server.database.result;

public enum DeleteExecutionStatus implements DeleteExecutionStatuses {
	EMPTY_RESULT,
	INTERNAL_ERROR,
	INPUT_PARAMETERS_ERROR,
	NOT_FOUND,
	WAS_DELETED,
	MORE_THAN_ONE_FOUND,

}
