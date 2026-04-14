package com.colligendis.server.database.result;

public enum FindExecutionStatus implements FindExecutionStatuses {
	EMPTY_RESULT,
	INTERNAL_ERROR,
	INPUT_PARAMETERS_ERROR,
	FOUND,
	NOT_FOUND,
	MORE_THAN_ONE_FOUND,

}
