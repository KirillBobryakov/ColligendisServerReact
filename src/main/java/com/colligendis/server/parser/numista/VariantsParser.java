package com.colligendis.server.parser.numista;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.database.common.service.CalendarService;
import com.colligendis.server.database.common.service.YearService;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.numista.model.Mark;
import com.colligendis.server.database.numista.model.Signature;
import com.colligendis.server.database.numista.model.Variant;
import com.colligendis.server.database.numista.service.CatalogueReferenceService;
import com.colligendis.server.database.numista.service.CatalogueService;
import com.colligendis.server.database.numista.service.IssuingEntityService;
import com.colligendis.server.database.numista.service.MarkService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.SignatureService;
import com.colligendis.server.database.numista.service.VariantService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.database.result.UpdateExecutionStatus;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;
import com.colligendis.server.util.web.WebPageClient;
import com.colligendis.server.util.web.WebPageLoadException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class VariantsParser extends Parser {

	private static final PauseLock PAUSE_LOCK = new PauseLock("VariantsParser");
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern DIGITS = Pattern.compile("\\d+");
	private static final Path SIGNATURE_PICTURES_STORAGE_ROOT = Path
			.of("/Users/kirillbobryakov/Coins/Numista/storage/signatures");
	private static final Path MARK_PICTURES_STORAGE_ROOT = Path
			.of("/Users/kirillbobryakov/Coins/Numista/storage/marks");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	private static final String ISSUING_ENTITIES_URL_PREFIX = "https://en.numista.com/catalogue/get_issuing_entities.php?prefill=&country=";
	private static final String SEARCH_SIGNATURES_URL = "https://en.numista.com/catalogue/search_signatures.php?ie=%s&sie=&_type=query&term=&q=";

	private final VariantService variantService;
	private final NTypeService nTypeService;
	private final MarkService markService;
	private final CatalogueService catalogueService;
	private final CatalogueReferenceService catalogueReferenceService;
	private final SignatureService signatureService;
	private final IssuingEntityService issuingEntityService;
	private final CalendarService calendarService;
	private final YearService yearService;
	private final WebPageClient webPageClient;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.<NumistaPage>defer(() -> {
			Element annees = resolveAnneesFieldset(numistaPage.page);
			List<Element> variantRows = variantRowsInAnnees(annees);
			if (!variantRows.isEmpty()) {
				numistaPage.getPipelineStepLogger().info("VariantsParser: {} variant row(s) in #annees for page nid {}",
						variantRows.size(), numistaPage.nid);

				return Flux.<Element>fromIterable(variantRows)
						.concatMap(row -> resolveVariantForRow(row, numistaPage)
								.flatMap(variant -> persistVariantFromRowScope(row, variant, numistaPage)))
						.collectList()
						.flatMap(variants -> {
							if (variants.isEmpty()) {
								return Mono.just(numistaPage);
							}
							return nTypeService
									.setVariants(numistaPage.nType, variants,
											numistaPage.getNumistaParserUserMono(),
											numistaPage.getPipelineStepLogger())
									.flatMap(er -> {
										if (er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
												|| er.getStatus()
														.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
											return Mono.just(numistaPage);
										}
										return Mono.error(new ParserException(
												"Failed to set variants on NType: " + er.getStatus()));
									});
						});
			}

			Element root = varietiesRoot(numistaPage.page);
			Element scope = root != null ? root : numistaPage.page;
			String variantNid = extractVariantNid(scope);
			if (StringUtils.isBlank(variantNid)) {
				numistaPage.getPipelineStepLogger().info("VariantsParser: no variety checkbox nid for page nid {}",
						numistaPage.nid);
				return Mono.just(numistaPage);
			}
			return Mono.just(numistaPage);

		});
	}

	private Element resolveAnneesFieldset(Document page) {
		Element varieties = varietiesRoot(page);
		if (varieties != null) {
			Element inner = varieties.selectFirst("fieldset#annees");
			if (inner != null) {
				return inner;
			}
		}
		return page.selectFirst("fieldset#annees");
	}

	private List<Element> variantRowsInAnnees(Element annees) {
		if (annees == null) {
			return List.of();
		}
		Elements trs = annees.select("table > tbody > tr");
		if (trs.isEmpty()) {
			trs = annees.select("tbody tr");
		}
		if (trs.isEmpty()) {
			trs = annees.select("tr");
		}
		List<Element> out = new ArrayList<>();
		for (Element tr : trs) {
			if (StringUtils.isNotBlank(extractVariantNid(tr))) {
				out.add(tr);
			}
		}
		return out;
	}

	private Mono<Variant> resolveVariantForRow(Element row, NumistaPage numistaPage) {
		String variantNid = extractVariantNid(row);
		if (StringUtils.isBlank(variantNid)) {
			return Mono.empty();
		}
		return variantService.findByNid(variantNid, numistaPage.getPipelineStepLogger()).flatMap(er -> {
			switch (er.getStatus()) {
				case FOUND:
					return Mono.just(er.getNode());
				case NOT_FOUND:
					return variantService
							.create(new Variant(variantNid), numistaPage.getNumistaParserUserMono(),
									numistaPage.getPipelineStepLogger())
							.flatMap(cr -> {
								if (cr.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
									return Mono.just(cr.getNode());
								}
								return Mono.error(new ParserException("Failed to create Variant: " + cr.getStatus()));
							});
				default:
					numistaPage.getPipelineStepLogger().error("Failed to find Variant: {}", er.getStatus());
					return Mono.error(new ParserException("Failed to find Variant: " + er.getStatus()));
			}
		});
	}

	private Mono<Variant> persistVariantFromRowScope(Element rowScope, Variant variant, NumistaPage numistaPage) {

		Variant updatedVariant = updateVariantFromDom(rowScope, variant, numistaPage);

		return variantService
				.update(updatedVariant, numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
				.flatMap(er -> {
					switch (er.getStatus()) {
						case WAS_UPDATED, NOTHING_TO_UPDATE, NOT_FOUND:
							return Mono.just(er.getNode());
						default:
							numistaPage.getPipelineStepLogger().error("Failed to update Variant: {}", er.getStatus());
							er.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(new ParserException("Failed to update Variant: " + er.getStatus()));
					}
				})
				.flatMap(updated -> resolveAndLinkCalendarAndYears(updated, rowScope, numistaPage))
				.flatMap(updated -> resolveAndLinkSignatures(updated, rowScope, numistaPage))
				.flatMap(updated -> resolveAndLinkMarks(updated, rowScope, numistaPage))
				.flatMap(updated -> resolveAndLinkCatalogueReferences(updated, rowScope, numistaPage));
	}

	private Element varietiesRoot(Document page) {
		Element fs = page.selectFirst("fieldset:contains(Varieties)");
		if (fs == null) {
			fs = page.selectFirst("fieldset:contains(Variety)");
		}
		return fs;
	}

	private String extractVariantNid(Element scope) {
		for (Element cb : scope.select("input[type=checkbox]")) {
			String name = cb.attr("name");
			if (!StringUtils.startsWithIgnoreCase(name, "nd")) {
				continue;
			}
			Matcher m = DIGITS.matcher(name);
			if (m.find()) {
				return m.group();
			}
		}
		Element nd = scope.selectFirst("input[type=checkbox][name=nd]");
		if (nd != null) {
			Matcher m = DIGITS.matcher(nd.attr("value"));
			if (m.find()) {
				return m.group();
			}
		}
		return null;
	}

	private Variant updateVariantFromDom(Element root, Variant variant, NumistaPage numistaPage) {

		boolean dateCheckSelected = isDateCheckSelected(root);
		variant.setDated(!dateCheckSelected);

		variant.setDateMonth(parseIntFirst(root.select("input[name^=month]")));
		variant.setDateDay(parseIntFirst(root.select("input[name^=day]")));

		Integer mintage = parseIntFirst(root.select("td.date_mintage input[name^=tirage]"), null);
		variant.setMintage(mintage != null ? mintage : 0);

		String comment = firstInputValue(root, "input[name^=commentaire]");
		variant.setComment(comment);

		variant.setMintLetter(firstInputValue(root, "input[name^=atelier]"));

		return variant;
	}

	private Mono<Variant> resolveAndLinkCalendarAndYears(Variant variant, Element rowScope, NumistaPage numistaPage) {
		Element anneesScope = resolveAnneesScope(rowScope, numistaPage);
		String calendarCode = parseSelectedCalendarCode(anneesScope);
		Integer datedAtYear = parseIntFirst(rowScope.select("input[name^=millesime]"));
		Element datesRow = rowScope.parent().selectFirst("tr[id^=dates]");
		Integer fromGregorianYear = datesRow != null
				? parseIntFirst(datesRow.select("input[name^=dated]"))
				: null;
		Integer tillGregorianYear = datesRow != null
				? parseIntFirst(datesRow.select("input[name^=datef]"))
				: null;

		Mono<ColligendisUser> userMono = numistaPage.getNumistaParserUserMono();
		BaseLogger logger = numistaPage.getPipelineStepLogger();

		Mono<Calendar> calendarMono = calendarService.findByCode(calendarCode, logger)
				.flatMap(er -> {
					if (er.getStatus().equals(FindExecutionStatus.FOUND)) {
						return Mono.just(er.getNode());
					}
					logger.warning("VariantsParser: calendar code {} not in database, using gregorian", calendarCode);
					return CalendarService.GREGORIAN;
				});

		return calendarMono
				.flatMap(calendar -> variantService.setCalendar(variant, calendar, userMono, logger)
						.flatMap(calendarEr -> {
							if (!calendarEr.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
									&& !calendarEr.getStatus()
											.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
								return Mono.error(new ParserException(
										"Failed to set calendar on Variant: " + calendarEr.getStatus()));
							}
							return linkDatedAtYear(variant, datedAtYear, calendar, userMono, logger);
						}))
				.flatMap(v -> linkGregorianDatedFromYear(v, fromGregorianYear, userMono, logger))
				.flatMap(v -> linkGregorianDatedTillYear(v, tillGregorianYear, userMono, logger));
	}

	private Mono<Variant> linkDatedAtYear(Variant variant, Integer dateYear, Calendar calendar,
			Mono<ColligendisUser> userMono, BaseLogger logger) {
		if (dateYear == null) {
			return variantService.setDatedAt(variant, null, userMono, logger).thenReturn(variant);
		}
		return yearService.findYearByDateYearWithCreate(dateYear, Mono.just(calendar), userMono)
				.flatMap(year -> variantService.setDatedAt(variant, year, userMono, logger)
						.flatMap(er -> {
							if (!er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
									&& !er.getStatus()
											.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
								return Mono.error(
										new ParserException("Failed to set DATED_AT on Variant: " + er.getStatus()));
							}
							return Mono.just(variant);
						}));
	}

	private Mono<Variant> linkGregorianDatedFromYear(Variant variant, Integer gregorianYear,
			Mono<ColligendisUser> userMono, BaseLogger logger) {
		if (gregorianYear == null) {
			return variantService.setDatedFrom(variant, null, userMono, logger).thenReturn(variant);
		}
		return yearService.findYearByDateYearWithCreate(gregorianYear, CalendarService.GREGORIAN, userMono)
				.flatMap(year -> variantService.setDatedFrom(variant, year, userMono, logger)
						.flatMap(er -> {
							if (!er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
									&& !er.getStatus()
											.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
								return Mono.error(
										new ParserException("Failed to set DATED_FROM on Variant: " + er.getStatus()));
							}
							return Mono.just(variant);
						}));
	}

	private Mono<Variant> linkGregorianDatedTillYear(Variant variant, Integer gregorianYear,
			Mono<ColligendisUser> userMono, BaseLogger logger) {
		if (gregorianYear == null) {
			return variantService.setDatedTill(variant, null, userMono, logger).thenReturn(variant);
		}
		return yearService.findYearByDateYearWithCreate(gregorianYear, CalendarService.GREGORIAN, userMono)
				.flatMap(year -> variantService.setDatedTill(variant, year, userMono, logger)
						.flatMap(er -> {
							if (!er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
									&& !er.getStatus()
											.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
								return Mono.error(
										new ParserException("Failed to set DATED_TILL on Variant: " + er.getStatus()));
							}
							return Mono.just(variant);
						}));
	}

	private Element resolveAnneesScope(Element rowScope, NumistaPage numistaPage) {
		Element annees = rowScope.closest("fieldset#annees");
		if (annees != null) {
			return annees;
		}
		return resolveAnneesFieldset(numistaPage.page);
	}

	private static String parseSelectedCalendarCode(Element anneesScope) {
		if (anneesScope == null) {
			return Calendar.GREGORIAN_CODE;
		}
		Element select = anneesScope.selectFirst("select#calendrier");
		if (select == null) {
			return Calendar.GREGORIAN_CODE;
		}
		Element selected = select.select("option[selected]").stream().findFirst().orElse(null);
		if (selected == null) {
			selected = select.selectFirst("option");
		}
		if (selected == null || StringUtils.isBlank(selected.attr("value"))) {
			return Calendar.GREGORIAN_CODE;
		}
		return selected.attr("value").trim();
	}

	private boolean isDateCheckSelected(Element root) {
		Element td = root.selectFirst("td.date_check");
		if (td == null) {
			return false;
		}
		if (td.hasClass("selected")) {
			return true;
		}
		Element cb = td.selectFirst("input[type=checkbox]");
		return cb != null && (cb.hasAttr("checked") || "checked".equalsIgnoreCase(cb.attr("checked")));
	}

	private static Integer parseIntFirst(Elements inputs) {
		return parseIntFirst(inputs, null);
	}

	private static Integer parseIntFirst(Elements inputs, Integer defaultIfBlank) {
		for (Element in : inputs) {
			String raw = NumistaParseUtils.getAttribute(in, "value");
			if (StringUtils.isBlank(raw)) {
				continue;
			}
			try {
				return Integer.parseInt(raw.replaceAll("\\s", ""));
			} catch (NumberFormatException ignored) {
				return defaultIfBlank;
			}
		}
		return defaultIfBlank;
	}

	private static String firstInputValue(Element root, String query) {
		Element el = root.selectFirst(query);
		if (el == null) {
			return null;
		}
		return NumistaParseUtils.getAttribute(el, "value");
	}

	private Mono<Variant> resolveAndLinkSignatures(Variant variant, Element scope, NumistaPage numistaPage) {
		Set<String> nids = new LinkedHashSet<>();
		for (Element sel : scope.select("select[name^=signatures]")) {
			for (Element opt : sel.select("option[selected]")) {
				String val = opt.attr("value");
				if (StringUtils.isNotBlank(val)) {
					nids.add(val.trim());
				}
			}
		}
		if (nids.isEmpty()) {
			return variantService.setSignatures(variant, List.of(), numistaPage.getNumistaParserUserMono(),
					numistaPage.getPipelineStepLogger()).thenReturn(variant);
		}

		return Flux.fromIterable(nids)
				.flatMap(nid -> signatureService.findByNid(nid, numistaPage.getPipelineStepLogger()))
				.collectList()
				.flatMap(results -> {
					boolean missing = results.stream()
							.anyMatch(er -> !er.getStatus().equals(FindExecutionStatus.FOUND));
					Mono<Void> sync = missing ? ensureSignaturesFromPhp(numistaPage) : Mono.empty();
					return sync.thenMany(Flux.fromIterable(nids)
							.concatMap(nid -> signatureService.findByNid(nid, numistaPage.getPipelineStepLogger())
									.flatMap(er -> {
										if (er.getStatus().equals(FindExecutionStatus.FOUND)) {
											return ensureSignaturePictureLocal(er.getNode(), null, numistaPage);
										}
										numistaPage.getPipelineStepLogger()
												.warning("VariantsParser: signature nid {} not in database", nid);
										return Mono.<Signature>empty();
									})))
							.collectList();
				})
				.flatMap(sigs -> variantService.setSignatures(variant, sigs, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
						.flatMap(er -> {
							if (!er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
									&& !er.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
								return Mono.error(new ParserException(
										"Failed to set signatures on Variant: " + er.getStatus()));
							}
							return Mono.just(variant);
						}));
	}

	private Mono<Void> ensureSignaturesFromPhp(NumistaPage numistaPage) {
		Issuer issuer = numistaPage.getIssuer();
		if (issuer == null || StringUtils.isBlank(issuer.getNumistaCode())) {
			numistaPage.getPipelineStepLogger().warning("VariantsParser: skip PHP signature sync — no issuer");
			return Mono.empty();
		}
		return PAUSE_LOCK.runExclusiveOrElse(
				() -> syncIssuingEntitiesAndSignatures(issuer, numistaPage)
						.subscribeOn(Schedulers.boundedElastic()),
				() -> ensureSignaturesFromPhp(numistaPage));
	}

	private Mono<Void> syncIssuingEntitiesAndSignatures(Issuer issuer, NumistaPage numistaPage) {
		String url = ISSUING_ENTITIES_URL_PREFIX + issuer.getNumistaCode();
		return loadHtmlPage(url, numistaPage)
				.flatMap(doc -> Flux.fromIterable(doc.select("option[value]"))
						.filter(opt -> StringUtils.isNotBlank(opt.attr("value")))
						.concatMap(opt -> issuingEntityService.findByNidWithCreate(opt.attr("value"), opt.text(),
								numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
								.flatMap(er -> {
									if (er.getStatus().equals(FindExecutionStatus.FOUND)
											|| er.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
										return Mono.just(er.getNode());
									}
									return Mono.<IssuingEntity>empty();
								}))
						.collectList()
						.flatMap(entities -> {
							if (entities.isEmpty()) {
								return Mono.empty();
							}
							return issuingEntityService.setIssuer(issuer, entities,
									numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
									.flatMap(setI -> {
										if (!setI.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
												&& !setI.getStatus()
														.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
											numistaPage.getPipelineStepLogger().error(
													"VariantsParser: setIssuer status {}", setI.getStatus());
										}
										return nTypeService.setIssuingEntities(numistaPage.nType, entities,
												numistaPage.getNumistaParserUserMono(),
												numistaPage.getPipelineStepLogger());
									})
									.flatMap(setN -> {
										if (!setN.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
												&& !setN.getStatus()
														.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
											numistaPage.getPipelineStepLogger().error(
													"VariantsParser: setIssuingEntities status {}",
													setN.getStatus());
										}
										return Flux.fromIterable(entities)
												.concatMap(ie -> loadSignaturesForIssuingEntity(ie, numistaPage))
												.then();
									});
						}));
	}

	private Mono<Void> loadSignaturesForIssuingEntity(IssuingEntity ie, NumistaPage numistaPage) {
		String url = String.format(SEARCH_SIGNATURES_URL, ie.getNid());
		return loadJsonPage(url, numistaPage)
				.flatMap(json -> {
					List<JsonNode> nodes;
					try {
						nodes = parseSignatureJson(json);
					} catch (Exception e) {
						numistaPage.getPipelineStepLogger().error("VariantsParser: bad JSON from {}", url);
						return Mono.empty();
					}
					return Flux.fromIterable(nodes)
							.concatMap(node -> upsertSignatureFromJson(node, numistaPage))
							.then();
				});
	}

	private Mono<Document> loadHtmlPage(String url, NumistaPage numistaPage) {
		return numistaPage.getNumistaParserUserMono()
				.map(VariantsParser::resolveCookie)
				.defaultIfEmpty("")
				.flatMap(cookie -> webPageClient.loadPageDocument(url, cookie))
				.onErrorResume(WebPageLoadException.class, e -> {
					numistaPage.getPipelineStepLogger().error("VariantsParser: can't load {}: {}", url, e.getMessage());
					return Mono.empty();
				});
	}

	private Mono<String> loadJsonPage(String url, NumistaPage numistaPage) {
		return numistaPage.getNumistaParserUserMono()
				.map(VariantsParser::resolveCookie)
				.defaultIfEmpty("")
				.flatMap(cookie -> webPageClient.loadJson(url, cookie))
				.onErrorResume(WebPageLoadException.class, e -> {
					numistaPage.getPipelineStepLogger().error("VariantsParser: can't load {}: {}", url, e.getMessage());
					return Mono.empty();
				});
	}

	private static String resolveCookie(ColligendisUser user) {
		if (user != null && StringUtils.isNotBlank(user.getNumistaCookie())) {
			return user.getNumistaCookie().strip();
		}
		return "";
	}

	private List<JsonNode> parseSignatureJson(String body) throws Exception {
		JsonNode root = JSON.readTree(body.trim());
		List<JsonNode> out = new ArrayList<>();
		if (root.isArray()) {
			for (JsonNode n : root) {
				out.add(n);
			}
		}
		return out;
	}

	private Mono<Void> upsertSignatureFromJson(JsonNode node, NumistaPage numistaPage) {
		if (!node.has("id")) {
			return Mono.empty();
		}
		String nid = String.valueOf(node.get("id").asInt());
		String imageUrl = node.path("image").asText("");
		return signatureService.findByNid(nid, numistaPage.getPipelineStepLogger())
				.flatMap(er -> {
					if (er.getStatus().equals(FindExecutionStatus.FOUND)) {
						return ensureSignaturePictureLocal(er.getNode(), imageUrl, numistaPage).then();
					}
					Signature s = new Signature();
					s.setNid(nid);
					s.setName(node.path("text").asText(""));
					s.setPictureUrl(NumistaCatalogueImageUrls.toStoredPicturePath(imageUrl));
					return Mono.fromCallable(() -> resolveOrDownloadSignaturePicture(s, imageUrl, numistaPage))
							.subscribeOn(Schedulers.boundedElastic())
							.flatMap(signature -> signatureService
									.create(signature, numistaPage.getNumistaParserUserMono(),
											numistaPage.getPipelineStepLogger())
									.flatMap(cr -> Mono.<Void>empty()));
				});
	}

	private Mono<Signature> ensureSignaturePictureLocal(Signature signature, String pictureUrl,
			NumistaPage numistaPage) {
		String previousLocalPath = signature.getPictureLocalPath();
		return Mono.fromCallable(() -> resolveOrDownloadSignaturePicture(signature, pictureUrl, numistaPage))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(updated -> {
					if (Objects.equals(previousLocalPath, updated.getPictureLocalPath())) {
						return Mono.just(updated);
					}
					return signatureService
							.update(updated, numistaPage.getNumistaParserUserMono(),
									numistaPage.getPipelineStepLogger())
							.flatMap(er -> {
								if (er.getStatus().equals(UpdateExecutionStatus.WAS_UPDATED)
										|| er.getStatus().equals(UpdateExecutionStatus.NOTHING_TO_UPDATE)) {
									return Mono.just(er.getNode());
								}
								numistaPage.getPipelineStepLogger().warning(
										"VariantsParser: failed to update signature pictureLocalPath nid={}, status={}",
										updated.getNid(), er.getStatus());
								return Mono.just(updated);
							});
				});
	}

	private Signature resolveOrDownloadSignaturePicture(Signature signature, String pictureUrl,
			NumistaPage numistaPage) {
		String rawUrl = StringUtils.isNotBlank(pictureUrl) ? pictureUrl : signature.getPictureUrl();
		if (StringUtils.isBlank(rawUrl)) {
			return signature;
		}
		String storedPath = NumistaCatalogueImageUrls.toStoredPicturePath(rawUrl);
		Path existingLocalPath = normalizeLocalPath(signature.getPictureLocalPath());
		if (existingLocalPath != null && Files.exists(existingLocalPath) && Files.isRegularFile(existingLocalPath)) {
			return signature;
		}
		String absoluteUrl = NumistaCatalogueImageUrls.toAbsolutePictureUrl(storedPath);
		if (StringUtils.isBlank(absoluteUrl)) {
			return signature;
		}
		try {
			Files.createDirectories(SIGNATURE_PICTURES_STORAGE_ROOT);
			String fileName = "signature_" + signature.getNid() + extensionFromUrl(absoluteUrl);
			Path targetPath = SIGNATURE_PICTURES_STORAGE_ROOT.resolve(fileName).normalize();

			HttpRequest request = HttpRequest.newBuilder(URI.create(absoluteUrl)).GET().build();
			HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null
					&& response.body().length > 0) {
				Files.write(targetPath, response.body());
				signature.setPictureUrl(storedPath);
				signature.setPictureLocalPath(targetPath.toString());
				numistaPage.getPipelineStepLogger().debugGreen("Signature picture downloaded to: {}", targetPath);
			} else {
				numistaPage.getPipelineStepLogger().warning(
						"VariantsParser: failed to download signature picture nid={}, status={}, url={}",
						signature.getNid(), response.statusCode(), absoluteUrl);
			}
		} catch (IOException | InterruptedException | IllegalArgumentException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			numistaPage.getPipelineStepLogger().warning(
					"VariantsParser: failed to store signature picture locally nid={}, url={}",
					signature.getNid(), absoluteUrl, exception);
		}
		return signature;
	}

	private static Path normalizeLocalPath(String rawPath) {
		if (StringUtils.isBlank(rawPath)) {
			return null;
		}
		String value = rawPath.trim();
		if (value.startsWith("../") || value.startsWith("http://") || value.startsWith("https://")) {
			return null;
		}
		if (value.startsWith("file://")) {
			value = value.substring("file://".length());
		}
		return Path.of(value).normalize();
	}

	private static String extensionFromUrl(String url) {
		int query = url.indexOf('?');
		String path = query >= 0 ? url.substring(0, query) : url;
		int dot = path.lastIndexOf('.');
		if (dot < 0 || dot == path.length() - 1) {
			return ".jpg";
		}
		String ext = path.substring(dot).toLowerCase();
		if (ext.matches("\\.(jpg|jpeg|png|gif|webp|svg)")) {
			return ext;
		}
		return ".jpg";
	}

	private Mono<Variant> resolveAndLinkMarks(Variant variant, Element scope, NumistaPage numistaPage) {
		Element spanMarkContainer = scope.selectFirst("span.mark_container");

		Element dateMarkTd = scope.selectFirst("td.date_mark");
		List<ParsedMarkRow> rows = dateMarkTd == null ? List.of() : parseMarkRowsFromDateMark(dateMarkTd);
		if (rows.isEmpty()) {
			return variantService.setMarks(variant, List.of(), numistaPage.getNumistaParserUserMono(),
					numistaPage.getPipelineStepLogger()).thenReturn(variant);
		}
		return Flux.fromIterable(rows)
				.concatMap(row -> resolveOrCreateMark(row, numistaPage))
				.collectList()
				.flatMap(list -> variantService.setMarks(variant, list, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
						.flatMap(er -> {
							if (!er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
									&& !er.getStatus().equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
								return Mono.error(
										new ParserException("Failed to set marks on Variant: " + er.getStatus()));
							}
							return Mono.just(variant);
						}));
	}

	private static List<ParsedMarkRow> parseMarkRowsFromDateMark(Element dateMarkTd) {
		Element select = dateMarkTd.selectFirst("select[name^=marks]");
		List<String> nids = new ArrayList<>();
		if (select != null) {
			for (Element opt : select.select("option[selected]")) {
				String val = StringUtils.trimToNull(opt.attr("value"));
				if (val != null) {
					nids.add(val);
				}
			}
		}

		List<ParsedMarkRow> rows = new ArrayList<>();
		for (Element li : dateMarkTd.select("li.select2-selection__choice")) {
			Element img = li.selectFirst("span.mark_container img.mark, img.mark");
			if (img == null) {
				continue;
			}
			String src = NumistaParseUtils.getAttribute(img, "src");
			String code = codeFromImageFilename(src);
			String name = NumistaParseUtils.getAttribute(img, "alt");
			String description = NumistaParseUtils.getAttribute(img, "title");
			String nid = !nids.isEmpty() && rows.size() < nids.size() ? nids.get(rows.size()) : null;
			if (nid == null && select != null) {
				nid = findMarkNidByImageCode(select, code);
			}
			rows.add(new ParsedMarkRow(nid, code, name, description, src));
		}
		for (int i = rows.size(); i < nids.size(); i++) {
			rows.add(new ParsedMarkRow(nids.get(i), null, null, null, null));
		}
		return rows;
	}

	private static String findMarkNidByImageCode(Element select, String code) {
		if (select == null || StringUtils.isBlank(code)) {
			return null;
		}
		for (Element opt : select.select("option[value]")) {
			String display = opt.attr("data-display");
			if (StringUtils.isBlank(display)) {
				continue;
			}
			try {
				String decoded = new String(Base64.getDecoder().decode(display.strip()),
						StandardCharsets.UTF_8);
				if (decoded.contains(code)) {
					return StringUtils.trimToNull(opt.attr("value"));
				}
			} catch (IllegalArgumentException ignored) {
				// skip malformed data-display
			}
		}
		return null;
	}

	private Mono<Mark> resolveOrCreateMark(ParsedMarkRow row, NumistaPage numistaPage) {
		if (StringUtils.isBlank(row.nid()) && StringUtils.isBlank(row.code())) {
			return Mono.empty();
		}
		Mono<ExecutionResult<Mark, FindExecutionStatus>> lookup = StringUtils.isNotBlank(row.nid())
				? markService.findByNid(row.nid(), numistaPage.getPipelineStepLogger())
				: markService.findByCode(row.code(), numistaPage.getPipelineStepLogger());
		return lookup.flatMap(er -> {
			Mono<Mark> markMono;
			if (er.getStatus().equals(FindExecutionStatus.FOUND)) {
				markMono = syncMarkFromRow(er.getNode(), row, numistaPage);
			} else {
				Mark mark = new Mark();
				mark.setNid(row.nid());
				mark.setCode(row.code());
				mark.setName(row.name());
				mark.setDescription(row.description());
				mark.setPicture(row.picture());
				markMono = markService.create(mark, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger())
						.flatMap(cr -> {
							if (cr.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
								return Mono.just(cr.getNode());
							}
							return Mono.empty();
						});
			}
			return markMono.flatMap(mark -> ensureMarkPictureLocal(mark, row.picture(), numistaPage));
		});
	}

	private Mono<Mark> ensureMarkPictureLocal(Mark mark, String pictureUrl, NumistaPage numistaPage) {
		String previousLocalPath = mark.getPictureLocalPath();
		String previousPicture = mark.getPicture();
		return Mono.fromCallable(() -> resolveOrDownloadMarkPicture(mark, pictureUrl, numistaPage))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(updated -> {
					if (Objects.equals(previousLocalPath, updated.getPictureLocalPath())
							&& Objects.equals(
									NumistaCatalogueImageUrls.toStoredPicturePath(previousPicture),
									NumistaCatalogueImageUrls.toStoredPicturePath(updated.getPicture()))) {
						return Mono.just(updated);
					}
					return markService
							.update(updated, numistaPage.getNumistaParserUserMono(),
									numistaPage.getPipelineStepLogger())
							.flatMap(er -> {
								if (er.getStatus().equals(UpdateExecutionStatus.WAS_UPDATED)
										|| er.getStatus().equals(UpdateExecutionStatus.NOTHING_TO_UPDATE)) {
									return Mono.just(er.getNode());
								}
								numistaPage.getPipelineStepLogger().warning(
										"VariantsParser: failed to update Mark pictureLocalPath nid={}, status={}",
										updated.getNid(), er.getStatus());
								return Mono.just(updated);
							});
				});
	}

	private Mark resolveOrDownloadMarkPicture(Mark mark, String pictureUrl, NumistaPage numistaPage) {
		String rawUrl = StringUtils.isNotBlank(pictureUrl) ? pictureUrl : mark.getPicture();
		if (StringUtils.isBlank(rawUrl)) {
			return mark;
		}
		String storedPath = NumistaCatalogueImageUrls.toStoredPicturePath(rawUrl);
		String previousStoredPath = NumistaCatalogueImageUrls.toStoredPicturePath(mark.getPicture());
		Path existingLocalPath = normalizeLocalPath(mark.getPictureLocalPath());
		if (Objects.equals(storedPath, previousStoredPath) && existingLocalPath != null
				&& Files.exists(existingLocalPath) && Files.isRegularFile(existingLocalPath)) {
			return mark;
		}
		String absoluteUrl = NumistaCatalogueImageUrls.toAbsolutePictureUrl(storedPath);
		if (StringUtils.isBlank(absoluteUrl)) {
			return mark;
		}
		try {
			Files.createDirectories(MARK_PICTURES_STORAGE_ROOT);
			String fileKey = StringUtils.isNotBlank(mark.getNid()) ? mark.getNid() : mark.getCode();
			if (StringUtils.isBlank(fileKey)) {
				fileKey = codeFromImageFilename(rawUrl);
			}
			String fileName = "mark_" + fileKey + extensionFromUrl(absoluteUrl);
			Path targetPath = MARK_PICTURES_STORAGE_ROOT.resolve(fileName).normalize();

			HttpRequest request = HttpRequest.newBuilder(URI.create(absoluteUrl)).GET().build();
			HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null
					&& response.body().length > 0) {
				Files.write(targetPath, response.body());
				mark.setPicture(storedPath);
				mark.setPictureLocalPath(targetPath.toString());
				numistaPage.getPipelineStepLogger().debugGreen("Mark picture downloaded to: {}", targetPath);
			} else {
				numistaPage.getPipelineStepLogger().warning(
						"VariantsParser: failed to download mark picture nid={}, status={}, url={}",
						mark.getNid(), response.statusCode(), absoluteUrl);
			}
		} catch (IOException | InterruptedException | IllegalArgumentException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			numistaPage.getPipelineStepLogger().warning(
					"VariantsParser: failed to store mark picture locally nid={}, url={}",
					mark.getNid(), absoluteUrl, exception);
		}
		return mark;
	}

	private Mono<Mark> syncMarkFromRow(Mark mark, ParsedMarkRow row, NumistaPage numistaPage) {
		if (!applyRowToMark(mark, row)) {
			return Mono.just(mark);
		}
		return markService.update(mark, numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
				.flatMap(er -> {
					if (er.getStatus().equals(UpdateExecutionStatus.WAS_UPDATED)
							|| er.getStatus().equals(UpdateExecutionStatus.NOTHING_TO_UPDATE)) {
						return Mono.just(er.getNode());
					}
					numistaPage.getPipelineStepLogger().warning(
							"VariantsParser: failed to update Mark nid={}, status={}",
							mark.getNid(), er.getStatus());
					return Mono.just(mark);
				});
	}

	private static boolean applyRowToMark(Mark mark, ParsedMarkRow row) {
		boolean changed = false;
		if (StringUtils.isNotBlank(row.nid()) && !Objects.equals(mark.getNid(), row.nid())) {
			mark.setNid(row.nid());
			changed = true;
		}
		if (StringUtils.isNotBlank(row.code()) && !Objects.equals(mark.getCode(), row.code())) {
			mark.setCode(row.code());
			changed = true;
		}
		if (StringUtils.isNotBlank(row.name()) && !Objects.equals(mark.getName(), row.name())) {
			mark.setName(row.name());
			changed = true;
		}
		if (StringUtils.isNotBlank(row.description()) && !Objects.equals(mark.getDescription(), row.description())) {
			mark.setDescription(row.description());
			changed = true;
		}
		if (StringUtils.isNotBlank(row.picture()) && !Objects.equals(mark.getPicture(), row.picture())) {
			mark.setPicture(row.picture());
			changed = true;
		}
		return changed;
	}

	private record ParsedMarkRow(String nid, String code, String name, String description, String picture) {
	}

	private static String codeFromImageFilename(String src) {
		if (StringUtils.isBlank(src)) {
			return null;
		}
		int q = src.indexOf('?');
		String path = q > 0 ? src.substring(0, q) : src;
		int slash = path.lastIndexOf('/');
		String file = slash >= 0 ? path.substring(slash + 1) : path;
		int dot = file.lastIndexOf('.');
		return dot > 0 ? file.substring(0, dot) : file;
	}

	private Mono<Variant> resolveAndLinkCatalogueReferences(Variant variant, Element scope,
			NumistaPage numistaPage) {
		List<CatalogueRefRow> rows = List.of(
				new CatalogueRefRow("first_ref", "first_number"),
				new CatalogueRefRow("second_ref", "second_number"),
				new CatalogueRefRow("third_ref", "third_number"),
				new CatalogueRefRow("fourth_ref", "fourth_number"));
		return Flux.fromIterable(rows)
				.concatMap(row -> processCatalogueRow(scope, row, numistaPage))
				.filter(Objects::nonNull)
				.collectList()
				.flatMap(refs -> {
					if (refs.isEmpty()) {
						return variantService.setCatalogueReferences(variant, List.of(),
								numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
								.thenReturn(variant);
					}
					return variantService.setCatalogueReferences(variant, refs,
							numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
							.flatMap(er -> {
								if (!er.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
										&& !er.getStatus()
												.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
									return Mono.error(new ParserException(
											"Failed to set catalogue refs on Variant: " + er.getStatus()));
								}
								return Mono.just(variant);
							});
				});
	}

	private Mono<CatalogueReference> processCatalogueRow(Element scope, CatalogueRefRow row, NumistaPage numistaPage) {
		Element sel = scope.selectFirst("select[name^=\"" + row.refSelectName + "\"]");
		Element num = scope.selectFirst("input[name^=\"" + row.numberInputName + "\"]");
		if (sel == null || num == null) {
			return Mono.empty();
		}
		Element opt = sel.select("option[selected]").stream().findFirst().orElse(sel.selectFirst("option"));
		if (opt == null) {
			return Mono.empty();
		}
		String catalogueCode = opt.text();
		String numberVal = NumistaParseUtils.getAttribute(num, "value");
		if (StringUtils.isBlank(numberVal)) {
			return Mono.empty();
		}
		return catalogueReferenceService.findByNumberAndCatalogueCode(numberVal, catalogueCode,
				numistaPage.getPipelineStepLogger())
				.flatMap(crEr -> {
					if (crEr.getStatus().equals(FindExecutionStatus.NOT_FOUND)) {
						return catalogueService.findByCode(catalogueCode, numistaPage.getPipelineStepLogger())
								.flatMap(catEr -> {
									if (!catEr.getStatus().equals(FindExecutionStatus.FOUND)) {
										return Mono.<CatalogueReference>empty();
									}
									Catalogue cat = catEr.getNode();
									return catalogueReferenceService.create(numberVal, cat,
											numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
											.flatMap(createEr -> {
												if (createEr.getStatus()
														.equals(CreateNodeExecutionStatus.WAS_CREATED)) {
													return Mono.just((CatalogueReference) createEr.getNode());
												}
												return Mono.<CatalogueReference>empty();
											});
								});
					}
					if (crEr.getStatus().equals(FindExecutionStatus.FOUND)) {
						return Mono.just(crEr.getNode());
					}
					return Mono.<CatalogueReference>empty();
				});
	}

	private record CatalogueRefRow(String refSelectName, String numberInputName) {
	}
}
