package com.colligendis.server.parser.numista.collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumistaCollectionClientTest {

	@Test
	void isCollectionPageHtml_acceptsMyCollectionTitle() {
		String html = """
				<!doctype html>
				<html lang="en">
				<head>
				    <title>My collection &ndash; Numista</title>
				</head>
				<body></body>
				</html>
				""";
		assertTrue(NumistaCollectionClient.isCollectionPageHtml(html));
	}

	@Test
	void isCollectionPageHtml_acceptsVosPiecesTable() {
		assertTrue(NumistaCollectionClient.isCollectionPageHtml("<table id=\"vos_pieces\"></table>"));
	}

	@Test
	void isCollectionPageHtml_rejectsLoginSnippetWithPleaseLogIn() {
		String html = """
				<div class="error">Please log in to continue</div>
				""";
		assertFalse(NumistaCollectionClient.isCollectionPageHtml(html));
	}
}
