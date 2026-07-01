package com.colligendis.server.parser.numista;

import com.colligendis.server.database.numista.service.ArtistService;
import com.colligendis.server.database.numista.service.NTypePartService;

import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.numista.model.NTypePart;
import com.colligendis.server.database.numista.model.techdata.PART_TYPE;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.techdata.LetteringScriptService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.numista.exception.ParserException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class NTypePartParser extends Parser {

	private static final Path NTYPE_PICTURES_STORAGE_ROOT = Path
			.of("/Users/kirillbobryakov/Coins/Numista/storage/images/ntypes");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	private final NTypePartService nTypePartService;
	private final NTypeService nTypeService;
	private final ArtistService artistService;
	private final LetteringScriptService letteringScriptService;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			return parseNTypePart(numistaPage, PART_TYPE.OBVERSE)
					.flatMap(parsedNumistaPage -> parseNTypePart(parsedNumistaPage, PART_TYPE.REVERSE))
					.flatMap(parsedNumistaPage -> parseNTypePart(parsedNumistaPage, PART_TYPE.EDGE))
					.flatMap(parsedNumistaPage -> parseNTypePart(parsedNumistaPage, PART_TYPE.WATERMARK))
					.thenReturn(numistaPage);
		});
	}

	private Mono<NumistaPage> parseNTypePart(NumistaPage numistaPage, PART_TYPE partType) {
		return Mono.defer(() -> parseNTypePartFromPage(numistaPage, partType)
				.flatMap(draft -> nTypeService.getNTypePart(numistaPage.nType, partType,
						numistaPage.getPipelineStepLogger())
						.flatMap(executionResult -> {
							if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
								numistaPage.getPipelineStepLogger().debugGreen("NTypePart found: {}", partType);
								return persistParsedNTypePart(draft, executionResult.getNode(), numistaPage);
							}
							if (executionResult.getStatus().equals(FindExecutionStatus.NOT_FOUND)) {
								if (!hasParsedContent(draft)) {
									numistaPage.getPipelineStepLogger().debugOrange(
											"NTypePart has no page data, skipping creation: {}", partType);
									return Mono.just(numistaPage);
								}
								return createNTypePartAndPersist(draft, numistaPage);
							}
							numistaPage.getPipelineStepLogger().error("Failed to get NTypePart: {}",
									executionResult.getStatus());
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(
									new ParserException("Failed to get NTypePart: " + executionResult.getStatus()));
						})));
	}

	private Mono<NTypePart> parseNTypePartFromPage(NumistaPage numistaPage, PART_TYPE partType) {
		NTypePart draft = new NTypePart(partType);
		return Mono.just(draft)
				.flatMap(part -> parseEngravers(part, numistaPage))
				.flatMap(part -> parseDesigners(part, numistaPage))
				.flatMap(part -> parseDescription(part, numistaPage))
				.flatMap(part -> parseLettering(part, numistaPage))
				.flatMap(part -> parseScripts(part, numistaPage))
				.flatMap(part -> parseUnabridgedLegend(part, numistaPage))
				.flatMap(part -> parseLetteringTranslation(part, numistaPage))
				.flatMap(part -> parsePicture(part, numistaPage));
	}

	private boolean hasParsedContent(NTypePart draft) {
		return StringUtils.isNotBlank(draft.getDescription())
				|| StringUtils.isNotBlank(draft.getLettering())
				|| StringUtils.isNotBlank(draft.getUnabridgedLegend())
				|| StringUtils.isNotBlank(draft.getLetteringTranslation())
				|| StringUtils.isNotBlank(draft.getPicture())
				|| StringUtils.isNotBlank(draft.getPictureLocalPath())
				|| (draft.getEngravers() != null && !draft.getEngravers().isEmpty())
				|| (draft.getDesigners() != null && !draft.getDesigners().isEmpty())
				|| (draft.getLetteringScripts() != null && !draft.getLetteringScripts().isEmpty());
	}

	private Mono<NumistaPage> createNTypePartAndPersist(NTypePart draft, NumistaPage numistaPage) {
		PART_TYPE partType = draft.getPartType();
		numistaPage.getPipelineStepLogger().debugOrange("NTypePart not found, creating it: {}", partType);
		return nTypePartService.create(partType, numistaPage.getNumistaParserUserMono(),
				numistaPage.getPipelineStepLogger())
				.flatMap(createdExecutionResult -> {
					if (!createdExecutionResult.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
						numistaPage.getPipelineStepLogger().error("Failed to create NTypePart: {}",
								createdExecutionResult.getStatus());
						return Mono.error(new ParserException(
								"Failed to create NTypePart: " + createdExecutionResult.getStatus()));
					}
					numistaPage.getPipelineStepLogger().debugGreen(
							"NTypePart created, setting relationship between NType and NTypePart: {}", partType);
					NTypePart created = createdExecutionResult.getNode();
					return nTypeService.setNTypePart(numistaPage.nType, created,
							numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
							.flatMap(setExecutionResult -> {
								if (!setExecutionResult.getStatus()
										.equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
									numistaPage.getPipelineStepLogger().error(
											"Failed to set relationship between NType and NTypePart: {}",
											setExecutionResult.getStatus());
									setExecutionResult.logError(numistaPage.getPipelineStepLogger());
									return Mono.error(new ParserException(
											"Failed to set relationship between NType and NTypePart: "
													+ setExecutionResult.getStatus()));
								}
								numistaPage.getPipelineStepLogger().debugGreen(
										"Relationship between NType {} and NTypePart {} set successfully",
										numistaPage.nType.getNid(), created.getPartType());
								return persistParsedNTypePart(draft, created, numistaPage);
							});
				});
	}

	private Mono<NumistaPage> persistParsedNTypePart(NTypePart draft, NTypePart nTypePart, NumistaPage numistaPage) {
		applyDraftProperties(draft, nTypePart);
		return setRelationshipsFromDraft(draft, nTypePart, numistaPage)
				.flatMap(updated -> nTypePartService.update(updated, numistaPage.getNumistaParserUserMono(),
						numistaPage.getPipelineStepLogger()))
				.thenReturn(numistaPage);
	}

	private void applyDraftProperties(NTypePart draft, NTypePart target) {
		target.setDescription(draft.getDescription());
		target.setLettering(draft.getLettering());
		target.setUnabridgedLegend(draft.getUnabridgedLegend());
		target.setLetteringTranslation(draft.getLetteringTranslation());
		target.setPicture(draft.getPicture());
		target.setPictureLocalPath(draft.getPictureLocalPath());
	}

	private Mono<NTypePart> setRelationshipsFromDraft(NTypePart draft, NTypePart nTypePart, NumistaPage numistaPage) {
		return nTypePartService.setEngravers(nTypePart, draft.getEngravers(), numistaPage.getNumistaParserUserMono(),
				numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case WAS_CREATED, IS_ALREADY_EXISTS:
							return Mono.just(nTypePart);
						default:
							return Mono.error(
									new ParserException("Failed to set engravers: " + executionResult.getStatus()));
					}
				})
				.flatMap(part -> nTypePartService.setDesigners(part, draft.getDesigners(),
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger()))
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case WAS_CREATED, IS_ALREADY_EXISTS:
							return Mono.just(nTypePart);
						default:
							return Mono.error(
									new ParserException("Failed to set designers: " + executionResult.getStatus()));
					}
				})
				.flatMap(part -> nTypePartService.setLetteringScripts(part, draft.getLetteringScripts(),
						numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger()))
				.flatMap(executionResult -> {
					switch (executionResult.getStatus()) {
						case WAS_CREATED, IS_ALREADY_EXISTS:
							return Mono.just(nTypePart);
						default:
							return Mono.error(new ParserException(
									"Failed to set lettering scripts: " + executionResult.getStatus()));
					}
				});
	}

	private Mono<NTypePart> parseEngravers(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			final String engraversTag;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					engraversTag = "#engravers_obverse";
					break;
				case REVERSE:
					engraversTag = "#engravers_reverse";
					break;
				case EDGE, WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}

			List<String> engravers = NumistaParseUtils
					.getTextsSelectedOptions(numistaPage.page.selectFirst(engraversTag));
			if (engravers == null || engravers.isEmpty()) {
				return Mono.just(nTypePart);
			}

			return Flux.fromIterable(engravers)
					.flatMap(engraver -> artistService.findByName(engraver, numistaPage.getPipelineStepLogger()))
					.filter(executionResult -> executionResult.getStatus().equals(FindExecutionStatus.FOUND))
					.map(executionResult -> executionResult.getNode())
					.collectList()
					.map(artists -> {
						nTypePart.setEngravers(artists);
						return nTypePart;
					});

		});

	}

	private Mono<NTypePart> parseDesigners(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			String designersTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					designersTag = "#designers_obverse";
					break;
				case REVERSE:
					designersTag = "#designers_reverse";
					break;
				case EDGE, WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}
			List<String> designers = NumistaParseUtils
					.getTextsSelectedOptions(numistaPage.page.selectFirst(designersTag));
			if (designers == null || designers.isEmpty()) {
				return Mono.just(nTypePart);
			}

			return Flux.fromIterable(designers)
					.flatMap(designer -> artistService.findByName(designer, numistaPage.getPipelineStepLogger()))
					.filter(executionResult -> executionResult.getStatus().equals(FindExecutionStatus.FOUND))
					.map(executionResult -> executionResult.getNode())
					.collectList()
					.map(designersList -> {
						nTypePart.setDesigners(designersList);
						return nTypePart;
					});
		});
	}

	private Mono<NTypePart> parseDescription(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {

			String descriptionTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					descriptionTag = "#description_avers";
					break;
				case REVERSE:
					descriptionTag = "#description_revers";
					break;
				case EDGE:
					descriptionTag = "#description_tranche";
					break;
				case WATERMARK:
					descriptionTag = "#description_watermark";
					break;
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}

			String description = NumistaParseUtils.getTagText(numistaPage.page.selectFirst(descriptionTag));
			numistaPage.getPipelineStepLogger().debugGreen("Description set on: {}", description);
			nTypePart.setDescription(description);
			return Mono.just(nTypePart);
		});
	}

	private Mono<NTypePart> parseLettering(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			String letteringTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					letteringTag = "#texte_avers";
					break;
				case REVERSE:
					letteringTag = "#texte_revers";
					break;
				case EDGE:
					letteringTag = "#texte_tranche";
					break;
				case WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}
			String lettering = NumistaParseUtils.getTagText(numistaPage.page.selectFirst(letteringTag));
			numistaPage.getPipelineStepLogger().debugGreen("Lettering set on: {}", lettering);
			nTypePart.setLettering(lettering);
			return Mono.just(nTypePart);
		});
	}

	private Mono<NTypePart> parseScripts(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {

			String scriptsTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					scriptsTag = "#script_avers";
					break;
				case REVERSE:
					scriptsTag = "#script_revers";
					break;
				case EDGE:
					scriptsTag = "#script_tranche";
					break;
				case WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}

			List<HashMap<String, String>> scripts = NumistaParseUtils.getAttributesWithTextSelectedOptions(
					numistaPage.page.selectFirst(scriptsTag));
			if (scripts == null || scripts.isEmpty()) {
				return Mono.just(nTypePart);
			}
			return Flux.fromIterable(scripts)
					.flatMap(script -> letteringScriptService.findByNid(script.get("value"),
							numistaPage.getPipelineStepLogger()))
					.filter(executionResult -> executionResult.getStatus().equals(FindExecutionStatus.FOUND))
					.map(executionResult -> executionResult.getNode())
					.collectList()
					.map(letteringScripts -> {
						nTypePart.setLetteringScripts(letteringScripts);
						return nTypePart;
					});
		});
	}

	private Mono<NTypePart> parseUnabridgedLegend(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			String unabridgedTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					unabridgedTag = "#unabridged_avers";
					break;
				case REVERSE:
					unabridgedTag = "#unabridged_revers";
					break;
				case EDGE:
					unabridgedTag = "#unabridged_tranche";
					break;
				case WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}

			String unabridged = NumistaParseUtils.getTagText(numistaPage.page.selectFirst(unabridgedTag));
			numistaPage.getPipelineStepLogger().debugGreen("Unabridged legend set on: {}", unabridged);
			nTypePart.setUnabridgedLegend(unabridged);
			return Mono.just(nTypePart);
		});
	}

	private Mono<NTypePart> parseLetteringTranslation(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			String traductionTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					traductionTag = "#traduction_avers";
					break;
				case REVERSE:
					traductionTag = "#traduction_revers";
					break;
				case EDGE:
					traductionTag = "#traduction_tranche";
					break;
				case WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}

			String traduction = NumistaParseUtils.getTagText(numistaPage.page.selectFirst(traductionTag));
			numistaPage.getPipelineStepLogger().debugGreen("Lettering translation set on: {}", traduction);
			nTypePart.setLetteringTranslation(traduction);
			return Mono.just(nTypePart);
		});
	}

	private Mono<NTypePart> parsePicture(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			String pictureTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					pictureTag = "fieldset:contains(Obverse)";
					break;
				case REVERSE:
					pictureTag = "fieldset:contains(Reverse (back))";
					break;
				case EDGE:
					pictureTag = "fieldset:has(legend:matchesOwn(^Edge$))";
					break;
				case WATERMARK:
					pictureTag = "fieldset:contains(Watermark)";
					break;
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}

			Element pictureElement = numistaPage.page.selectFirst(pictureTag);
			if (pictureElement == null) {
				return Mono.just(nTypePart);
			}

			String picture = NumistaParseUtils.getAttribute(pictureElement.selectFirst("a[target=_blank]"),
					"href");
			picture = NumistaCatalogueImageUrls.toStoredPicturePath(picture);
			String pictureAbsoluteUrl = NumistaCatalogueImageUrls.toAbsolutePictureUrl(picture);

			numistaPage.getPipelineStepLogger().debugGreen("Picture set on: {}", picture);
			nTypePart.setPicture(picture);

			if (pictureAbsoluteUrl == null || pictureAbsoluteUrl.isEmpty()) {
				return Mono.just(nTypePart);
			}

			try {
				String issuerNumistaCode = numistaPage.getIssuer() != null
						&& numistaPage.getIssuer().getNumistaCode() != null
								? numistaPage.getIssuer().getNumistaCode().trim()
								: "";
				if (issuerNumistaCode.isEmpty()) {
					issuerNumistaCode = "unknown_issuer";
				}

				Path issuerDir = NTYPE_PICTURES_STORAGE_ROOT.resolve(issuerNumistaCode);
				Files.createDirectories(issuerDir);

				String fileName = "nid_" + numistaPage.nType.getNid() + "_" + nTypePart.getPartType() + ".jpg";
				Path pictureLocalPath = issuerDir.resolve(fileName);

				if (Files.exists(pictureLocalPath)) {
					nTypePart.setPictureLocalPath(pictureLocalPath.toString());
					return Mono.just(nTypePart);
				}

				HttpRequest request = HttpRequest.newBuilder(URI.create(pictureAbsoluteUrl)).GET().build();
				HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					Files.write(pictureLocalPath, response.body());
					nTypePart.setPictureLocalPath(pictureLocalPath.toString());
					numistaPage.getPipelineStepLogger().debugGreen("Picture downloaded to: {}", pictureLocalPath);
				} else {
					numistaPage.getPipelineStepLogger().warning("Failed to download picture. URL: {}, status: {}",
							pictureAbsoluteUrl, response.statusCode());
				}
			} catch (IOException | InterruptedException | IllegalArgumentException exception) {
				if (exception instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				numistaPage.getPipelineStepLogger().warning("Failed to store picture locally from URL: {}",
						pictureAbsoluteUrl, exception);
			}

			return Mono.just(nTypePart);
		});
	}

}
