package com.colligendis.server.database.result;

public enum ExistsExecutionStatus implements ExistsExecutionStatuses {
	EMPTY_RESULT,
	INTERNAL_ERROR,
	INPUT_PARAMETERS_ERROR,
	EXISTS,
	NOT_EXISTS,
	MORE_THAN_ONE_FOUND,

}
