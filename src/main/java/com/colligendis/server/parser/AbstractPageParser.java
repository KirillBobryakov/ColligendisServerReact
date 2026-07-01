package com.colligendis.server.parser;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.jsoup.nodes.Document;
import org.springframework.util.StringUtils;

import com.colligendis.server.util.web.WebPageClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPageParser {

	protected final WebPageClient webPageClient;

	public String url;

	/** Optional {@code Cookie} header value; when blank, no cookie is sent. */
	public String cookie;

	public ParsingStatus currentParsingStatus = ParsingStatus.NOT_CHANGED;
	public Document page;
	protected Boolean showPageAfterLoad = false;

	protected AbstractPageParser(WebPageClient webPageClient) {
		this.webPageClient = webPageClient;
	}

	public abstract Consumer<Stream<String>> parse();

	public UnaryOperator<AbstractPageParser> loadPage() {
		return this::loadPageIntoParser;
	}

	protected AbstractPageParser loadPageIntoParser(AbstractPageParser pageParser) {
		if (!StringUtils.hasText(pageParser.url)) {
			log.error("Cannot load page: URL is empty");
			return null;
		}

		String cookieHeader = StringUtils.hasText(pageParser.cookie) ? pageParser.cookie.strip() : null;

		Document document = webPageClient
				.loadPageDocument(pageParser.url, cookieHeader)
				.block();

		if (document == null) {
			log.error("Error loading page by URL: {}: page not found or empty response", pageParser.url);
			return null;
		}

		pageParser.page = document;

		if (Boolean.TRUE.equals(pageParser.showPageAfterLoad)) {
			log.debug("Loaded page {} (title={})", pageParser.url, document.title());
		}

		return pageParser;
	}

	public abstract Predicate<AbstractPageParser> isPageLoaded();

}
