package com.colligendis.server.parser.numista;

import com.colligendis.server.database.numista.service.ArtistService;
import com.colligendis.server.database.numista.service.NTypePartService;

import java.util.HashMap;
import java.util.List;

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
		return Mono.defer(() -> {

			return nTypeService.getNTypePart(numistaPage.nType, partType, numistaPage.getPipelineStepLogger())
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
							numistaPage.getPipelineStepLogger().debugGreen("NTypePart found: {}", partType);
							return Mono.just(executionResult.getNode());
						} else if (executionResult.getStatus().equals(FindExecutionStatus.NOT_FOUND)) {
							numistaPage.getPipelineStepLogger().debugOrange("NTypePart not found, creating it: {}",
									partType);
							return nTypePartService.create(partType, numistaPage.getNumistaParserUserMono(),
									numistaPage.getPipelineStepLogger())
									.flatMap(createdExecutionResult -> {
										if (createdExecutionResult.getStatus()
												.equals(CreateNodeExecutionStatus.WAS_CREATED)) {
											numistaPage.getPipelineStepLogger().debugGreen(
													"NTypePart created, setting relationship between NType and NTypePart: {}",
													partType);
											return nTypeService
													.setNTypePart(numistaPage.nType, createdExecutionResult.getNode(),
															numistaPage.getNumistaParserUserMono(),
															numistaPage.getPipelineStepLogger())
													.flatMap(setExecutionResult -> {
														if (setExecutionResult.getStatus()
																.equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
															numistaPage.getPipelineStepLogger().debugGreen(
																	"Relationship between NType {} and NTypePart {} set successfully",
																	numistaPage.nType.getNid(),
																	createdExecutionResult.getNode().getPartType());
															return Mono.just(createdExecutionResult.getNode());
														} else {
															numistaPage.getPipelineStepLogger().error(
																	"Failed to set relationship between NType and NTypePart: {}",
																	setExecutionResult.getStatus());
															setExecutionResult
																	.logError(numistaPage.getPipelineStepLogger());
															return Mono.error(new ParserException(
																	"Failed to set relationship between NType and NTypePart: "
																			+ setExecutionResult.getStatus()));
														}
													});
										} else {
											numistaPage.getPipelineStepLogger().error("Failed to create NTypePart: {}",
													createdExecutionResult.getStatus());
											return Mono.error(new ParserException("Failed to create NTypePart: "
													+ createdExecutionResult.getStatus()));
										}
									});
						} else {
							numistaPage.getPipelineStepLogger().error("Failed to get NTypePart: {}",
									executionResult.getStatus());
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(
									new ParserException("Failed to get NTypePart: " + executionResult.getStatus()));
						}
					})
					.flatMap(nTypePart -> parseEngravers(nTypePart, numistaPage))
					.flatMap(nTypePart -> parseDesigners(nTypePart, numistaPage))
					.flatMap(nTypePart -> parseDescription(nTypePart, numistaPage))
					.flatMap(nTypePart -> parseLettering(nTypePart, numistaPage))
					.flatMap(nTypePart -> parseScripts(nTypePart, numistaPage))
					.flatMap(nTypePart -> parseUnabridgedLegend(nTypePart, numistaPage))
					.flatMap(nTypePart -> parseLetteringTranslation(nTypePart, numistaPage))
					.flatMap(nTypePart -> parsePicture(nTypePart, numistaPage))
					.flatMap(nTypePart -> nTypePartService.update(nTypePart, numistaPage.getNumistaParserUserMono(),
							numistaPage.getPipelineStepLogger()))
					.then(Mono.defer(() -> Mono.just(numistaPage)));

			// return parseEngravers(numistaPage, partType)
			// .flatMap(parsedNumistaPage -> parseDesigners(parsedNumistaPage, partType))
			// .flatMap(parsedNumistaPage -> parseDescription(parsedNumistaPage, partType))
			// .flatMap(parsedNumistaPage -> parseLettering(parsedNumistaPage, partType))
			// .flatMap(parsedNumistaPage -> parseScripts(parsedNumistaPage, partType))
			// .flatMap(parsedNumistaPage -> parseUnabridgedLegend(parsedNumistaPage,
			// partType))
			// .flatMap(parsedNumistaPage -> parseLetteringTranslation(parsedNumistaPage,
			// partType))
			// .flatMap(parsedNumistaPage -> parsePicture(parsedNumistaPage, partType));
		});
	}

	private Mono<NTypePart> parseEngravers(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			final String engraversTag;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					engraversTag = "#graveur_avers";
					break;
				case REVERSE:
					engraversTag = "#graveur_revers";
					break;
				case EDGE, WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}

			List<String> engravers = NumistaParseUtils
					.getTextsSelectedOptions(numistaPage.page.selectFirst(engraversTag));

			return Flux.fromIterable(engravers)
					.flatMap(engraver -> artistService.findByName(engraver, numistaPage.getPipelineStepLogger()))
					.filter(executionResult -> executionResult.getStatus().equals(FindExecutionStatus.FOUND))
					.map(executionResult -> executionResult.getNode())
					.collectList()
					.flatMap(artists -> nTypePartService.setEngravers(nTypePart, artists,
							numistaPage.getNumistaParserUserMono(),
							numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
							return Mono.just(nTypePart);
						} else {
							return Mono.error(
									new ParserException("Failed to set engravers: " + executionResult.getStatus()));
						}
					});

		});

	}

	private Mono<NTypePart> parseDesigners(NTypePart nTypePart, NumistaPage numistaPage) {
		return Mono.defer(() -> {
			String designersTag = null;
			switch (nTypePart.getPartType()) {
				case OBVERSE:
					designersTag = "#designer_avers";
					break;
				case REVERSE:
					designersTag = "#designer_revers";
					break;
				case EDGE, WATERMARK:
					return Mono.just(nTypePart);
				default:
					throw new IllegalArgumentException("Invalid part type: " + nTypePart.getPartType());
			}
			List<String> designers = NumistaParseUtils
					.getTextsSelectedOptions(numistaPage.page.selectFirst(designersTag));

			return Flux.fromIterable(designers)
					.flatMap(designer -> artistService.findByName(designer, numistaPage.getPipelineStepLogger()))
					.filter(executionResult -> executionResult.getStatus().equals(FindExecutionStatus.FOUND))
					.map(executionResult -> executionResult.getNode())
					.collectList()
					.flatMap(designersList -> nTypePartService.setDesigners(nTypePart, designersList,
							numistaPage.getNumistaParserUserMono(),
							numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
							return Mono.just(nTypePart);
						} else {
							return Mono.error(
									new ParserException("Failed to set designers: " + executionResult.getStatus()));
						}
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
			return Flux.fromIterable(scripts)
					.flatMap(script -> letteringScriptService.findByNid(script.get("value"),
							numistaPage.getPipelineStepLogger()))
					.filter(executionResult -> executionResult.getStatus().equals(FindExecutionStatus.FOUND))
					.map(executionResult -> executionResult.getNode())
					.collectList()
					.flatMap(letteringScripts -> nTypePartService.setLetteringScripts(nTypePart, letteringScripts,
							numistaPage.getNumistaParserUserMono(),
							numistaPage.getPipelineStepLogger()))
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(CreateRelationshipExecutionStatus.WAS_CREATED)) {
							return Mono.just(nTypePart);
						} else {
							return Mono.error(new ParserException(
									"Failed to set lettering scripts: " + executionResult.getStatus()));
						}
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
					pictureTag = "fieldset>legend:containsOwn(Edge)";
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

			numistaPage.getPipelineStepLogger().debugGreen("Picture set on: {}", picture);
			nTypePart.setPicture(picture);
			return Mono.just(nTypePart);
		});
	}

}
