package com.colligendis.server.util.web;

import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;

/**
 * Raw HTTP result from {@link WebPageClient}.
 */
public record WebPageResponse(int statusCode, String body) {

	public static WebPageResponse of(HttpStatusCode statusCode, String body) {
		return new WebPageResponse(statusCode.value(), body == null ? "" : body);
	}

	public boolean is2xxSuccessful() {
		return statusCode >= 200 && statusCode < 300;
	}

	public boolean hasBody() {
		return StringUtils.hasText(body);
	}

	public int bodyLength() {
		return body == null ? 0 : body.length();
	}
}
