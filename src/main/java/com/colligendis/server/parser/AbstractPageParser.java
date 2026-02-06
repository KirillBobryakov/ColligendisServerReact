package com.colligendis.server.parser;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.jsoup.nodes.Document;

import com.colligendis.server.parser.numista.NumistaParseUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPageParser {

    public String url;

    public ParsingStatus currentParsingStatus = ParsingStatus.NOT_CHANGED;
    public Document page;
    protected Boolean showPageAfterLoad = false;

    public abstract Consumer<Stream<String>> parse();

    public UnaryOperator<AbstractPageParser> loadPage = pageParser -> {

        pageParser.page = NumistaParseUtils.loadPageByURL(pageParser.url);

        if (pageParser.page == null) {
            log.error("Error loading page by URL: {}: {}", pageParser.url, "Page is null");
            return null;
        }
        return pageParser;
    };

    public abstract Predicate<AbstractPageParser> isPageLoaded();

}
