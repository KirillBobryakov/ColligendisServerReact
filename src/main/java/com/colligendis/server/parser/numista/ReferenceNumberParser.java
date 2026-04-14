package com.colligendis.server.parser.numista;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.colligendis.server.database.numista.service.CatalogueService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.parser.numista.exception.ParserException;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.service.CatalogueReferenceService;

@Component
@RequiredArgsConstructor
public class ReferenceNumberParser extends Parser {

	private final CatalogueService catalogueService;
	private final CatalogueReferenceService catalogueReferenceService;
	private final NTypeService nTypeService;

	@Override
	protected Mono<NumistaPage> parse(NumistaPage numistaPage) {
		return Mono.defer(() -> {
			List<ReferenceToCatalogue> references = new ArrayList<>();

			Elements referenceInputs = numistaPage.page.select("div[class=reference_input]");

			for (Element element : referenceInputs) {
				Element catalogueElement = element.selectFirst("option");
				Element numberElement = element.selectFirst("input");

				if (catalogueElement == null || numberElement == null)
					continue;

				if (!numberElement.attr("value").isEmpty()) {
					ReferenceToCatalogue referenceToCatalogue = new ReferenceToCatalogue(
							catalogueElement.attr("value"),
							catalogueElement.text(),
							numberElement.attr("value"));

					references.add(referenceToCatalogue);
				}
			}

			if (references.isEmpty()) {
				return Mono.just(numistaPage);
			}

			return Flux.fromIterable(references)
					.flatMap(ref -> processReference(ref, numistaPage))
					.collectList()
					.flatMap(catalogueReferences -> {

						if (catalogueReferences.isEmpty()) {
							numistaPage.getPipelineStepLogger()
									.info("Catalogue references is empty while linking to NType");
							return Mono.just(numistaPage);
						}

						return nTypeService.setCatalogueReferences(numistaPage.nType, catalogueReferences,
								numistaPage.getNumistaParserUserMono(), numistaPage.getPipelineStepLogger())
								.flatMap(executionResult -> {
									if (executionResult.getStatus()
											.equals(CreateRelationshipExecutionStatus.WAS_CREATED)
											|| executionResult.getStatus()
													.equals(CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS)) {

										numistaPage.getPipelineStepLogger()
												.info("Catalogue references linked to NType: {}",
														numistaPage.nType.nid);
										return Mono.just(numistaPage);
									} else {

										return Mono.<NumistaPage>error(
												new ParserException("Failed to link catalogue references to NType: "
														+ numistaPage.nType.nid));
									}
								});
					});
		});
	}

	private Mono<CatalogueReference> processReference(
			ReferenceToCatalogue ref, NumistaPage numistaPage) {
		// Step 1: Try to find existing catalogue reference
		return catalogueReferenceService.findByNumberAndCatalogueCode(ref.number,
				ref.catalogueCode, numistaPage.getPipelineStepLogger())
				.flatMap(crExecutionResult -> {
					if (crExecutionResult.getStatus().equals(FindExecutionStatus.NOT_FOUND)) {
						return catalogueService.findByCode(ref.catalogueCode, numistaPage.getPipelineStepLogger())
								.flatMap(catalogueExecutionResult -> {
									if (catalogueExecutionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
										return catalogueReferenceService.create(ref.number,
												catalogueExecutionResult.getNode(),
												numistaPage.getNumistaParserUserMono(),
												numistaPage.getPipelineStepLogger())
												.flatMap(createExecutionResult -> {
													if (createExecutionResult.getStatus()
															.equals(CreateNodeExecutionStatus.WAS_CREATED)) {
														return Mono.just(
																(CatalogueReference) createExecutionResult.getNode());
													}
													return Mono.<CatalogueReference>empty();
												});
									} else {
										numistaPage.getPipelineStepLogger()
												.error("Failed to find catalogue by code: {}", ref.catalogueCode);
										return Mono.<CatalogueReference>empty();
									}
								});
					} else if (crExecutionResult.getStatus().equals(FindExecutionStatus.FOUND)) {
						return Mono.just(crExecutionResult.getNode());
					} else {
						return Mono.<CatalogueReference>empty();
					}
				});

	}

	public static final String CATALOGUE_SEARCH = "https://en.numista.com/catalogue/search_catalogues.php?q=";

	/*
	 * References
	 * Last update: 26 September 2024
	 *
	 * The field specifies the alphanumeric code that identifies the coin type in
	 * a
	 * reference catalogue. Up to ten references can be specified (if some
	 * reference
	 * catalogues are missing, you can request their addition to the database). If
	 * more references exist for a coin, the additional ones may be added in the
	 * comments section.
	 *
	 * Order of references
	 * When possible, the same sequence of references should be used for all the
	 * coin types of an issuer. Newer, more authoritative, and more exhaustive
	 * standard references should appear first.
	 *
	 * What if same entry on Numista is linked to multiple reference numbers in
	 * the
	 * same catalogue?
	 * If a coin type has more than one code in a reference catalogue, always add
	 * the first one (or the most relevant). The same reference catalogue may be
	 * added in a new line for recording multiple codes, provided you did not
	 * reach
	 * the maximum number of references.
	 *
	 * Missing coin in a catalogue
	 * If only a small number of coins are missing from a standard monograph
	 * (reference catalogue dedicated to a very specific topic) that is used
	 * consistently for an issuer, then an en dash (“–”) can be used instead of a
	 * reference code, to show that the coins are unlisted, for instance this
	 * unlisted 20 Batzen coin in the Hofer catalogue. All other coins of the
	 * Helvetic Republic have a Hofer reference.
	 *
	 * Similar reference
	 * If a coin type is absent from a catalog, the similarity to another type in
	 * that catalog can be indicated by “var.” after the code.
	 */

	// public Mono<Boolean> parseCataloguesByCode(String code, ColligendisUser
	// colligendisUser) {
	// if (code == null || code.isEmpty()) {
	// return Mono.just(false);
	// }
	// final String normalizedCode = Normalizer.normalize(code, Normalizer.Form.NFD)
	// .replaceAll("[^\\p{ASCII}]", "")
	// .replace(" ", "%20");

	// String url = CATALOGUE_SEARCH + normalizedCode;
	// return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
	// .flatMap(doc -> {
	// if (doc == null) {
	// log.error("Can't load PHP CatalogueSearch from URL: {}", url);
	// return Mono.just(false);
	// }

	// Element body = doc.selectFirst("body");
	// String jsonRefs = body.text().replace("\\r\\n", "").replace("<\\/em>", "");
	// Gson gson = new Gson();
	// ReferencedCatalogue[] referencedCatalogues = gson.fromJson(jsonRefs,
	// ReferencedCatalogue[].class);
	// return processCatalogues(referencedCatalogues, colligendisUser);
	// });
	// }

	// private Mono<Boolean> processCatalogues(ReferencedCatalogue[]
	// referencedCatalogues, ColligendisUser user) {
	// return Flux.fromArray(referencedCatalogues)
	// .flatMap(referencedCatalogue -> {
	// return getCatalogueService().findByNidWithSave(referencedCatalogue.id,
	// referencedCatalogue.text,
	// user);
	// })
	// .all(catalogue -> catalogue.isRight());
	// }

}

@Data
@AllArgsConstructor
class ReferenceToCatalogue {
	String catalogueNid;
	String catalogueCode;
	String number;

}

@Data
@AllArgsConstructor
class ReferencedCatalogue {
	String id;
	String text;
	String bibliography;
}