package com.colligendis.server.parser.numista;

import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.colligendis.server.database.ExecutionStatus;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.database.numista.service.CollectibleTypeService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.numista.exception.ParserException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CollectibleTypeParser extends Parser {

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return parseCollectibleType(numistaPage);
	}

	private final CollectibleTypeService collectibleTypeService;
	private final NTypeService nTypeService;

	/**
	 * Parse the collectible type from the Numista page.
	 * All collectable types are loaded in the database by CollectibleTypeTreeParser
	 * 
	 * @param numistaPage
	 * @return
	 */
	private Mono<NumistaPage> parseCollectibleType(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			Element collectibleSubtype = numistaPage.page.selectFirst("#collectible_type");

			if (collectibleSubtype == null) {
				numistaPage.getPipelineStepLogger().error(
						"CollectibleType not found for nid: {} - #collectible_type tag not found on page",
						numistaPage.nid);
				return Mono.error(new ParserException("Collectible type not found for nid: " + numistaPage.nid));
			}

			Element typeElement = collectibleSubtype.select("option").stream()
					.filter(e -> e.hasAttr("selected")
							&& !"Unknown".equals(e.text()))
					.findFirst().orElse(null);

			if (typeElement == null) {
				numistaPage.getPipelineStepLogger().error(
						"CollectibleType element is null for nid: {} - Element <option> while parsing collectible type is null on page",
						numistaPage.nid);
				return Mono.error(new ParserException("Collectible type element is null for nid: " + numistaPage.nid));
			}

			String collectibleTypeCode = NumistaParseUtils.getAttribute(typeElement, "value");

			return collectibleTypeService.findByCode(collectibleTypeCode, numistaPage.getPipelineStepLogger())
					.flatMap(executionResult -> {
						if (executionResult.getStatus().equals(ExecutionStatus.NODE_IS_FOUND)) {
							return Mono.just(executionResult.getNode());
						} else {
							numistaPage.getPipelineStepLogger().error("Failed to find CollectibleType: {}",
									executionResult.getStatus());
							executionResult.logError(numistaPage.getPipelineStepLogger());
							return Mono.error(
									new ParserException("Failed to find CollectibleType: " + collectibleTypeCode));
						}
					})
					.flatMap(foundCollectibleType -> linkCollectibleTypeToNType(foundCollectibleType,
							collectibleTypeCode,
							numistaPage));
		});

	}

	/**
	 * Link the collectible type to the NType and update the parsing status
	 * 
	 * @param collectibleType
	 * @param collectibleTypeCode
	 * @param numistaPage
	 * @return
	 */
	private Mono<NumistaPage> linkCollectibleTypeToNType(CollectibleType collectibleType, String collectibleTypeCode,
			NumistaPage numistaPage) {

		return nTypeService.setCollectibleType(numistaPage.nType, collectibleType,
				numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
				.flatMap(executionResult -> {
					if (executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_IS_EXISTS)) {
						numistaPage.getPipelineStepLogger().info(
								"CollectibleType: {}", collectibleType.getName());
						numistaPage.collectibleType = collectibleType;
						return Mono.just(numistaPage);
					}
					if (!executionResult.getStatus().equals(ExecutionStatus.RELATIONSHIP_WAS_CREATED)) {
						numistaPage.getPipelineStepLogger().error(
								"CollectibleType error while setting relationship between NType and CollectibleType for nid: {} and collectible type name: {} - Error: {}",
								numistaPage.nid,
								collectibleType.getName(),
								executionResult.getStatus());
						return Mono.error(new ParserException("Error setting CollectibleType: " + collectibleTypeCode));
					}

					numistaPage.getPipelineStepLogger().warning(
							"CollectibleType: set to {}",
							collectibleType.getName());

					numistaPage.collectibleType = collectibleType;

					return Mono.just(numistaPage);
				});
	}

}
