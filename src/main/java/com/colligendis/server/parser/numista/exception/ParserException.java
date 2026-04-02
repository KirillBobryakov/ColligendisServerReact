package com.colligendis.server.parser.numista.exception;

public class ParserException extends RuntimeException {

	final String parserMessage;

	public ParserException(String parserMessage) {
		super(parserMessage);
		this.parserMessage = parserMessage;
	}

	public ParserException(String parserMessage, Throwable cause) {
		super(parserMessage, cause);
		this.parserMessage = parserMessage;
	}
}
