package com.colligendis.server.parser.numista;

import lombok.Getter;

@Getter
public class NumistaPageLoadException extends RuntimeException {

	private final String url;
	private final int statusCode;

	public NumistaPageLoadException(String message, String url) {
		this(message, url, 0);
	}

	public NumistaPageLoadException(String message, String url, int statusCode) {
		super(message);
		this.url = url;
		this.statusCode = statusCode;
	}
}
