package com.colligendis.server.parser.numista;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.N4JUtil;
import com.colligendis.server.database.exception.DatabaseException;
import com.colligendis.server.database.exception.NotFoundError;
import com.colligendis.server.database.numista.service.CatalogueService;
import com.colligendis.server.database.numista.service.NTypeService;
import com.colligendis.server.parser.PauseLock;
import com.google.gson.Gson;
import com.colligendis.server.database.numista.model.Catalogue;
import com.colligendis.server.database.numista.model.CatalogueReference;
import com.colligendis.server.database.numista.service.CatalogueReferenceService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReferenceNumberParser {
	public static final ReferenceNumberParser instance = new ReferenceNumberParser();

	private static final String ANSI_BLUE = "\u001B[34m";
	private static final String ANSI_RED = "\u001B[31m";
	private static final String ANSI_RESET = "\u001B[0m";

	private CatalogueService catalogueService;

	private CatalogueService getCatalogueService() {
		if (catalogueService == null) {
			catalogueService = N4JUtil.getInstance().numistaServices.catalogueService;
		}
		return catalogueService;
	}

	private CatalogueReferenceService catalogueReferenceService;

	private CatalogueReferenceService getCatalogueReferenceService() {
		if (catalogueReferenceService == null) {
			catalogueReferenceService = N4JUtil.getInstance().numistaServices.catalogueReferenceService;
		}
		return catalogueReferenceService;
	}

	private NTypeService nTypeService;

	private NTypeService getNTypeService() {
		if (nTypeService == null) {
			nTypeService = N4JUtil.getInstance().numistaServices.nTypeService;
		}
		return nTypeService;
	}

	private Map<String, PauseLock> pauseLockMap = new HashMap<>();

	public Function<NumistaPage, Mono<NumistaPage>> parse = page -> Mono.defer(() -> parseReferenceNumber(page));

	private Mono<NumistaPage> parseReferenceNumber(NumistaPage numistaPage) {

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
				.flatMap(catalogueReferences -> linkCatalogueReferencesToNType(catalogueReferences, numistaPage))
				.thenReturn(numistaPage);
	}

	private Mono<Void> linkCatalogueReferencesToNType(List<CatalogueReference> catalogueReferences,
			NumistaPage numistaPage) {
		if (numistaPage.nType == null || catalogueReferences.isEmpty()) {
			return Mono.empty();
		}

		return getNTypeService()
				.setCatalogueReferences(numistaPage.nType, catalogueReferences, numistaPage.colligendisUser)
				.then(Mono.empty());
	}

	private Mono<CatalogueReference> processReference(
			ReferenceToCatalogue ref, NumistaPage numistaPage) {
		// Step 1: Try to find existing catalogue reference
		return getCatalogueReferenceService().findByNumberAndCatalogueCode(ref.number, ref.catalogueCode)
				.flatMap(catalogueReferenceResult -> catalogueReferenceResult.fold(
						// Not found, proceed to create
						error -> {
							if (error instanceof NotFoundError) {
								return getCatalogueService().findByCode(ref.catalogueCode)
										.flatMap(catalogueEither -> catalogueEither.fold(
												catalogueError -> {
													if (catalogueError instanceof NotFoundError) {
														if (log.isErrorEnabled()) {
															log.error(
																	"nid: {}{}{} - Error finding catalogue while 'handleEntityNotFound' : catalogue code: {}{}{} - error: {}{}{}",
																	ANSI_BLUE, numistaPage.nid, ANSI_RESET,
																	ANSI_BLUE, ref.catalogueCode, ANSI_RESET,
																	ANSI_RED, error.message(), ANSI_RESET);
														}
													}
													return Mono.empty();
												},
												catalogue -> getCatalogueReferenceService()
														.create(ref.number, catalogue, numistaPage.colligendisUser)
														.flatMap(createResult -> createResult.fold(
																createError -> Mono.empty(),
																Mono::just))));
							}
							return Mono.empty();
						},
						// Found, return it
						catalogueReference -> Mono.just(catalogueReference)));
	}

	public static final String CATALOGUE_SEARCH = "https://en.numista.com/catalogue/search_catalogues.php?q=";

	/*
	 * References
	 * Last update: 26 September 2024
	 * 
	 * The field specifies the alphanumeric code that identifies the coin type in a
	 * reference catalogue. Up to ten references can be specified (if some reference
	 * catalogues are missing, you can request their addition to the database). If
	 * more references exist for a coin, the additional ones may be added in the
	 * comments section.
	 * 
	 * Order of references
	 * When possible, the same sequence of references should be used for all the
	 * coin types of an issuer. Newer, more authoritative, and more exhaustive
	 * standard references should appear first.
	 * 
	 * What if same entry on Numista is linked to multiple reference numbers in the
	 * same catalogue?
	 * If a coin type has more than one code in a reference catalogue, always add
	 * the first one (or the most relevant). The same reference catalogue may be
	 * added in a new line for recording multiple codes, provided you did not reach
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

	public Mono<Boolean> parseCataloguesByCode(String code, ColligendisUser colligendisUser) {
		if (code == null || code.isEmpty()) {
			return Mono.just(false);
		}
		final String normalizedCode = Normalizer.normalize(code, Normalizer.Form.NFD)
				.replaceAll("[^\\p{ASCII}]", "")
				.replace(" ", "%20");

		String url = CATALOGUE_SEARCH + normalizedCode;
		return Mono.fromCallable(() -> NumistaParseUtils.loadPageByURL(url))
				.flatMap(doc -> {
					if (doc == null) {
						log.error("Can't load PHP CatalogueSearch from URL: {}", url);
						return Mono.just(false);
					}

					Element body = doc.selectFirst("body");
					String jsonRefs = body.text().replace("\\r\\n", "").replace("<\\/em>", "");
					Gson gson = new Gson();
					ReferencedCatalogue[] referencedCatalogues = gson.fromJson(jsonRefs, ReferencedCatalogue[].class);
					return processCatalogues(referencedCatalogues, colligendisUser);
				});
	}

	private Mono<Boolean> processCatalogues(ReferencedCatalogue[] referencedCatalogues, ColligendisUser user) {
		return Flux.fromArray(referencedCatalogues)
				.flatMap(referencedCatalogue -> {
					return getCatalogueService().findByNidWithSave(referencedCatalogue.id, referencedCatalogue.text,
							user);
				})
				.all(catalogue -> catalogue.isRight());
	}

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