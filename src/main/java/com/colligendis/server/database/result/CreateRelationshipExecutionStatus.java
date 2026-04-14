package com.colligendis.server.database.result;

public enum CreateRelationshipExecutionStatus implements CreateRelationshipExecutionStatuses {
	EMPTY_RESULT,
	INTERNAL_ERROR,
	INPUT_PARAMETERS_ERROR,
	IS_ALREADY_EXISTS,
	WAS_CREATED,
	SOURCE_OR_TARGET_NODE_IS_NOT_FOUND,

}
