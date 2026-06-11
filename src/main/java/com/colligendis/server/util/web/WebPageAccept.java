package com.colligendis.server.util.web;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * {@code Accept} header presets for {@link WebPageClient}.
 */
public enum WebPageAccept {

	HTML(MediaType.TEXT_HTML_VALUE + ", */*"),
	JSON(MediaType.APPLICATION_JSON_VALUE),
	HTML_AND_JSON(MediaType.TEXT_HTML_VALUE + ", " + MediaType.APPLICATION_JSON_VALUE + ", */*");

	private final String headerValue;

	WebPageAccept(String headerValue) {
		this.headerValue = headerValue;
	}

	public String headerValue() {
		return headerValue;
	}

	/**
	 * Maps {@code colligendis.web.client.accept} values. Unrecognized non-blank strings are
	 * treated as a raw {@code Accept} header value.
	 */
	public static ResolvedAccept resolve(String configValue) {
		if (!StringUtils.hasText(configValue)) {
			return new ResolvedAccept(HTML, HTML.headerValue());
		}
		String normalized = configValue.strip();
		return switch (normalized.toLowerCase()) {
			case "json", "application/json" -> preset(JSON);
			case "html-and-json", "html_and_json", "both" -> preset(HTML_AND_JSON);
			case "html", "text/html" -> preset(HTML);
			default -> new ResolvedAccept(null, normalized);
		};
	}

	private static ResolvedAccept preset(WebPageAccept accept) {
		return new ResolvedAccept(accept, accept.headerValue());
	}

	public record ResolvedAccept(WebPageAccept preset, String headerValue) {
	}
}
