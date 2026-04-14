package com.colligendis.server.parser.numista.init_parser;

import java.net.URI;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.parser.numista.NumistaParseUtils;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.service.CollectibleTypeService;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.logger.LogExecutionTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectibleTypeTreeParser {

	public static final String TYPES_URL = "https://en.numista.com/catalogue/types.php";

	private final CollectibleTypeService collectibleTypeService;

	private final ColligendisUserService colligendisUserService;

	private final BaseLogger collectibleTypeTreeParserLogger = new BaseLogger();

	@LogExecutionTime
	public void parseAndSave() {
		Document doc = loadDocument();
		if (doc == null) {
			log.error("Failed to load Numista types page: {}", TYPES_URL);
			return;
		}

		Element rootUl = doc.selectFirst("ul#types_list");

		if (rootUl == null) {
			log.error("Could not find root <ul id=types_list> on types page");
			return;
		}

		Elements topLis = rootUl.children();
		for (Element li : topLis) {
			if (!li.tagName().equals("li"))
				continue;
			processLi(li, null);
		}
	}

	private Document loadDocument() {
		try {
			// Reuse headers similar to AbstractPageParser
			return Jsoup.connect(TYPES_URL)
					.userAgent(NumistaParseUtils.USER_AGENT)
					.method(org.jsoup.Connection.Method.GET)
					.get();
		} catch (Exception e) {
			log.error("Error loading types page: {}", TYPES_URL, e);
			return null;
		}
	}

	private void processLi(Element li, CollectibleType parent) {
		CollectibleType current = upsertTypeFromLi(li).block();
		if (current == null)
			return;

		if (parent != null) {
			collectibleTypeService.linkParentChild(parent, current, collectibleTypeTreeParserLogger).block();
		}

		Element childUl = null;
		for (Element child : li.children()) {
			if ("ul".equals(child.tagName())) {
				childUl = child;
				break;
			}
		}
		if (childUl == null)
			return;

		for (Element childLi : childUl.children()) {
			if (!childLi.tagName().equals("li"))
				continue;
			processLi(childLi, current);
		}
	}

	private Mono<CollectibleType> upsertTypeFromLi(Element li) {
		Element link = null;
		for (Element child : li.children()) {
			if ("a".equals(child.tagName()) && child.hasAttr("href")) {
				link = child;
				break;
			}
		}
		if (link == null)
			link = li.selectFirst("a[href]");
		String name = (link != null ? link.text() : li.ownText()).trim();
		if (name.isEmpty())
			return null;

		String code = "";
		if (link != null) {
			try {
				URI href = URI.create(link.attr("abs:href").isEmpty() ? link.attr("href") : link.attr("abs:href"));
				String query = href.getQuery();
				if (query != null) {
					for (String part : query.split("&")) {
						String[] kv = part.split("=", 2);
						if (kv.length == 2 && kv[0].equals("st")) {
							code = kv[1];
							break;
						}
					}
				}
			} catch (Exception e) {
				// ignore URL parsing errors; we'll proceed without code
			}
		}

		try {
			final String finalCode = code;
			return collectibleTypeService.findByCode(code, collectibleTypeTreeParserLogger)
					.flatMap(executionResult -> {
						switch (executionResult.getStatus()) {
							case FOUND:
								return Mono.just(executionResult.getNode());
							default:
								return Mono.empty();
						}
					}).switchIfEmpty(Mono.defer(() -> {
						CollectibleType node = new CollectibleType();
						node.setCode(finalCode);
						node.setName(name);
						return collectibleTypeService.create(node, colligendisUserService.getNumistaParserUserMono(),
								collectibleTypeTreeParserLogger)
								.flatMap(er -> {
									switch (er.getStatus()) {
										case WAS_CREATED:
											return Mono.just(er.getNode());
										default:
											return Mono.empty();
									}
								});
					}));

		} catch (Exception e) {
			log.error("Error saving CollectibleType '{}': {}", name, e.getMessage());
		}
		return null;
	}
}
