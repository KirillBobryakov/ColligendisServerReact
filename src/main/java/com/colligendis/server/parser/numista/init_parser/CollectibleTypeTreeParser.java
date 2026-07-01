package com.colligendis.server.parser.numista.init_parser;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.service.CollectibleTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.dto.CollectibleTypeParseSummaryResponse;
import com.colligendis.server.dto.CollectibleTypeParseSummaryResponse.CollectibleTypeSummaryItem;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.logger.LogExecutionTime;
import com.colligendis.server.util.web.WebPageClient;
import com.colligendis.server.util.web.WebPageLoadException;

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

	private final WebPageClient webPageClient;

	private final BaseLogger collectibleTypeTreeParserLogger = new BaseLogger();

	private final List<CollectibleTypeSummaryItem> parsedTypes = new ArrayList<>();
	private int createdCount;
	private int updatedCount;
	private ColligendisUser actingUser;

	@LogExecutionTime
	public CollectibleTypeParseSummaryResponse parseAndSave(ColligendisUser actingUser) {
		this.actingUser = actingUser;
		this.parsedTypes.clear();
		this.createdCount = 0;
		this.updatedCount = 0;

		Document doc = loadDocument();
		if (doc == null) {
			log.error("Failed to load Numista types page: {}", TYPES_URL);
			return null;
		}

		Element rootUl = doc.selectFirst("ul#types_list");

		if (rootUl == null) {
			log.error("Could not find root <ul id=types_list> on types page");
			return null;
		}

		Elements topLis = rootUl.children();
		for (Element li : topLis) {
			if (!li.tagName().equals("li"))
				continue;
			processLi(li, null);
		}

		return new CollectibleTypeParseSummaryResponse(
				parsedTypes.size(),
				createdCount,
				updatedCount,
				List.copyOf(parsedTypes));
	}

	private Document loadDocument() {
		return loadPage(TYPES_URL);
	}

	private Document loadPage(String url) {
		try {
			String cookie = colligendisUserService.getNumistaParserUserMono()
					.map(this::resolveCookie)
					.defaultIfEmpty("")
					.block();
			return webPageClient.loadPageDocument(url, cookie).block();
		} catch (WebPageLoadException e) {
			log.error("Error loading page: {}", url, e);
			return null;
		}
	}

	private String resolveCookie(ColligendisUser user) {
		if (user != null && StringUtils.hasText(user.getNumistaCookie())) {
			return user.getNumistaCookie().strip();
		}
		return "";
	}

	private void processLi(Element li, CollectibleType parent) {
		UpsertOutcome outcome = upsertTypeFromLi(li).block();
		if (outcome == null || outcome.type() == null)
			return;

		CollectibleType current = outcome.type();
		parsedTypes.add(new CollectibleTypeSummaryItem(
				current.getCode(),
				current.getName(),
				current.getCountNTypesOnNumista(),
				parent != null ? parent.getCode() : null));
		if (outcome.created()) {
			createdCount++;
		} else {
			updatedCount++;
		}

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

	private Mono<UpsertOutcome> upsertTypeFromLi(Element li) {
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
			return Mono.empty();

		String code = extractCode(link);
		if (!StringUtils.hasText(code)) {
			log.warn("Skipping collectible type without code: {}", name);
			return Mono.empty();
		}

		final int countNTypesOnNumista = parseCountFromLi(li);
		final String finalCode = code;
		final Mono<ColligendisUser> userMono = Mono.just(actingUser);

		return collectibleTypeService.findByCode(finalCode, collectibleTypeTreeParserLogger)
				.flatMap(executionResult -> {
					if (executionResult.getStatus() == FindExecutionStatus.FOUND && executionResult.getNode() != null) {
						CollectibleType existing = executionResult.getNode();
						CollectibleType updatePayload = new CollectibleType();
						updatePayload.setUuid(existing.getUuid());
						updatePayload.setName(name);
						updatePayload.setCountNTypesOnNumista(countNTypesOnNumista);
						return collectibleTypeService.update(updatePayload, userMono, collectibleTypeTreeParserLogger)
								.flatMap(updateResult -> {
									if (updateResult.getStatus() != UpdateExecutionStatus.WAS_UPDATED
											&& updateResult.getStatus() != UpdateExecutionStatus.NOTHING_TO_UPDATE) {
										log.warn("Failed to update CollectibleType code={} status={}", finalCode,
												updateResult.getStatus());
										return Mono.empty();
									}
									CollectibleType updated = updateResult.getNode() != null
											? updateResult.getNode()
											: existing;
									updated.setCode(finalCode);
									updated.setName(name);
									updated.setCountNTypesOnNumista(countNTypesOnNumista);
									return Mono.just(new UpsertOutcome(updated, false));
								});
					}

					CollectibleType node = new CollectibleType();
					node.setCode(finalCode);
					node.setName(name);
					node.setCountNTypesOnNumista(countNTypesOnNumista);
					return collectibleTypeService.create(node, userMono, collectibleTypeTreeParserLogger)
							.flatMap(er -> {
								if (er.getStatus() != CreateNodeExecutionStatus.WAS_CREATED || er.getNode() == null) {
									log.warn("Failed to create CollectibleType code={} status={}", finalCode,
											er.getStatus());
									return Mono.empty();
								}
								return Mono.just(new UpsertOutcome(er.getNode(), true));
							});
				})
				.onErrorResume(e -> {
					log.error("Error saving CollectibleType '{}': {}", name, e.getMessage());
					return Mono.empty();
				});
	}

	private static String extractCode(Element link) {
		if (link == null) {
			return "";
		}
		try {
			URI href = URI.create(link.attr("abs:href").isEmpty() ? link.attr("href") : link.attr("abs:href"));
			String query = href.getQuery();
			if (query == null) {
				return "";
			}
			for (String part : query.split("&")) {
				String[] kv = part.split("=", 2);
				if (kv.length == 2 && kv[0].equals("st")) {
					return kv[1];
				}
			}
		} catch (Exception e) {
			// ignore URL parsing errors
		}
		return "";
	}

	private static int parseCountFromLi(Element li) {
		for (Element child : li.children()) {
			if (!"span".equals(child.tagName())) {
				continue;
			}
			String text = child.text().trim();
			if (!text.startsWith("(") || !text.endsWith(")")) {
				continue;
			}
			String inner = text.substring(1, text.length() - 1).replaceAll("[\\s\\u00a0\\u202f,]", "");
			if (inner.isEmpty()) {
				return 0;
			}
			try {
				return Integer.parseInt(inner);
			} catch (NumberFormatException e) {
				log.warn("Could not parse ntype count from span text: {}", text);
				return 0;
			}
		}
		return 0;
	}

	private record UpsertOutcome(CollectibleType type, boolean created) {
	}
}
