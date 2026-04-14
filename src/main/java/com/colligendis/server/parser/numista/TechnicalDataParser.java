package com.colligendis.server.parser.numista;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.model.techdata.Composition;
import com.colligendis.server.database.numista.model.techdata.CompositionMetalParsingData;
import com.colligendis.server.database.numista.model.techdata.CompositionPartType;
import com.colligendis.server.database.numista.model.techdata.CompositionType;
import com.colligendis.server.database.numista.model.techdata.Metal;
import com.colligendis.server.database.numista.model.techdata.Shape;
import com.colligendis.server.database.numista.service.CollectibleTypeService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.numista.service.techdata.CompositionService;
import com.colligendis.server.database.numista.service.techdata.CompositionTypeService;
import com.colligendis.server.database.numista.service.techdata.MetalService;
import com.colligendis.server.database.numista.service.techdata.ShapeService;
import com.colligendis.server.database.numista.service.techdata.TechniqueService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.ExecutionResult;
import com.colligendis.server.database.result.ExecutionStatuses;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.logger.BaseLogger;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TechnicalDataParser extends Parser {

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return parseTechnicalData(numistaPage);
	}

	private final NTypeService nTypeService;

	private final CompositionService compositionService;

	private final CompositionTypeService compositionTypeService;

	private final CollectibleTypeService collectibleTypeService;

	private final MetalService metalService;

	private final ShapeService shapeService;

	private final TechniqueService techniqueService;

	private Mono<NumistaPage> parseTechnicalData(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			return Mono.just(numistaPage)
					.flatMap(this::parseComposition)
					.flatMap(this::parseShape)
					.flatMap(this::parseWeight)
					.flatMap(this::parseSize)
					.flatMap(this::parseThickness)
					.flatMap(this::parseTechniques)
					.flatMap(this::parseAlignment);
		});
	}

	private Mono<NumistaPage> parseComposition(NumistaPage numistaPage) {

		Document page = numistaPage.page;
		HashMap<String, String> metalType = NumistaParseUtils.getAttributeWithTextSelectedOption(page, "#metal_type");

		Mono<ExecutionResult<Composition, ? extends ExecutionStatuses>> compositionMono = metalType != null
				? getOrCreateNTypeComposition(numistaPage)
						.flatMap(executionResult -> {
							Object st = executionResult.getStatus();
							if (FindExecutionStatus.FOUND.equals(st)
									|| CreateNodeExecutionStatus.WAS_CREATED.equals(st)) {
								return setCompositionTypeForComposition(executionResult, metalType.get("value"),
										metalType.get("text"), numistaPage);
							} else {
								return Mono.just(executionResult);
							}
						})
				: getOrCreateNTypeComposition(numistaPage);

		var logger = numistaPage.getPipelineStepLogger();
		var userMono = numistaPage.getNumistaParserUserMono();
		return compositionMono
				.flatMap(compositionResult -> {
					Composition composition = compositionResult.getNode();
					if (composition == null) {
						logger.error("Composition not found and not created for NType: {}", numistaPage.nType.getNid());
						return Mono.just(numistaPage);
					}
					return applyCompositionParts(composition, metalType, page, userMono, logger)
							.flatMap(c -> handleBanknoteComposition(c, numistaPage))
							.flatMap(c -> updateMetalDetails(c, page, numistaPage))
							.thenReturn(numistaPage);
				})
				.switchIfEmpty(Mono.just(numistaPage))
				.onErrorResume(err -> {
					numistaPage.getPipelineStepLogger().error("Error parsing composition: {}", err.getMessage());
					return Mono.just(numistaPage);
				});
	}

	private static <S extends ExecutionStatuses> ExecutionResult<Composition, ? extends ExecutionStatuses> widenCompositionExecution(
			ExecutionResult<Composition, S> result) {
		return result;
	}

	private Mono<ExecutionResult<Composition, ? extends ExecutionStatuses>> createCompositionForNType(
			NumistaPage numistaPage, BaseLogger logger) {
		logger.debugOrange(
				"Composition not found for NType, creating new one: {}",
				numistaPage.nType.getNid());
		return compositionService
				.create(new Composition(), numistaPage.getNumistaParserUserMono(), logger)
				.flatMap(createResult -> Mono.just(createResult)
						.filter(r -> r.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED))
						.doOnNext(r -> logger.debugGreen(
								"Composition created for NType: {}",
								numistaPage.nType.getNid()))
						.flatMap(r -> nTypeService.setComposition(
								numistaPage.nType,
								r.getNode(),
								numistaPage.getNumistaParserUserMono(),
								logger).thenReturn(r))
						.switchIfEmpty(Mono.defer(() -> {
							logger.error(
									"Failed to create Composition for NType: {}",
									numistaPage.nType.getNid());
							return Mono.just(createResult);
						})))
				.map(TechnicalDataParser::widenCompositionExecution);
	}

	private Mono<ExecutionResult<Composition, ? extends ExecutionStatuses>> getOrCreateNTypeComposition(
			NumistaPage numistaPage) {

		var logger = numistaPage.getPipelineStepLogger();

		Mono<ExecutionResult<Composition, ? extends ExecutionStatuses>> whenFound = nTypeService
				.getComposition(numistaPage.nType, logger)
				.filter(result -> result.getStatus().equals(FindExecutionStatus.FOUND))
				.doOnNext(result -> logger.debugGreen("Composition found for NType: {}", numistaPage.nType.getNid()))
				.flatMap(r -> Mono.just(widenCompositionExecution(r)));

		return whenFound
				.switchIfEmpty(Mono.defer(() -> createCompositionForNType(numistaPage, logger)))
				.switchIfEmpty(Mono.defer(() -> {
					logger.error("Failed to get Composition for NType: {}", numistaPage.nType.getNid());
					return nTypeService.getComposition(numistaPage.nType, logger)
							.flatMap(r -> Mono.just(widenCompositionExecution(r)));
				}));
	}

	private Mono<Metal> resolveMetal(String nid, String name, Mono<ColligendisUser> userMono, BaseLogger logger) {
		return metalService.findByNidOrNameOrCreate(nid, name, userMono, logger)
				.flatMap(er -> {
					if (FindExecutionStatus.FOUND.equals(er.getStatus())
							|| CreateNodeExecutionStatus.WAS_CREATED.equals(er.getStatus())) {
						return Mono.just(er.getNode());
					}
					return Mono.empty();
				});
	}

	private Mono<Composition> updateCompositionProperties(Composition composition, Mono<ColligendisUser> userMono,
			BaseLogger logger) {
		return compositionService.update(composition, userMono, logger)
				.map(er -> er.getNode() != null ? er.getNode() : composition);
	}

	private Mono<Composition> applyCompositionParts(Composition composition,
			HashMap<String, String> metalType,
			Document page, Mono<ColligendisUser> userMono, BaseLogger logger) {
		if (metalType == null) {
			return Mono.just(composition);
		}
		String compositionTypeCode = metalType.get("value");
		Mono<Composition> result = Mono.just(composition);
		switch (compositionTypeCode) {
			case "plain" -> {
				CompositionMetalParsingData metalParsingData = parseCompositionMetal("#metal1", "#fineness1", page);
				composition.setPart1Type(CompositionPartType.material);
				composition.setPart1MetalFineness(metalParsingData.fineness());
				composition.setPart2Type(null);
				composition.setPart2MetalFineness(null);
				composition.setPart3Type(null);
				composition.setPart3MetalFineness(null);
				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);

				Mono<Metal> metal1Mono = resolveMetal(metalParsingData.metalCode(), metalParsingData.metalName(),
						userMono, logger);
				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);
				result = Mono.zip(metal1Mono, updatedCompositionMono).flatMap(tuple -> {
					Metal metal1 = tuple.getT1();
					Composition updatedComposition = tuple.getT2();
					return compositionService.setPartMetal(updatedComposition, metal1, 1, userMono, logger)
							.then(compositionService.removePartMetal(updatedComposition, 2, userMono, logger))
							.then(compositionService.removePartMetal(updatedComposition, 3, userMono, logger))
							.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
							.thenReturn(updatedComposition);
				});

			}
			case "plated" -> {
				CompositionMetalParsingData coreMetalParsingData = parseCompositionMetal("#metal1", "#fineness1", page);
				CompositionMetalParsingData platingMetalParsingData = parseCompositionMetal("#metal2", "#fineness2",
						page);

				composition.setPart1Type(CompositionPartType.core);
				composition.setPart1MetalFineness(coreMetalParsingData.fineness());
				Mono<Metal> coreMetalMono = resolveMetal(coreMetalParsingData.metalCode(),
						coreMetalParsingData.metalName(), userMono, logger);

				composition.setPart2Type(CompositionPartType.plating);
				composition.setPart2MetalFineness(platingMetalParsingData.fineness());
				Mono<Metal> platingMetalMono = resolveMetal(platingMetalParsingData.metalCode(),
						platingMetalParsingData.metalName(), userMono, logger);

				composition.setPart3Type(null);
				composition.setPart3MetalFineness(null);
				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);

				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);

				result = updatedCompositionMono.flatMap(updatedComposition -> {
					return Mono.zip(coreMetalMono, platingMetalMono).flatMap(tuple -> {
						Metal coreMetal = tuple.getT1();
						Metal platingMetal = tuple.getT2();
						return compositionService.setPartMetal(updatedComposition, coreMetal, 1, userMono, logger)
								.then(compositionService.setPartMetal(updatedComposition, platingMetal, 2, userMono,
										logger))
								.then(compositionService.removePartMetal(updatedComposition, 3, userMono, logger))
								.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
								.thenReturn(updatedComposition);
					});

				});

			}
			case "clad" -> {
				CompositionMetalParsingData coreMetalParsingData = parseCompositionMetal("#metal1", "#fineness1", page);
				CompositionMetalParsingData cladMetalParsingData = parseCompositionMetal("#metal2", "#fineness2", page);

				composition.setPart1Type(CompositionPartType.core);
				composition.setPart1MetalFineness(coreMetalParsingData.fineness());
				Mono<Metal> coreMetalMono = resolveMetal(coreMetalParsingData.metalCode(),
						coreMetalParsingData.metalName(), userMono, logger);

				composition.setPart2Type(CompositionPartType.clad);
				composition.setPart2MetalFineness(cladMetalParsingData.fineness());
				Mono<Metal> cladMetalMono = resolveMetal(cladMetalParsingData.metalCode(),
						cladMetalParsingData.metalName(), userMono, logger);

				composition.setPart3Type(null);
				composition.setPart3MetalFineness(null);
				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);

				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);

				result = Mono.zip(coreMetalMono, cladMetalMono,
						updatedCompositionMono).flatMap(tuple -> {
							Metal coreMetal = tuple.getT1();
							Metal cladMetal = tuple.getT2();
							Composition updatedComposition = tuple.getT3();
							return compositionService.setPartMetal(updatedComposition, coreMetal, 1, userMono, logger)
									.then(compositionService.setPartMetal(updatedComposition, cladMetal, 2, userMono,
											logger))
									.then(compositionService.removePartMetal(updatedComposition, 3, userMono, logger))
									.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
									.thenReturn(updatedComposition);
						});

			}
			case "bimetallic" -> {
				CompositionMetalParsingData centerMetalParsingData = parseCompositionMetal("#metal1", "#fineness1",
						page);
				CompositionMetalParsingData ringMetalParsingData = parseCompositionMetal("#metal2", "#fineness2", page);

				composition.setPart1Type(CompositionPartType.center);
				composition.setPart1MetalFineness(centerMetalParsingData.fineness());
				Mono<Metal> centerMetalMono = resolveMetal(centerMetalParsingData.metalCode(),
						centerMetalParsingData.metalName(), userMono, logger);

				composition.setPart2Type(CompositionPartType.ring);
				composition.setPart2MetalFineness(ringMetalParsingData.fineness());
				Mono<Metal> ringMetalMono = resolveMetal(ringMetalParsingData.metalCode(),
						ringMetalParsingData.metalName(), userMono, logger);

				composition.setPart3Type(null);
				composition.setPart3MetalFineness(null);
				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);

				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);
				result = Mono.zip(centerMetalMono, ringMetalMono,
						updatedCompositionMono).flatMap(tuple -> {
							Metal centerMetal = tuple.getT1();
							Metal ringMetal = tuple.getT2();
							Composition updatedComposition = tuple.getT3();
							return compositionService.setPartMetal(updatedComposition, centerMetal, 1, userMono, logger)
									.then(compositionService.setPartMetal(updatedComposition, ringMetal, 2, userMono,
											logger))
									.then(compositionService.removePartMetal(updatedComposition, 3, userMono, logger))
									.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
									.thenReturn(updatedComposition);
						});

			}
			case "bimetallic_plated" -> {
				CompositionMetalParsingData centerCoreMetalParsingData = parseCompositionMetal("#metal1", "#fineness1",
						page);
				CompositionMetalParsingData centerPlatingMetalParsingData = parseCompositionMetal("#metal2",
						"#fineness2", page);
				CompositionMetalParsingData ringMetalParsingData = parseCompositionMetal("#metal3", "#fineness3", page);

				composition.setPart1Type(CompositionPartType.center_core);
				composition.setPart1MetalFineness(centerCoreMetalParsingData.fineness());
				Mono<Metal> centerCoreMetalMono = resolveMetal(centerCoreMetalParsingData.metalCode(),
						centerCoreMetalParsingData.metalName(), userMono, logger);
				composition.setPart2Type(CompositionPartType.center_plating);
				composition.setPart2MetalFineness(centerPlatingMetalParsingData.fineness());
				Mono<Metal> centerPlatingMetalMono = resolveMetal(centerPlatingMetalParsingData.metalCode(),
						centerPlatingMetalParsingData.metalName(), userMono, logger);
				composition.setPart3Type(CompositionPartType.ring);
				composition.setPart3MetalFineness(ringMetalParsingData.fineness());
				Mono<Metal> ringMetalMono = resolveMetal(ringMetalParsingData.metalCode(),
						ringMetalParsingData.metalName(), userMono, logger);
				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);

				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);
				result = Mono.zip(centerCoreMetalMono, centerPlatingMetalMono, ringMetalMono,
						updatedCompositionMono)
						.flatMap(tuple -> {
							Metal centerCoreMetal = tuple.getT1();
							Metal centerPlatingMetal = tuple.getT2();
							Metal ringMetal = tuple.getT3();
							Composition updatedComposition = tuple.getT4();
							return compositionService
									.setPartMetal(updatedComposition, centerCoreMetal, 1, userMono, logger)
									.then(compositionService.setPartMetal(updatedComposition, centerPlatingMetal, 2,
											userMono, logger))
									.then(compositionService.setPartMetal(updatedComposition, ringMetal, 3, userMono,
											logger))
									.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
									.thenReturn(updatedComposition);
						});

			}
			case "bimetallic_plated_ring" -> {
				CompositionMetalParsingData centerMetalParsingData = parseCompositionMetal("#metal1", "#fineness1",
						page);
				CompositionMetalParsingData ringCoreMetalParsingData = parseCompositionMetal("#metal2", "#fineness2",
						page);
				CompositionMetalParsingData ringPlatingMetalParsingData = parseCompositionMetal("#metal3", "#fineness3",
						page);

				composition.setPart1Type(CompositionPartType.center);
				composition.setPart1MetalFineness(centerMetalParsingData.fineness());
				Mono<Metal> centerMetalMono = resolveMetal(centerMetalParsingData.metalCode(),
						centerMetalParsingData.metalName(), userMono, logger);
				composition.setPart2Type(CompositionPartType.ring_core);
				composition.setPart2MetalFineness(ringCoreMetalParsingData.fineness());
				Mono<Metal> ringCoreMetalMono = resolveMetal(ringCoreMetalParsingData.metalCode(),
						ringCoreMetalParsingData.metalName(), userMono, logger);
				composition.setPart3Type(CompositionPartType.ring_plating);
				composition.setPart3MetalFineness(ringPlatingMetalParsingData.fineness());
				Mono<Metal> ringPlatingMetalMono = resolveMetal(ringPlatingMetalParsingData.metalCode(),
						ringPlatingMetalParsingData.metalName(), userMono, logger);
				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);

				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);
				result = Mono.zip(centerMetalMono, ringCoreMetalMono, ringPlatingMetalMono,
						updatedCompositionMono)
						.flatMap(tuple -> {
							Metal centerMetal = tuple.getT1();
							Metal ringCoreMetal = tuple.getT2();
							Metal ringPlatingMetal = tuple.getT3();
							Composition updatedComposition = tuple.getT4();
							return compositionService.setPartMetal(updatedComposition, centerMetal, 1, userMono, logger)
									.then(compositionService.setPartMetal(updatedComposition, ringCoreMetal, 2,
											userMono, logger))
									.then(compositionService.setPartMetal(updatedComposition, ringPlatingMetal, 3,
											userMono, logger))
									.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
									.thenReturn(updatedComposition);
						});
			}
			case "bimetallic_plated_plated" -> {
				CompositionMetalParsingData centerCoreMetalParsingData = parseCompositionMetal("#metal1", "#fineness1",
						page);
				CompositionMetalParsingData centerPlatingMetalParsingData = parseCompositionMetal("#metal2",
						"#fineness2", page);
				CompositionMetalParsingData ringCoreMetalParsingData = parseCompositionMetal("#metal3", "#fineness3",
						page);
				CompositionMetalParsingData ringPlatingMetalParsingData = parseCompositionMetal("#metal4", "#fineness4",
						page);

				composition.setPart1Type(CompositionPartType.center_core);
				composition.setPart1MetalFineness(centerCoreMetalParsingData.fineness());
				Mono<Metal> centerCoreMetalMono = resolveMetal(centerCoreMetalParsingData.metalCode(),
						centerCoreMetalParsingData.metalName(), userMono, logger);
				composition.setPart2Type(CompositionPartType.center_plating);
				composition.setPart2MetalFineness(centerPlatingMetalParsingData.fineness());
				Mono<Metal> centerPlatingMetalMono = resolveMetal(centerPlatingMetalParsingData.metalCode(),
						centerPlatingMetalParsingData.metalName(), userMono, logger);
				composition.setPart3Type(CompositionPartType.ring_core);
				composition.setPart3MetalFineness(ringCoreMetalParsingData.fineness());
				Mono<Metal> ringCoreMetalMono = resolveMetal(ringCoreMetalParsingData.metalCode(),
						ringCoreMetalParsingData.metalName(), userMono, logger);
				composition.setPart4Type(CompositionPartType.ring_plating);
				composition.setPart4MetalFineness(ringPlatingMetalParsingData.fineness());
				Mono<Metal> ringPlatingMetalMono = resolveMetal(ringPlatingMetalParsingData.metalCode(),
						ringPlatingMetalParsingData.metalName(), userMono, logger);

				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);
				result = Mono
						.zip(centerCoreMetalMono, centerPlatingMetalMono, ringCoreMetalMono,
								ringPlatingMetalMono,
								updatedCompositionMono)
						.flatMap(tuple -> {
							Metal centerCoreMetal = tuple.getT1();
							Metal centerPlatingMetal = tuple.getT2();
							Metal ringCoreMetal = tuple.getT3();
							Metal ringPlatingMetal = tuple.getT4();
							Composition updatedComposition = tuple.getT5();
							return compositionService
									.setPartMetal(updatedComposition, centerCoreMetal, 1, userMono, logger)
									.then(compositionService.setPartMetal(updatedComposition, centerPlatingMetal, 2,
											userMono, logger))
									.then(compositionService.setPartMetal(updatedComposition, ringCoreMetal, 3,
											userMono, logger))
									.then(compositionService.setPartMetal(updatedComposition, ringPlatingMetal, 4,
											userMono, logger))
									.thenReturn(updatedComposition);
						});

			}
			case "bimetallic_clad" -> {
				CompositionMetalParsingData centerCoreMetalParsingData = parseCompositionMetal("#metal1", "#fineness1",
						page);
				CompositionMetalParsingData centerCladMetalParsingData = parseCompositionMetal("#metal2", "#fineness2",
						page);
				CompositionMetalParsingData ringMetalParsingData = parseCompositionMetal("#metal3", "#fineness3", page);

				composition.setPart1Type(CompositionPartType.center_core);
				composition.setPart1MetalFineness(centerCoreMetalParsingData.fineness());
				Mono<Metal> centerCoreMetalMono = resolveMetal(centerCoreMetalParsingData.metalCode(),
						centerCoreMetalParsingData.metalName(), userMono, logger);
				composition.setPart2Type(CompositionPartType.center_clad);
				composition.setPart2MetalFineness(centerCladMetalParsingData.fineness());
				Mono<Metal> centerCladMetalMono = resolveMetal(centerCladMetalParsingData.metalCode(),
						centerCladMetalParsingData.metalName(), userMono, logger);
				composition.setPart3Type(CompositionPartType.ring);
				composition.setPart3MetalFineness(ringMetalParsingData.fineness());
				Mono<Metal> ringMetalMono = resolveMetal(ringMetalParsingData.metalCode(),
						ringMetalParsingData.metalName(), userMono, logger);

				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);

				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);
				result = Mono.zip(centerCoreMetalMono, centerCladMetalMono, ringMetalMono,
						updatedCompositionMono)
						.flatMap(tuple -> {
							Metal centerCoreMetal = tuple.getT1();
							Metal centerCladMetal = tuple.getT2();
							Metal ringMetal = tuple.getT3();
							Composition updatedComposition = tuple.getT4();
							return compositionService
									.setPartMetal(updatedComposition, centerCoreMetal, 1, userMono, logger)
									.then(compositionService.setPartMetal(updatedComposition, centerCladMetal, 2,
											userMono, logger))
									.then(compositionService.setPartMetal(updatedComposition, ringMetal, 3, userMono,
											logger))
									.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
									.thenReturn(updatedComposition);
						});
			}
			case "trimetallic" -> {
				CompositionMetalParsingData centerMetalParsingData = parseCompositionMetal("#metal1", "#fineness1",
						page);
				CompositionMetalParsingData middleRingMetalParsingData = parseCompositionMetal("#metal2", "#fineness2",
						page);
				CompositionMetalParsingData outerRingMetalParsingData = parseCompositionMetal("#metal3", "#fineness3",
						page);

				composition.setPart1Type(CompositionPartType.center);
				composition.setPart1MetalFineness(centerMetalParsingData.fineness());
				Mono<Metal> centerMetalMono = resolveMetal(centerMetalParsingData.metalCode(),
						centerMetalParsingData.metalName(), userMono, logger);
				composition.setPart2Type(CompositionPartType.middle_ring);
				composition.setPart2MetalFineness(middleRingMetalParsingData.fineness());
				Mono<Metal> middleRingMetalMono = resolveMetal(middleRingMetalParsingData.metalCode(),
						middleRingMetalParsingData.metalName(), userMono, logger);
				composition.setPart3Type(CompositionPartType.outer_ring);
				composition.setPart3MetalFineness(outerRingMetalParsingData.fineness());
				Mono<Metal> outerRingMetalMono = resolveMetal(outerRingMetalParsingData.metalCode(),
						outerRingMetalParsingData.metalName(), userMono, logger);
				composition.setPart4Type(null);
				composition.setPart4MetalFineness(null);
				Mono<Composition> updatedCompositionMono = updateCompositionProperties(composition, userMono, logger);
				result = Mono.zip(centerMetalMono, middleRingMetalMono, outerRingMetalMono,
						updatedCompositionMono)
						.flatMap(tuple -> {
							Metal centerMetal = tuple.getT1();
							Metal middleRingMetal = tuple.getT2();
							Metal outerRingMetal = tuple.getT3();
							Composition updatedComposition = tuple.getT4();
							return compositionService.setPartMetal(updatedComposition, centerMetal, 1, userMono, logger)
									.then(compositionService.setPartMetal(updatedComposition, middleRingMetal, 2,
											userMono, logger))
									.then(compositionService.setPartMetal(updatedComposition, outerRingMetal, 3,
											userMono, logger))
									.then(compositionService.removePartMetal(updatedComposition, 4, userMono, logger))
									.thenReturn(updatedComposition);
						});
			}
			default -> {
				/* no specific parts */ }
		}
		return result;
	}

	private Mono<Composition> handleBanknoteComposition(Composition composition,
			NumistaPage numistaPage) {
		if (numistaPage.collectibleType == null) {
			return Mono.just(composition);
		}
		return collectibleTypeService
				.findTopCollectibleTypeRecursive(numistaPage.collectibleType, numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (!executionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
						return Mono.just(composition);
					}
					CollectibleType topType = executionResult.getNode();
					if (!CollectibleType.BANKNOTES_CODE.equals(topType.getCode())
							&& !CollectibleType.PAPER_EXONUMIA_CODE.equals(topType.getCode())) {
						return Mono.just(composition);
					}
					HashMap<String, String> compositionHashMap = NumistaParseUtils
							.getAttributeWithTextSelectedOption(numistaPage.page, "#metal1");
					if (compositionHashMap == null ||
							!isValueAndTextNotNullAndNotEmpty(compositionHashMap)) {
						return Mono.just(composition);
					}
					String compositionTypeCode = compositionHashMap.get("value");
					String compositionTypeName = compositionHashMap.get("text");
					ExecutionResult<Composition, FindExecutionStatus> compositionResult = new ExecutionResult<>(
							composition,
							FindExecutionStatus.FOUND, null);
					return setCompositionTypeForComposition(compositionResult, compositionTypeCode,
							compositionTypeName, numistaPage)
							.map(er -> er.getNode() != null ? er.getNode() : composition);
				});

	}

	private Mono<Composition> updateMetalDetails(Composition composition,
			Document page, NumistaPage numistaPage) {
		String metalDetails = NumistaParseUtils.getAttribute(page.selectFirst("#metal_details"), "value");
		if (metalDetails == null || metalDetails.isEmpty()) {
			return Mono.just(composition);
		}
		composition.setCompositionAdditionalDetails(metalDetails);
		return compositionService
				.update(composition, numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
				.thenReturn(composition);
	}

	private static boolean isValueAndTextNotNullAndNotEmpty(HashMap<String, String> map) {
		return map != null && StringUtils.isNotBlank(map.get("value")) &&
				StringUtils.isNotBlank(map.get("text"));
	}

	public Mono<ExecutionResult<Composition, ? extends ExecutionStatuses>> setCompositionTypeForComposition(
			ExecutionResult<Composition, ? extends ExecutionStatuses> compositionResult,
			String compositionTypeCode,
			String compositionTypeName, NumistaPage numistaPage) {

		Composition composition = compositionResult.getNode();
		var logger = numistaPage.getPipelineStepLogger();

		return compositionService.findCompositionType(composition, logger)
				.flatMap(existingTypeResult -> {
					if (existingTypeResult.getStatus().equals(FindExecutionStatus.FOUND)
							&& existingTypeResult.getNode().getCode().equals(compositionTypeCode)) {
						logger.debugGreen("CompositionType with code {} ({}) is already set for Composition: {}",
								compositionTypeCode, compositionTypeName, composition.getUuid());
						return Mono.just(compositionResult);
					}
					return compositionTypeService
							.findByCodeOrCreate(compositionTypeCode, compositionTypeName,
									numistaPage.getNumistaParserUserMono(), logger)
							.flatMap(ctResult -> {
								Object cts = ctResult.getStatus();
								if (!FindExecutionStatus.FOUND.equals(cts)
										&& !CreateNodeExecutionStatus.WAS_CREATED.equals(cts)) {
									logger.error("Failed to find CompositionType: {} for Composition: {}",
											compositionTypeCode, composition.getUuid());
									return Mono.just(compositionResult);
								}
								CompositionType newType = ctResult.getNode();
								logger.debugGreen(
										"Found CompositionType: {} ({}) for Composition: {}, setting new one",
										newType.getName(), compositionTypeName, composition.getUuid());
								return compositionService.setCompositionType(composition, newType,
										numistaPage.getNumistaParserUserMono(), logger)
										.thenReturn(compositionResult);
							});
				});

	}

	private static CompositionMetalParsingData parseCompositionMetal(String metalId, String finenessId,
			Document document) {

		String metalCode = "";
		String metalName = "Unknown";
		String fineness = "";

		Element metalElement = document.selectFirst(metalId);
		if (metalElement != null) {
			Elements metalsElements = metalElement.select("option");
			for (Element metal : metalsElements) {
				if (metal.attributes().get("selected").equals("selected")) {
					metalCode = metal.attributes().get("value");
					metalName = metal.text();
				}
			}
		}

		Element fineness1Element = document.selectFirst(finenessId);
		if (fineness1Element != null) {
			// pattern="[0-9]{1,3}(\.[0-9]+)?"
			fineness = fineness1Element.attributes().get("value");
		}

		return new CompositionMetalParsingData(metalCode, metalName, fineness);
	}

	private Mono<NumistaPage> parseShape(NumistaPage numistaPage) {
		return Mono.just(numistaPage).flatMap(np -> {
			HashMap<String, String> shape = NumistaParseUtils.getAttributeWithTextSelectedOption(np.page,
					"#shape");
			if (shape != null && isValueAndTextNotNullAndNotEmpty(shape)) {
				String shapeNid = shape.get("value");
				String shapeName = shape.get("text");
				return shapeService.findByNidOrCreate(shapeNid, shapeName, np.getNumistaParserUserMono(),
						np.getPipelineStepLogger())
						.flatMap(shapeEr -> {
							if (!shapeEr.getStatus().equals(FindExecutionStatus.FOUND)
									&& !shapeEr.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
								return Mono.just(np);
							}
							Shape foundShape = shapeEr.getNode();
							return nTypeService.setShape(np.nType, foundShape, np.getNumistaParserUserMono(),
									np.getPipelineStepLogger())
									.thenReturn(np);
						});
			}
			return Mono.just(np);
		}).flatMap(np -> {
			String shapeDetails = NumistaParseUtils.getAttribute(np.page.selectFirst("#shape_details"),
					"value");
			if (shapeDetails != null && !shapeDetails.isEmpty()) {
				numistaPage.getNType().setShapeAdditionalDetails(shapeDetails);
			}
			return Mono.just(numistaPage);
		});
	}

	private Mono<NumistaPage> parseWeight(NumistaPage numistaPage) {
		return Mono.just(numistaPage).flatMap(np -> {
			String poids = NumistaParseUtils.getAttribute(np.page.selectFirst("#poids"),
					"value");
			if (poids != null && !poids.isEmpty()) {
				numistaPage.getNType().setWeight(Double.parseDouble(poids));
			}
			return Mono.just(np);
		});
	}

	private Mono<NumistaPage> parseSize(NumistaPage numistaPage) {
		return Mono.just(numistaPage).flatMap(np -> {
			String dimension = NumistaParseUtils.getAttribute(np.page.selectFirst("#dimension"), "value");
			if (dimension != null && !dimension.isEmpty()) {
				numistaPage.getNType().setSize(Double.parseDouble(dimension));
			}
			return Mono.just(np);
		}).flatMap(np -> {
			// Second dimension
			// <option value="42">Klippe</option>
			// <option value="50">Other</option>
			// <option value="36">Oval</option>
			// <option value="37">Oval with a loop</option>
			// <option value="4">Rectangular</option>
			// <option value="46">Rectangular (irregular)</option>
			// <option value="75">Sculptural</option>
			// <option value="54">Spade</option>
			// <option value="44">Square</option>
			// <option value="41">Square (irregular)</option>
			// <option value="55">Square with angled corners</option>
			// <option value="40">Square with rounded corners</option>
			// <option value="71">Square with scalloped edges</option>

			// <option value="">Unknown</option>
			// <option value="150">Other</option>
			// <option value="100" selected="selected">Rectangular</option>
			// <option value="102">Rectangular (hand cut)</option>
			// <option value="101">Rectangular with undulating edge</option>
			// <option value="105">Square</option>

			return nTypeService.getShape(np.nType, np.getPipelineStepLogger()).flatMap(shapeEr -> {
				if (!shapeEr.getStatus().equals(FindExecutionStatus.FOUND)) {
					return Mono.just(np);
				}
				Shape shape = shapeEr.getNode();
				List<String> shapeCodes = Arrays.asList("", "4", "36", "37", "40", "41",
						"42", "44", "46", "50",
						"54", "55", "71", "75", "100", "101", "102", "105", "150");
				if (shapeCodes.contains(shape.getNid())) {
					String dimension2 = NumistaParseUtils
							.getAttribute(np.page.selectFirst("input[name=dimension2]"), "value");
					if (dimension2 != null && !dimension2.isEmpty()) {
						numistaPage.getNType().setSize2(Double.parseDouble(dimension2));
					}
				}
				return Mono.just(np);
			});
		});
	}

	private Mono<NumistaPage> parseThickness(NumistaPage numistaPage) {
		return Mono.just(numistaPage).flatMap(np -> {
			String epaisseur = NumistaParseUtils.getAttribute(np.page.selectFirst("#epaisseur"), "value");
			if (epaisseur != null && !epaisseur.isEmpty()) {
				numistaPage.getNType().setThickness(Double.parseDouble(epaisseur));
			}
			return Mono.just(np);
		});
	}

	private Mono<NumistaPage> parseTechniques(NumistaPage numistaPage) {
		var logger = numistaPage.getPipelineStepLogger();
		return Mono.just(numistaPage).flatMap(np -> {
			List<HashMap<String, String>> techniques = NumistaParseUtils.getAttributesWithTextSelectedOptions(
					np.page.selectFirst("#techniques"));

			if (techniques != null && !techniques.isEmpty()) {
				return Flux.fromIterable(techniques)
						.flatMap(t -> techniqueService.findByNidOrCreate(t.get("value"), t.get("text"),
								np.getNumistaParserUserMono(), np.getPipelineStepLogger()))
						.flatMap(techniqueEr -> {
							if (techniqueEr.getStatus().equals(FindExecutionStatus.FOUND)
									|| techniqueEr.getStatus().equals(CreateNodeExecutionStatus.WAS_CREATED)) {
								var node = techniqueEr.getNode();
								logger.debugGreen("Technique found or created: {} ({}) for NType: {}",
										node.getNid(), node.getName(), np.nType.getNid());
								return Mono.just(node);
							}
							var node = techniqueEr.getNode();
							logger.error(
									"Failed to find or create Technique: status: {} nid: {} name: {} for NType: {}",
									techniqueEr.getStatus(),
									node != null ? node.getNid() : null,
									node != null ? node.getName() : null,
									np.nType.getNid());
							return Mono.empty();
						})
						.collectList()
						.flatMap(techniqueList -> nTypeService
								.setTechniques(np.nType, techniqueList, np.getNumistaParserUserMono(),
										np.getPipelineStepLogger())
								.thenReturn(np));
			}
			logger.debugOrange("No techniques found for NType: {}", np.nType.getNid());
			return Mono.just(np);
		}).flatMap(np -> {
			// Technique Additional details
			String techniqueDetail = NumistaParseUtils.getAttribute(np.page.selectFirst("#technique_details"),
					"value");
			if (techniqueDetail != null && !techniqueDetail.isEmpty()) {
				numistaPage.getNType().setTechniqueAdditionalDetails(techniqueDetail);
			}
			return Mono.just(numistaPage);
		});
	}

	private Mono<NumistaPage> parseAlignment(NumistaPage numistaPage) {

		return Mono.just(numistaPage).flatMap(np -> {
			String alignementCode = NumistaParseUtils
					.getAttribute(np.page.selectFirst("input[name=alignement][checked=checked]"),
							"value");
			if (alignementCode != null && !alignementCode.isEmpty()) {
				numistaPage.getNType().setAlignment(alignementCode);
			}
			return Mono.just(np);
		});
	}

}
