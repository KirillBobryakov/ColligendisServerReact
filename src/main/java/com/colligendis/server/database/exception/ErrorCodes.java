package com.colligendis.server.database.exception;

public class ErrorCodes {

	public static final String UNKNOWN_EXECUTION_STATUS = "UNKNOWN_EXECUTION_STATUS";

	public static final String EXECUTION_WRITE_EMPTY_RESULT = "EXECUTION_WRITE_EMPTY_RESULT";
	public static final String EXECUTION_READ_EMPTY_RESULT = "EXECUTION_READ_EMPTY_RESULT";

	public static final String NEO4J_INTERNAL_ERROR = "NEO4J_INTERNAL_ERROR";

	public static final String INPUT_PARAMETERS_ERROR = "INPUT_PARAMETERS_ERROR";

	/**
	 * Error code for updating a node that does not exist. This error is thrown when
	 * the node.uuid is null.
	 */
	public static final String UPDATING_NOT_EXISTING_NODE_ERROR = "UPDATING_NOT_EXISTING_NODE_ERROR";

	/**
	 * Error code for creating a node with an existing uuid. This error is thrown
	 * when
	 * the node.uuid is not null.
	 */
	public static final String CREATING_NODE_WITH_EXISTING_UUID_ERROR = "CREATING_NODE_WITH_EXISTING_UUID_ERROR";

	public static final String NODE_NOT_FOUND_ERROR = "NODE_NOT_FOUND_ERROR";
	public static final String RELATIONSHIP_NOT_FOUND_ERROR = "RELATIONSHIP_NOT_FOUND_ERROR";

	public static final String NODE_LABEL_EMPTY_ERROR = "NODE_LABEL_EMPTY_ERROR";

	public static final String NODE_ALREADY_EXISTS_ERROR = "NODE_ALREADY_EXISTS_ERROR";
	public static final String RELATIONSHIP_ALREADY_EXISTS_ERROR = "RELATIONSHIP_ALREADY_EXISTS_ERROR";

}
