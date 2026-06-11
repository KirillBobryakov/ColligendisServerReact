package com.colligendis.server.util.web;

/**
 * Thrown when a page cannot be loaded (invalid URL, missing cookie, HTTP failure).
 */
public class WebPageLoadException extends RuntimeException {

	private final String url;
	private final int statusCode;

	public WebPageLoadException(String message, String url) {
		this(message, url, 0);
	}

	public WebPageLoadException(String message, String url, int statusCode) {
		super(message);
		this.url = url == null ? "" : url;
		this.statusCode = statusCode;
	}

	public String getUrl() {
		return url;
	}

	public int getStatusCode() {
		return statusCode;
	}
}
