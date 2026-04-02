package com.colligendis.server.database.exception;

import java.util.Map;

import lombok.Data;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class AbstractServiceError implements DatabaseError {

	private String sourceMethod;
	private String message;
	private Map<String, Object> metadata;
	private StackTraceElement[] stackTrace;
	private Throwable cause;

	public AbstractServiceError(String sourceMethod, String message, Map<String, Object> metadata) {
		this(sourceMethod, message, metadata, null, null);
	}

	public AbstractServiceError(String message, Map<String, Object> metadata,
			StackTraceElement[] stackTrace, Throwable cause) {
		this(null, message, metadata, stackTrace, cause);
	}

	@Override
	public String sourceMethod() {
		return sourceMethod;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public Map<String, Object> metadata() {
		return metadata;
	}

	@Override
	public StackTraceElement[] stackTrace() {
		return stackTrace;
	}

	@Override
	public Throwable cause() {
		return cause;
	}

}
