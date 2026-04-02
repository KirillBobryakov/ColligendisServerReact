package com.colligendis.server.database.exception;

import java.util.Map;

public interface DatabaseError {
	String sourceMethod();

	String message();

	Map<String, Object> metadata();

	StackTraceElement[] stackTrace();

	Throwable cause();

}
