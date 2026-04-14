package com.colligendis.server.parser.numista;

import java.util.ArrayList;
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

import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.model.IssuingEntity;
import com.colligendis.server.database.numista.model.Mark;
import com.colligendis.server.database.numista.model.Signature;
import com.colligendis.server.database.numista.model.SpecifiedMint;
import com.colligendis.server.database.numista.model.Variant;
import com.colligendis.server.database.numista.service.CatalogueReferenceService;
import com.colligendis.server.database.numista.service.CatalogueService;
import com.colligendis.server.database.numista.service.IssuingEntityService;
import com.colligendis.server.database.numista.service.MarkService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.SignatureService;
import com.colligendis.server.database.numista.service.SpecifiedMintService;
import com.colligendis.server.database.numista.service.VariantService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.PauseLock;
import com.colligendis.server.parser.numista.exception.ParserException;
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

	private static final String ISSUING_ENTITIES_URL_PREFIX = "https://en.numista.com/catalogue/get_issuing_entities.php?prefill=&country=";
	private static final String SEARCH_SIGNATURES_URL = "https://en.numista.com/catalogue/search_signatures.php?ie=%s&sie=&_type=query&term=&q=";

	private final VariantService variantService;
	private final NTypeService nTypeService;
	private final SpecifiedMintService specifiedMintService;
	private final MarkService markService;
	private final CatalogueService catalogueService;
	private final CatalogueReferenceService catalogueReferenceService;
	private final SignatureService signatureService;
	private final IssuingEntityService issuingEntityService;

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
			if (er.getStatus().equals(FindExecutionStatus.FOUND)) {
				numistaPage.getPipelineStepLogger().info("Variant found: {}", er.getNode());
				return Mono.just(er.getNode());
			}
			if (er.getStatus().equals(FindExecutionStatus.NOT_FOUND)) {
				numistaPage.getPipelineStepLogger().info("Variant not found: {}", variantNid);
				return variantService
						.create(new Variant(variantNid), numistaPage.getNumistaParserUserMono(),
								numistaPage.getPipelineStepLogger())
						.flatMap(cr -> {
							if (cr.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
								numistaPage.getPipelineStepLogger().info("Variant created: {}", cr.getNode());
								return Mono.just(cr.getNode());
							}
							numistaPage.getPipelineStepLogger().error("Failed to create Variant: {}", cr.getStatus());
							return Mono.error(new ParserException("Failed to create Variant: " + cr.getStatus()));
						});
			}
			numistaPage.getPipelineStepLogger().error("Failed to find Variant: {}", er.getStatus());
			return Mono.error(new ParserException("Failed to find Variant: " + er.getStatus()));
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
				.flatMap(updated -> resolveAndLinkSignatures(updated, rowScope, numistaPage))
				.flatMap(updated -> linkSpecifiedMint(updated, rowScope, numistaPage))
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

		variant.setDateGregorianYear(parseIntFirst(root.select("input[name^=millesime]")));
		variant.setDateMonth(parseIntFirst(root.select("input[name^=month]")));
		variant.setDateDay(parseIntFirst(root.select("input[name^=day]")));
		Element datesRow = root.selectFirst("tr[id^=dates]");
		if (datesRow != null) {
			variant.setFromGregorianYear(parseIntFirst(datesRow.select("input[name^=dated]")));
			variant.setTillGregorianYear(parseIntFirst(datesRow.select("input[name^=datef]")));
		}

		Integer mintage = parseIntFirst(root.select("td.date_mintage input[name^=tirage]"), null);
		variant.setMintage(mintage != null ? mintage : 0);

		String comment = firstInputValue(root, "input[name^=commentaire]");
		variant.setComment(comment);

		return variant;
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
											return Mono.just(er.getNode());
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
		return PAUSE_LOCK.awaitIfPaused()
				.then(Mono.defer(() -> {
					if (!PAUSE_LOCK.pause()) {
						return PAUSE_LOCK.awaitIfPaused().then(ensureSignaturesFromPhp(numistaPage));
					}
					return syncIssuingEntitiesAndSignatures(issuer, numistaPage)
							.subscribeOn(Schedulers.boundedElastic())
							.doFinally(signal -> PAUSE_LOCK.resume());
				}));
	}

	private Mono<Void> syncIssuingEntitiesAndSignatures(Issuer issuer, NumistaPage numistaPage) {
		String url = ISSUING_ENTITIES_URL_PREFIX + issuer.getNumistaCode();
		return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(doc -> {
					if (doc == null) {
						numistaPage.getPipelineStepLogger().error("VariantsParser: can't load {}", url);
						return Mono.empty();
					}
					return Flux.fromIterable(doc.select("option[value]"))
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
							});
				});
	}

	private Mono<Void> loadSignaturesForIssuingEntity(IssuingEntity ie, NumistaPage numistaPage) {
		String url = String.format(SEARCH_SIGNATURES_URL, ie.getNid());
		return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(doc -> {
					if (doc == null) {
						return Mono.empty();
					}
					List<JsonNode> nodes;
					try {
						nodes = parseSignatureJson(doc.body().text());
					} catch (Exception e) {
						numistaPage.getPipelineStepLogger().error("VariantsParser: bad JSON from {}", url);
						return Mono.empty();
					}
					return Flux.fromIterable(nodes)
							.concatMap(node -> upsertSignatureFromJson(node, numistaPage))
							.then();
				});
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
		return signatureService.findByNid(nid, numistaPage.getPipelineStepLogger())
				.flatMap(er -> {
					if (er.getStatus().equals(FindExecutionStatus.FOUND)) {
						return Mono.<Void>empty();
					}
					Signature s = new Signature();
					s.setNid(nid);
					s.setName(node.path("text").asText(""));
					s.setPictureUrl(node.path("image").asText(""));
					return signatureService.create(s, numistaPage.getNumistaParserUserMono(),
							numistaPage.getPipelineStepLogger())
							.flatMap(cr -> Mono.<Void>empty());
				});
	}

	private Mono<Variant> linkSpecifiedMint(Variant variant, Element scope, NumistaPage numistaPage) {
		Element atelier = scope.selectFirst("input[name^=atelier]");
		String rowId = atelier == null ? null
				: StringUtils.trimToNull(NumistaParseUtils.getAttribute(atelier, "value"));
		if (rowId == null) {
			return Mono.just(variant);
		}
		return specifiedMintService.findByIdentifierLinkedToNType(rowId, numistaPage.nType.getUuid(),
				numistaPage.getPipelineStepLogger())
				.flatMap(er -> {
					if (!er.getStatus().equals(FindExecutionStatus.FOUND)) {
						numistaPage.getPipelineStepLogger().warning(
								"VariantsParser: SpecifiedMint not found for identifier {} on nid {}", rowId,
								numistaPage.nid);
						return Mono.just(variant);
					}
					SpecifiedMint sm = er.getNode();
					return variantService.setSpecifiedMint(variant, sm, numistaPage.getNumistaParserUserMono(),
							numistaPage.getPipelineStepLogger())
							.flatMap(link -> {
								if (!link.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)
										&& !link.getStatus()
												.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {
									return Mono.error(new ParserException(
											"Failed to link SpecifiedMint: " + link.getStatus()));
								}
								return Mono.just(variant);
							});
				});
	}

	private Mono<Variant> resolveAndLinkMarks(Variant variant, Element scope, NumistaPage numistaPage) {
		return Flux.fromIterable(scope.select("td.date_mark span.mark_container img"))
				.concatMap(img -> {
					String src = NumistaParseUtils.getAttribute(img, "src");
					String code = codeFromImageFilename(src);
					if (StringUtils.isBlank(code)) {
						return Mono.empty();
					}
					return markService.findByCode(code, numistaPage.getPipelineStepLogger())
							.flatMap(er -> {
								if (er.getStatus().equals(FindExecutionStatus.FOUND)) {
									return Mono.just(er.getNode());
								}
								Mark m = new Mark();
								m.setCode(code);
								m.setName(NumistaParseUtils.getAttribute(img, "alt"));
								m.setDescription(NumistaParseUtils.getAttribute(img, "title"));
								m.setPicture(src);
								return markService.create(m, numistaPage.getNumistaParserUserMono(),
										numistaPage.getPipelineStepLogger())
										.flatMap(cr -> {
											if (cr.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
												return Mono.just(cr.getNode());
											}
											return Mono.empty();
										});
							});
				})
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
				new CatalogueRefRow("first_ref1", "first_number1"),
				new CatalogueRefRow("second_ref1", "second_number1"),
				new CatalogueRefRow("third_ref1", "third_number1"),
				new CatalogueRefRow("fourth_ref1", "fourth_number1"));
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
		Element sel = scope.selectFirst("select[name=" + row.refSelectName + "]");
		Element num = scope.selectFirst("input[name=" + row.numberInputName + "]");
		if (sel == null || num == null) {
			return Mono.empty();
		}
		Element opt = sel.select("option[selected]").stream().findFirst().orElse(sel.selectFirst("option"));
		if (opt == null) {
			return Mono.empty();
		}
		String catalogueCode = opt.attr("value");
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
