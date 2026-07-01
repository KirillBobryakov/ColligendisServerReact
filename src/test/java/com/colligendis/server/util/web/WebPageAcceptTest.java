package com.colligendis.server.util.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class WebPageAcceptTest {

	@Test
	void resolveHtml() {
		var resolved = WebPageAccept.resolve("html");
		assertEquals(WebPageAccept.HTML, resolved.preset());
		assertEquals(MediaType.TEXT_HTML_VALUE + ", */*", resolved.headerValue());
	}

	@Test
	void resolveJson() {
		var resolved = WebPageAccept.resolve("json");
		assertEquals(WebPageAccept.JSON, resolved.preset());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, resolved.headerValue());
	}

	@Test
	void resolveHtmlAndJson() {
		var resolved = WebPageAccept.resolve("html-and-json");
		assertEquals(WebPageAccept.HTML_AND_JSON, resolved.preset());
		assertEquals(
				MediaType.TEXT_HTML_VALUE + ", " + MediaType.APPLICATION_JSON_VALUE + ", */*",
				resolved.headerValue());
	}

	@Test
	void resolveCustomAcceptHeader() {
		var resolved = WebPageAccept.resolve("application/vnd.api+json");
		assertNull(resolved.preset());
		assertEquals("application/vnd.api+json", resolved.headerValue());
	}
}
