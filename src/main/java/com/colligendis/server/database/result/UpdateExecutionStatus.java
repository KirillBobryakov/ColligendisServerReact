package com.colligendis.server.database.result;

public enum UpdateExecutionStatus implements UpdateExecutionStatuses {
	EMPTY_RESULT,
	INTERNAL_ERROR,
	INPUT_PARAMETERS_ERROR,
	WAS_UPDATED,
	NOT_FOUND,
	NOTHING_TO_UPDATE,

}
