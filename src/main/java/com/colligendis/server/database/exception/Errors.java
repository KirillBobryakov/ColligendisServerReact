// package com.colligendis.server.database.exception;

// import java.util.Map;

// public final class Errors {

// public static final DatabaseError UNKNOWN_EXECUTION_STATUS(String message,
// Map<String, Object> metadata) {
// return new DefaultError(
// ErrorCodes.UNKNOWN_EXECUTION_STATUS, message, metadata);
// }

// public static final DatabaseError EXECUTION_WRITE_EMPTY_RESULT(String
// message, Map<String, Object> metadata) {
// return new DefaultError(
// ErrorCodes.EXECUTION_WRITE_EMPTY_RESULT, message, metadata);
// }

// public static final DatabaseError EXECUTION_READ_EMPTY_RESULT(String message,
// Map<String, Object> metadata) {
// return new DefaultError(
// ErrorCodes.EXECUTION_READ_EMPTY_RESULT, message, metadata);
// }

// public static final DatabaseError NEO4J_INTERNAL_ERROR(String message,
// Map<String, Object> metadata,
// StackTraceElement[] stackTrace,
// Throwable cause) {
// return new DefaultError(
// ErrorCodes.NEO4J_INTERNAL_ERROR, message, metadata, stackTrace, cause);
// }

// public static final DatabaseError INPUT_PARAMETERS_ERROR(String message,
// Map<String, Object> metadata) {
// return new DefaultError(
// ErrorCodes.INPUT_PARAMETERS_ERROR, message, metadata);
// }

// public static final DatabaseError RELATIONSHIP_ALREADY_EXISTS_ERROR(String
// message, Map<String, Object> metadata) {
// return new DefaultError(
// ErrorCodes.RELATIONSHIP_ALREADY_EXISTS_ERROR, message, metadata);
// }

// public static final DatabaseError RELATIONSHIP_NOT_FOUND_ERROR(String
// message, Map<String, Object> metadata) {
// return new DefaultError(
// ErrorCodes.RELATIONSHIP_NOT_FOUND_ERROR, message, metadata);
// }

// public static final DatabaseError UPDATING_NOT_EXISTING_NODE_ERROR(String
// message) {
// return new DefaultError(
// ErrorCodes.UPDATING_NOT_EXISTING_NODE_ERROR, message, null);
// }

// public static final DatabaseError
// CREATING_NODE_WITH_EXISTING_UUID_ERROR(String message) {
// return new DefaultError(
// ErrorCodes.CREATING_NODE_WITH_EXISTING_UUID_ERROR, message, null);
// }
// }
