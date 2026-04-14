package com.colligendis.server.database.result;

public enum CreateNodeExecutionStatus implements CreateNodeExecutionStatuses {
	EMPTY_RESULT,
	INTERNAL_ERROR,
	INPUT_PARAMETERS_ERROR,
	WAS_CREATED,
	NOT_CREATED,
	NODE_ALREADY_EXISTS,

}
