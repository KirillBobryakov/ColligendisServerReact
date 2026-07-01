package com.colligendis.server.parser.numista.collection;

import lombok.Getter;

@Getter
public class NumistaCollectionHttpException extends RuntimeException {

	private final int statusCode;
	private final String responseBody;

	public NumistaCollectionHttpException(String message, int statusCode, String responseBody) {
		super(message);
		this.statusCode = statusCode;
		this.responseBody = responseBody == null ? "" : responseBody;
	}
}
