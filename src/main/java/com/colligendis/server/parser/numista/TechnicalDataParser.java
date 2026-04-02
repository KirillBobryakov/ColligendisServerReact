// package com.colligendis.server.parser.numista;

// import java.util.Arrays;
// import java.util.HashMap;
// import java.util.List;
// import java.util.function.Function;

// import org.apache.commons.lang3.StringUtils;
// import org.jsoup.nodes.Document;
// import org.jsoup.nodes.Element;
// import org.jsoup.select.Elements;

// import com.colligendis.server.database.ColligendisUser;
// import com.colligendis.server.database.N4JUtil;
// import com.colligendis.server.database.exception.NotFoundError;
// import com.colligendis.server.database.numista.model.CollectibleType;
// import com.colligendis.server.database.numista.model.NType;
// import com.colligendis.server.database.numista.model.techdata.Composition;
// import
// com.colligendis.server.database.numista.model.techdata.CompositionMetalParsingData;
// import
// com.colligendis.server.database.numista.model.techdata.CompositionPartType;
// import com.colligendis.server.database.numista.model.techdata.Metal;
// import com.colligendis.server.database.numista.model.techdata.Technique;
// import
// com.colligendis.server.database.numista.service.CollectibleTypeService;
// import com.colligendis.server.database.numista.service.NTypeService;
// import
// com.colligendis.server.database.numista.service.techdata.CompositionService;
// import
// com.colligendis.server.database.numista.service.techdata.CompositionTypeService;
// import com.colligendis.server.database.numista.service.techdata.MetalService;
// import com.colligendis.server.database.numista.service.techdata.ShapeService;
// import
// com.colligendis.server.database.numista.service.techdata.TechniqueService;

// import lombok.extern.slf4j.Slf4j;
// import reactor.core.publisher.Flux;
// import reactor.core.publisher.Mono;

// @Slf4j
// public class TechnicalDataParser {

// public static final TechnicalDataParser instance = new TechnicalDataParser();

// public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(()
// -> parseTechnicalData(page));

// private NTypeService nTypeService;

// private NTypeService getNTypeService() {
// if (nTypeService == null) {
// nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
// }
// return nTypeService;
// }

// private CompositionService compositionService;

// private CompositionService getCompositionService() {
// if (compositionService == null) {
// compositionService =
// N4JUtil.getInstance().numistaServices.compositionService;
// }
// return compositionService;
// }

// private CompositionTypeService compositionTypeService;

// private CompositionTypeService getCompositionTypeService() {
// if (compositionTypeService == null) {
// compositionTypeService =
// N4JUtil.getInstance().numistaServices.compositionTypeService;
// }
// return compositionTypeService;
// }

// private CollectibleTypeService collectibleTypeService;

// private CollectibleTypeService getCollectibleTypeService() {
// if (collectibleTypeService == null) {
// collectibleTypeService =
// N4JUtil.getInstance().numistaServices.collectibleTypeService;
// }
// return collectibleTypeService;
// }

// private MetalService metalService;

// private MetalService getMetalService() {
// if (metalService == null) {
// metalService = N4JUtil.getInstance().numistaServices.metalService;
// }
// return metalService;
// }

// private ShapeService shapeService;

// private ShapeService getShapeService() {
// if (shapeService == null) {
// shapeService = N4JUtil.getInstance().numistaServices.shapeService;
// }
// return shapeService;
// }

// private TechniqueService techniqueService;

// private TechniqueService getTechniqueService() {
// if (techniqueService == null) {
// techniqueService = N4JUtil.getInstance().numistaServices.techniqueService;
// }
// return techniqueService;
// }

// private Mono<NumistaPage> parseTechnicalData(NumistaPage numistaPage) {
// return Mono.just(numistaPage)
// .flatMap(this::parseComposition)
// .flatMap(this::parseShape)
// .flatMap(this::parseWeight)
// .flatMap(this::parseSize)
// .flatMap(this::parseThickness)
// .flatMap(this::parseTechniques)
// .flatMap(this::parseAlignment);
// }

// private Mono<NumistaPage> parseComposition(NumistaPage numistaPage) {
// if (numistaPage.nType == null || numistaPage.page == null ||
// numistaPage.colligendisUser == null) {
// return Mono.just(numistaPage);
// }

// Document page = numistaPage.page;
// HashMap<String, String> metalType =
// NumistaParseUtils.getAttributeWithTextSelectedOption(page, "#metal_type");

// Mono<Composition> compositionMono = metalType != null
// ? getOrCreateComposition(numistaPage.nType, numistaPage.colligendisUser)
// .flatMap(composition -> setCompositionType(Mono.just(composition),
// metalType.get("value"), metalType.get("text")))
// : getOrCreateComposition(numistaPage.nType, numistaPage.colligendisUser);

// return compositionMono
// .flatMap(
// composition -> applyCompositionParts(composition, metalType, page,
// numistaPage.colligendisUser))
// .flatMap(composition -> handleBanknoteComposition(composition, numistaPage))
// .flatMap(composition -> updateMetalDetails(composition, page, numistaPage))
// .thenReturn(numistaPage)
// .switchIfEmpty(Mono.just(numistaPage))
// .onErrorResume(err -> {
// log.error("Error parsing composition: {}", err.getMessage());
// return Mono.just(numistaPage);
// });
// }

// private Mono<Composition> getOrCreateComposition(NType nType,
// com.colligendis.server.database.ColligendisUser user) {
// return getNTypeService().getComposition(nType)
// .flatMap(either -> either.fold(
// error -> {
// if (error instanceof NotFoundError) {
// Composition composition = new Composition();
// return getCompositionService().create(composition, user)
// .flatMap(saveResult -> saveResult.fold(
// err -> Mono.<Composition>empty(),
// saved -> getNTypeService().setComposition(nType, saved, user)
// .thenReturn(saved)));
// }
// return Mono.empty();
// },
// Mono::just));
// }

// private Mono<Composition> applyCompositionParts(Composition composition,
// HashMap<String, String> metalType,
// Document page, ColligendisUser user) {
// if (metalType == null) {
// return Mono.just(composition);
// }
// String compositionTypeCode = metalType.get("value");
// Mono<Composition> result = Mono.just(composition);
// switch (compositionTypeCode) {
// case "plain" -> {
// CompositionMetalParsingData metalParsingData =
// parseCompositionMetal("#metal1", "#fineness1", page);
// composition.setPart1Type(CompositionPartType.material);
// composition.setPart1MetalFineness(metalParsingData.fineness());
// composition.setPart2Type(null);
// composition.setPart2MetalFineness(null);
// composition.setPart3Type(null);
// composition.setPart3MetalFineness(null);
// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);

// Mono<Metal> metal1Mono =
// getMetalService().findByNidOrNameOrCreate(metalParsingData.metalCode(),
// metalParsingData.metalName(), user).flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));
// result = Mono.zip(metal1Mono, updatedCompositionMono).flatMap(tuple -> {
// Metal metal1 = tuple.getT1();
// Composition updatedComposition = tuple.getT2();
// return getCompositionService().setPart1Metal(updatedComposition, metal1,
// user)
// .then(getCompositionService().removePart2Metal(updatedComposition, user))
// .then(getCompositionService().removePart3Metal(updatedComposition, user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });

// }
// case "plated" -> {
// CompositionMetalParsingData coreMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1", page);
// CompositionMetalParsingData platingMetalParsingData =
// parseCompositionMetal("#metal2", "#fineness2",
// page);

// composition.setPart1Type(CompositionPartType.core);
// composition.setPart1MetalFineness(coreMetalParsingData.fineness());
// Mono<Metal> coreMetalMono =
// getMetalService().findByNidOrNameOrCreate(coreMetalParsingData.metalCode(),
// coreMetalParsingData.metalName(), user).flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// composition.setPart2Type(CompositionPartType.plating);
// composition.setPart2MetalFineness(platingMetalParsingData.fineness());
// Mono<Metal> platingMetalMono = getMetalService()
// .findByNidOrNameOrCreate(platingMetalParsingData.metalCode(),
// platingMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// composition.setPart3Type(null);
// composition.setPart3MetalFineness(null);
// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);

// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));

// result = updatedCompositionMono.flatMap(updatedComposition -> {
// return Mono.zip(coreMetalMono, platingMetalMono).flatMap(tuple -> {
// Metal coreMetal = tuple.getT1();
// Metal platingMetal = tuple.getT2();
// return getCompositionService().setPart1Metal(updatedComposition, coreMetal,
// user)
// .then(getCompositionService().setPart2Metal(updatedComposition, platingMetal,
// user))
// .then(getCompositionService().removePart3Metal(updatedComposition, user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });

// });

// }
// case "clad" -> {
// CompositionMetalParsingData coreMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1", page);
// CompositionMetalParsingData cladMetalParsingData =
// parseCompositionMetal("#metal2", "#fineness2", page);

// composition.setPart1Type(CompositionPartType.core);
// composition.setPart1MetalFineness(coreMetalParsingData.fineness());
// Mono<Metal> coreMetalMono =
// getMetalService().findByNidOrNameOrCreate(coreMetalParsingData.metalCode(),
// coreMetalParsingData.metalName(), user).flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// composition.setPart2Type(CompositionPartType.clad);
// composition.setPart2MetalFineness(cladMetalParsingData.fineness());
// Mono<Metal> cladMetalMono =
// getMetalService().findByNidOrNameOrCreate(cladMetalParsingData.metalCode(),
// cladMetalParsingData.metalName(), user).flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// composition.setPart3Type(null);
// composition.setPart3MetalFineness(null);
// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);

// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));

// result = Mono.zip(coreMetalMono, cladMetalMono,
// updatedCompositionMono).flatMap(tuple -> {
// Metal coreMetal = tuple.getT1();
// Metal cladMetal = tuple.getT2();
// Composition updatedComposition = tuple.getT3();
// return getCompositionService().setPart1Metal(updatedComposition, coreMetal,
// user)
// .then(getCompositionService().setPart2Metal(updatedComposition, cladMetal,
// user))
// .then(getCompositionService().removePart3Metal(updatedComposition, user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });

// }
// case "bimetallic" -> {
// CompositionMetalParsingData centerMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1",
// page);
// CompositionMetalParsingData ringMetalParsingData =
// parseCompositionMetal("#metal2", "#fineness2", page);

// composition.setPart1Type(CompositionPartType.center);
// composition.setPart1MetalFineness(centerMetalParsingData.fineness());
// Mono<Metal> centerMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerMetalParsingData.metalCode(),
// centerMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// composition.setPart2Type(CompositionPartType.ring);
// composition.setPart2MetalFineness(ringMetalParsingData.fineness());
// Mono<Metal> ringMetalMono =
// getMetalService().findByNidOrNameOrCreate(ringMetalParsingData.metalCode(),
// ringMetalParsingData.metalName(), user).flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// composition.setPart3Type(null);
// composition.setPart3MetalFineness(null);
// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);

// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));
// result = Mono.zip(centerMetalMono, ringMetalMono,
// updatedCompositionMono).flatMap(tuple -> {
// Metal centerMetal = tuple.getT1();
// Metal ringMetal = tuple.getT2();
// Composition updatedComposition = tuple.getT3();
// return getCompositionService().setPart1Metal(updatedComposition, centerMetal,
// user)
// .then(getCompositionService().setPart2Metal(updatedComposition, ringMetal,
// user))
// .then(getCompositionService().removePart3Metal(updatedComposition, user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });

// }
// case "bimetallic_plated" -> {
// CompositionMetalParsingData centerCoreMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1",
// page);
// CompositionMetalParsingData centerPlatingMetalParsingData =
// parseCompositionMetal("#metal2",
// "#fineness2", page);
// CompositionMetalParsingData ringMetalParsingData =
// parseCompositionMetal("#metal3", "#fineness3", page);

// composition.setPart1Type(CompositionPartType.center_core);
// composition.setPart1MetalFineness(centerCoreMetalParsingData.fineness());
// Mono<Metal> centerCoreMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerCoreMetalParsingData.metalCode(),
// centerCoreMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart2Type(CompositionPartType.center_plating);
// composition.setPart2MetalFineness(centerPlatingMetalParsingData.fineness());
// Mono<Metal> centerPlatingMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerPlatingMetalParsingData.metalCode(),
// centerPlatingMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart3Type(CompositionPartType.ring);
// composition.setPart3MetalFineness(ringMetalParsingData.fineness());
// Mono<Metal> ringMetalMono =
// getMetalService().findByNidOrNameOrCreate(ringMetalParsingData.metalCode(),
// ringMetalParsingData.metalName(), user).flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);

// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));
// result = Mono.zip(centerCoreMetalMono, centerPlatingMetalMono, ringMetalMono,
// updatedCompositionMono)
// .flatMap(tuple -> {
// Metal centerCoreMetal = tuple.getT1();
// Metal centerPlatingMetal = tuple.getT2();
// Metal ringMetal = tuple.getT3();
// Composition updatedComposition = tuple.getT4();
// return getCompositionService().setPart1Metal(updatedComposition,
// centerCoreMetal, user)
// .then(getCompositionService().setPart2Metal(updatedComposition,
// centerPlatingMetal,
// user))
// .then(getCompositionService().setPart3Metal(updatedComposition, ringMetal,
// user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });

// }
// case "bimetallic_plated_ring" -> {
// CompositionMetalParsingData centerMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1",
// page);
// CompositionMetalParsingData ringCoreMetalParsingData =
// parseCompositionMetal("#metal2", "#fineness2",
// page);
// CompositionMetalParsingData ringPlatingMetalParsingData =
// parseCompositionMetal("#metal3", "#fineness3",
// page);

// composition.setPart1Type(CompositionPartType.center);
// composition.setPart1MetalFineness(centerMetalParsingData.fineness());
// Mono<Metal> centerMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerMetalParsingData.metalCode(),
// centerMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart2Type(CompositionPartType.ring_core);
// composition.setPart2MetalFineness(ringCoreMetalParsingData.fineness());
// Mono<Metal> ringCoreMetalMono = getMetalService()
// .findByNidOrNameOrCreate(ringCoreMetalParsingData.metalCode(),
// ringCoreMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart3Type(CompositionPartType.ring_plating);
// composition.setPart3MetalFineness(ringPlatingMetalParsingData.fineness());
// Mono<Metal> ringPlatingMetalMono = getMetalService()
// .findByNidOrNameOrCreate(ringPlatingMetalParsingData.metalCode(),
// ringPlatingMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);

// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));
// result = Mono.zip(centerMetalMono, ringCoreMetalMono, ringPlatingMetalMono,
// updatedCompositionMono)
// .flatMap(tuple -> {
// Metal centerMetal = tuple.getT1();
// Metal ringCoreMetal = tuple.getT2();
// Metal ringPlatingMetal = tuple.getT3();
// Composition updatedComposition = tuple.getT4();
// return getCompositionService().setPart1Metal(updatedComposition, centerMetal,
// user)
// .then(getCompositionService().setPart2Metal(updatedComposition,
// ringCoreMetal,
// user))
// .then(getCompositionService().setPart3Metal(updatedComposition,
// ringPlatingMetal,
// user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });
// }
// case "bimetallic_plated_plated" -> {
// CompositionMetalParsingData centerCoreMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1",
// page);
// CompositionMetalParsingData centerPlatingMetalParsingData =
// parseCompositionMetal("#metal2",
// "#fineness2", page);
// CompositionMetalParsingData ringCoreMetalParsingData =
// parseCompositionMetal("#metal3", "#fineness3",
// page);
// CompositionMetalParsingData ringPlatingMetalParsingData =
// parseCompositionMetal("#metal4", "#fineness4",
// page);

// composition.setPart1Type(CompositionPartType.center_core);
// composition.setPart1MetalFineness(centerCoreMetalParsingData.fineness());
// Mono<Metal> centerCoreMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerCoreMetalParsingData.metalCode(),
// centerCoreMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart2Type(CompositionPartType.center_plating);
// composition.setPart2MetalFineness(centerPlatingMetalParsingData.fineness());
// Mono<Metal> centerPlatingMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerPlatingMetalParsingData.metalCode(),
// centerPlatingMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart3Type(CompositionPartType.ring_core);
// composition.setPart3MetalFineness(ringCoreMetalParsingData.fineness());
// Mono<Metal> ringCoreMetalMono = getMetalService()
// .findByNidOrNameOrCreate(ringCoreMetalParsingData.metalCode(),
// ringCoreMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart4Type(CompositionPartType.ring_plating);
// composition.setPart4MetalFineness(ringPlatingMetalParsingData.fineness());
// Mono<Metal> ringPlatingMetalMono = getMetalService()
// .findByNidOrNameOrCreate(ringPlatingMetalParsingData.metalCode(),
// ringPlatingMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));
// result = Mono
// .zip(centerCoreMetalMono, centerPlatingMetalMono, ringCoreMetalMono,
// ringPlatingMetalMono,
// updatedCompositionMono)
// .flatMap(tuple -> {
// Metal centerCoreMetal = tuple.getT1();
// Metal centerPlatingMetal = tuple.getT2();
// Metal ringCoreMetal = tuple.getT3();
// Metal ringPlatingMetal = tuple.getT4();
// Composition updatedComposition = tuple.getT5();
// return getCompositionService().setPart1Metal(updatedComposition,
// centerCoreMetal, user)
// .then(getCompositionService().setPart2Metal(updatedComposition,
// centerPlatingMetal,
// user))
// .then(getCompositionService().setPart3Metal(updatedComposition,
// ringCoreMetal,
// user))
// .then(getCompositionService().setPart4Metal(updatedComposition,
// ringPlatingMetal,
// user))
// .thenReturn(updatedComposition);
// });

// }
// case "bimetallic_clad" -> {
// CompositionMetalParsingData centerCoreMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1",
// page);
// CompositionMetalParsingData centerCladMetalParsingData =
// parseCompositionMetal("#metal2", "#fineness2",
// page);
// CompositionMetalParsingData ringMetalParsingData =
// parseCompositionMetal("#metal3", "#fineness3", page);

// composition.setPart1Type(CompositionPartType.center_core);
// composition.setPart1MetalFineness(centerCoreMetalParsingData.fineness());
// Mono<Metal> centerCoreMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerCoreMetalParsingData.metalCode(),
// centerCoreMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart2Type(CompositionPartType.center_clad);
// composition.setPart2MetalFineness(centerCladMetalParsingData.fineness());
// Mono<Metal> centerCladMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerCladMetalParsingData.metalCode(),
// centerCladMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart3Type(CompositionPartType.ring);
// composition.setPart3MetalFineness(ringMetalParsingData.fineness());
// Mono<Metal> ringMetalMono =
// getMetalService().findByNidOrNameOrCreate(ringMetalParsingData.metalCode(),
// ringMetalParsingData.metalName(), user).flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));

// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);

// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));
// result = Mono.zip(centerCoreMetalMono, centerCladMetalMono, ringMetalMono,
// updatedCompositionMono)
// .flatMap(tuple -> {
// Metal centerCoreMetal = tuple.getT1();
// Metal centerCladMetal = tuple.getT2();
// Metal ringMetal = tuple.getT3();
// Composition updatedComposition = tuple.getT4();
// return getCompositionService().setPart1Metal(updatedComposition,
// centerCoreMetal, user)
// .then(getCompositionService().setPart2Metal(updatedComposition,
// centerCladMetal,
// user))
// .then(getCompositionService().setPart3Metal(updatedComposition, ringMetal,
// user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });
// }
// case "trimetallic" -> {
// CompositionMetalParsingData centerMetalParsingData =
// parseCompositionMetal("#metal1", "#fineness1",
// page);
// CompositionMetalParsingData middleRingMetalParsingData =
// parseCompositionMetal("#metal2", "#fineness2",
// page);
// CompositionMetalParsingData outerRingMetalParsingData =
// parseCompositionMetal("#metal3", "#fineness3",
// page);

// composition.setPart1Type(CompositionPartType.center);
// composition.setPart1MetalFineness(centerMetalParsingData.fineness());
// Mono<Metal> centerMetalMono = getMetalService()
// .findByNidOrNameOrCreate(centerMetalParsingData.metalCode(),
// centerMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart2Type(CompositionPartType.middle_ring);
// composition.setPart2MetalFineness(middleRingMetalParsingData.fineness());
// Mono<Metal> middleRingMetalMono = getMetalService()
// .findByNidOrNameOrCreate(middleRingMetalParsingData.metalCode(),
// middleRingMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart3Type(CompositionPartType.outer_ring);
// composition.setPart3MetalFineness(outerRingMetalParsingData.fineness());
// Mono<Metal> outerRingMetalMono = getMetalService()
// .findByNidOrNameOrCreate(outerRingMetalParsingData.metalCode(),
// outerRingMetalParsingData.metalName(), user)
// .flatMap(
// metalEither -> metalEither.fold(
// metalError -> Mono.empty(),
// metal -> Mono.just(metal)));
// composition.setPart4Type(null);
// composition.setPart4MetalFineness(null);
// Mono<Composition> updatedCompositionMono =
// getCompositionService().update(composition, user)
// .flatMap(updateResult -> updateResult.fold(
// updateError -> Mono.empty(),
// updatedComposition -> {
// return Mono.just(updatedComposition);
// }));
// result = Mono.zip(centerMetalMono, middleRingMetalMono, outerRingMetalMono,
// updatedCompositionMono)
// .flatMap(tuple -> {
// Metal centerMetal = tuple.getT1();
// Metal middleRingMetal = tuple.getT2();
// Metal outerRingMetal = tuple.getT3();
// Composition updatedComposition = tuple.getT4();
// return getCompositionService().setPart1Metal(updatedComposition, centerMetal,
// user)
// .then(getCompositionService().setPart2Metal(updatedComposition,
// middleRingMetal,
// user))
// .then(getCompositionService().setPart3Metal(updatedComposition,
// outerRingMetal,
// user))
// .then(getCompositionService().removePart4Metal(updatedComposition, user))
// .thenReturn(updatedComposition);
// });
// }
// default -> {
// /* no specific parts */ }
// }
// return result;
// }

// private Mono<Composition> handleBanknoteComposition(Composition composition,
// NumistaPage numistaPage) {
// if (numistaPage.collectibleType == null) {
// return Mono.just(composition);
// }
// return
// getCollectibleTypeService().findTopCollectibleType(numistaPage.collectibleType)
// .flatMap(topEither -> topEither.fold(
// err -> Mono.just(composition),
// topType -> {
// if (!CollectibleType.BANKNOTES_CODE.equals(topType.getCode())) {
// return Mono.just(composition);
// }
// HashMap<String, String> compositionHashMap = NumistaParseUtils
// .getAttributeWithTextSelectedOption(numistaPage.page, "#metal1");
// if (compositionHashMap == null ||
// !isValueAndTextNotNullAndNotEmpty(compositionHashMap)) {
// return Mono.just(composition);
// }
// String compositionTypeCode = compositionHashMap.get("value");
// String compositionTypeName = compositionHashMap.get("text");
// return setCompositionType(Mono.just(composition), compositionTypeCode,
// compositionTypeName);
// }));
// }

// private Mono<Composition> updateMetalDetails(Composition composition,
// Document page, NumistaPage numistaPage) {
// String metalDetails =
// NumistaParseUtils.getAttribute(page.selectFirst("#metal_details"), "value");
// if (metalDetails == null || metalDetails.isEmpty()) {
// return Mono.just(composition);
// }
// if (getCompositionService().compareCompositionAdditionalDetails(composition,
// metalDetails)) {
// return Mono.just(composition);
// }
// return getCompositionService()
// .setCompositionAdditionalDetails(composition, metalDetails,
// numistaPage.colligendisUser)
// .thenReturn(composition);
// }

// private static boolean isValueAndTextNotNullAndNotEmpty(HashMap<String,
// String> map) {
// return map != null && StringUtils.isNotBlank(map.get("value")) &&
// StringUtils.isNotBlank(map.get("text"));
// }

// public Mono<Composition> setCompositionType(Mono<Composition>
// compositionMono, String compositionTypeCode,
// String compositionTypeName) {
// return compositionMono.flatMap(composition ->
// getCompositionService().findCompositionType(composition)
// .flatMap(foundCompositionTypeResult -> foundCompositionTypeResult.fold(
// error -> {
// if (error instanceof NotFoundError) {
// return getCompositionTypeService()
// .findByCodeOrCreate(compositionTypeCode, compositionTypeName, null)
// .flatMap(compositionTypeEither -> compositionTypeEither.fold(
// saveError -> {
// return Mono.empty();
// },
// compositionType -> {
// return getCompositionService()
// .setCompositionType(composition, compositionType, null)
// .thenReturn(composition);
// }));
// }
// return Mono.empty();
// },
// foundCompositionType -> {
// if (foundCompositionType.getCode().equals(compositionTypeCode)) {
// return Mono.just(composition);
// }
// return getCompositionTypeService()
// .findByCodeOrCreate(compositionTypeCode, compositionTypeName, null)
// .flatMap(compositionTypeEither -> compositionTypeEither.fold(
// error -> {
// return Mono.empty();
// },
// compositionType -> {
// return getCompositionService()
// .setCompositionType(composition, compositionType, null)
// .thenReturn(composition);
// }));
// })));

// }

// private static CompositionMetalParsingData parseCompositionMetal(String
// metalId, String finenessId,
// Document document) {

// String metalCode = "";
// String metalName = "Unknown";
// String fineness = "";

// Element metalElement = document.selectFirst(metalId);
// if (metalElement != null) {
// Elements metalsElements = metalElement.select("option");
// for (Element metal : metalsElements) {
// if (metal.attributes().get("selected").equals("selected")) {
// metalCode = metal.attributes().get("value");
// metalName = metal.text();
// }
// }
// }

// Element fineness1Element = document.selectFirst(finenessId);
// if (fineness1Element != null) {
// // pattern="[0-9]{1,3}(\.[0-9]+)?"
// fineness = fineness1Element.attributes().get("value");
// }

// return new CompositionMetalParsingData(metalCode, metalName, fineness);
// }

// private Mono<NumistaPage> parseShape(NumistaPage numistaPage) {
// return Mono.just(numistaPage).flatMap(np -> {
// HashMap<String, String> shape =
// NumistaParseUtils.getAttributeWithTextSelectedOption(np.page,
// "#shape");
// if (shape != null && isValueAndTextNotNullAndNotEmpty(shape)) {
// String shapeNid = shape.get("value");
// String shapeName = shape.get("text");
// return getShapeService().findByNidOrCreate(shapeNid, shapeName,
// np.colligendisUser)
// .flatMap(shapeEither -> shapeEither.fold(
// error -> Mono.just(np),
// foundShape -> getNTypeService()
// .setShape(np.nType, foundShape, np.colligendisUser)
// .thenReturn(np)));
// }
// return Mono.just(np);
// }).flatMap(np -> {
// String shapeDetails =
// NumistaParseUtils.getAttribute(np.page.selectFirst("#shape_details"),
// "value");
// if (shapeDetails != null && !shapeDetails.isEmpty()) {
// return getNTypeService()
// .setShapeAdditionalDetails(np.nType, shapeDetails, np.colligendisUser)
// .thenReturn(np);
// }
// return Mono.just(np);
// });
// }

// private Mono<NumistaPage> parseWeight(NumistaPage numistaPage) {
// return Mono.just(numistaPage).flatMap(np -> {
// String poids = NumistaParseUtils.getAttribute(np.page.selectFirst("#poids"),
// "value");
// if (poids != null && !poids.isEmpty()) {
// return getNTypeService().setWeight(np.nType, Float.parseFloat(poids),
// np.colligendisUser)
// .thenReturn(np);
// }
// return Mono.just(np);
// });
// }

// private Mono<NumistaPage> parseSize(NumistaPage numistaPage) {
// return Mono.just(numistaPage).flatMap(np -> {
// String dimension =
// NumistaParseUtils.getAttribute(np.page.selectFirst("#dimension"), "value");
// if (dimension != null && !dimension.isEmpty()) {
// return getNTypeService().setSize(np.nType, Float.parseFloat(dimension),
// np.colligendisUser)
// .thenReturn(np);
// }
// return Mono.just(np);
// }).flatMap(np -> {
// // Second dimension
// // <option value="42">Klippe</option>
// // <option value="50">Other</option>
// // <option value="36">Oval</option>
// // <option value="37">Oval with a loop</option>
// // <option value="4">Rectangular</option>
// // <option value="46">Rectangular (irregular)</option>
// // <option value="75">Sculptural</option>
// // <option value="54">Spade</option>
// // <option value="44">Square</option>
// // <option value="41">Square (irregular)</option>
// // <option value="55">Square with angled corners</option>
// // <option value="40">Square with rounded corners</option>
// // <option value="71">Square with scalloped edges</option>

// // <option value="">Unknown</option>
// // <option value="150">Other</option>
// // <option value="100" selected="selected">Rectangular</option>
// // <option value="102">Rectangular (hand cut)</option>
// // <option value="101">Rectangular with undulating edge</option>
// // <option value="105">Square</option>

// return getNTypeService().getShape(np.nType).flatMap(shapeEither ->
// shapeEither.fold(
// error -> Mono.just(np),
// shape -> {
// List<String> shapeCodes = Arrays.asList("", "4", "36", "37", "40", "41",
// "42", "44", "46", "50",
// "54", "55", "71", "75", "100", "101", "102", "105", "150");
// if (shapeCodes.contains(shape.getNid())) {
// String dimension2 = NumistaParseUtils
// .getAttribute(np.page.selectFirst("input[name=dimension2]"), "value");
// if (dimension2 != null && !dimension2.isEmpty()) {
// return getNTypeService()
// .setSize2(np.nType, Float.parseFloat(dimension2), np.colligendisUser)
// .thenReturn(np);
// }
// }
// return Mono.just(np);
// }));
// });
// }

// private Mono<NumistaPage> parseThickness(NumistaPage numistaPage) {
// return Mono.just(numistaPage).flatMap(np -> {
// String epaisseur =
// NumistaParseUtils.getAttribute(np.page.selectFirst("#epaisseur"), "value");
// if (epaisseur != null && !epaisseur.isEmpty()) {
// return getNTypeService().setThickness(np.nType, Float.parseFloat(epaisseur),
// np.colligendisUser)
// .thenReturn(np);
// }
// return Mono.just(np);
// });
// }

// private Mono<NumistaPage> parseTechniques(NumistaPage numistaPage) {
// return Mono.just(numistaPage).flatMap(np -> {
// List<HashMap<String, String>> techniques =
// NumistaParseUtils.getAttributesWithTextSelectedOptions(
// np.page.selectFirst("#techniques"));

// if (techniques != null && !techniques.isEmpty()) {
// return Flux.fromIterable(techniques)
// .flatMap(t -> getTechniqueService().findByNidOrCreate(t.get("value"),
// t.get("text"),
// np.colligendisUser))
// .flatMap(techniqueEither -> techniqueEither.fold(
// error -> Mono.<Technique>empty(),
// technique -> Mono.just(technique)))
// .collectList()
// .flatMap(techniqueList -> getNTypeService()
// .setTechniques(np.nType, techniqueList, np.colligendisUser)
// .thenReturn(np));
// }
// return Mono.just(np);
// }).flatMap(np -> {
// // Technique Additional details
// String techniqueDetail =
// NumistaParseUtils.getAttribute(np.page.selectFirst("#technique_details"),
// "value");
// if (techniqueDetail != null && !techniqueDetail.isEmpty()) {
// return getNTypeService().setTechniqueAdditionalDetails(np.nType,
// techniqueDetail, np.colligendisUser)
// .thenReturn(np);
// }
// return Mono.just(np);
// });
// }

// private Mono<NumistaPage> parseAlignment(NumistaPage numistaPage) {

// return Mono.just(numistaPage).flatMap(np -> {
// String alignementCode = NumistaParseUtils
// .getAttribute(np.page.selectFirst("input[name=alignement][checked=checked]"),
// "value");
// if (alignementCode != null && !alignementCode.isEmpty()) {
// return getNTypeService().setAlignment(np.nType, alignementCode,
// np.colligendisUser)
// .thenReturn(np);
// }
// return Mono.just(np);
// });
// }

// }
