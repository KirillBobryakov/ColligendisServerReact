package com.colligendis.server.controller;

import com.colligendis.server.controller.CountryController.CountryResponse;
import com.colligendis.server.controller.IssuerController.IssuerResponse;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.CollectibleType;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser;
import com.colligendis.server.parser.numista.catalogue.CatalogueParser.CatalogueParseResult;
import com.colligendis.server.service.CatalogueNtypesService;
import com.colligendis.server.dto.MarkResponse;
import com.colligendis.server.dto.NumistaCollectionItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/public/catalogue")
@RequiredArgsConstructor
public class CatalogueController {

	private final CatalogueParser catalogueParser;
	private final ColligendisUserService colligendisUserService;
	private final CatalogueNtypesService catalogueNtypesService;
	private final BaseLogger baseLogger = new BaseLogger();

	@GetMapping("/parse")
	public Mono<ResponseEntity<CatalogueParseResult>> parseByIssuerNumistaCode(
			@RequestParam String issuerNumistaCode,
			@RequestParam(name = "collectibleTypeCode", required = false, defaultValue = CollectibleType.COINS_CODE) String collectibleTypeCode) {
		log.info("Request to parse catalogue by issuerNumistaCode={}, collectibleTypeCode={}",
				issuerNumistaCode, collectibleTypeCode);
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.map(user -> catalogueParser.parse(issuerNumistaCode, collectibleTypeCode, false, user))
				.map(ResponseEntity::ok)
				.onErrorResume(this::parseErrorResponse);
	}

	@GetMapping("/ntypes")
	public Mono<CatalogueSearchPageResponse> getCatalogueNtypes(
			@RequestParam(name = "search", required = false) String search,
			@RequestParam(name = "countryNumistaCode", required = false) String countryNumistaCode,
			@RequestParam(name = "issuerNumistaCode", required = false) String issuerNumistaCode,
			@RequestParam(name = "currencyNid", required = false) String currencyNid,
			@RequestParam(name = "denominationNid", required = false) String denominationNid,
			@RequestParam(name = "denominationNumericValue", required = false) Double denominationNumericValue,
			@RequestParam(name = "startYear", required = false) Integer startYear,
			@RequestParam(name = "endYear", required = false) Integer endYear,
			@RequestParam(name = "minDenomination", required = false) Double minDenomination,
			@RequestParam(name = "maxDenomination", required = false) Double maxDenomination,
			@RequestParam(name = "types", required = false) List<String> types,
			@RequestParam(name = "sortType", required = false, defaultValue = "country") String sortType,
			@RequestParam(name = "myCollectionOnly", required = false, defaultValue = "false") boolean myCollectionOnly,
			@RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
			@RequestParam(name = "limit", required = false, defaultValue = "200") int limit) {
		return catalogueNtypesService.search(CatalogueNtypesService.fromQueryParams(
				search,
				countryNumistaCode,
				issuerNumistaCode,
				currencyNid,
				denominationNid,
				denominationNumericValue,
				startYear,
				endYear,
				minDenomination,
				maxDenomination,
				types,
				sortType,
				myCollectionOnly,
				offset,
				limit));
	}

	private <T> Mono<ResponseEntity<T>> parseErrorResponse(Throwable error) {
		if (error instanceof ResponseStatusException statusException) {
			log.warn("Catalogue parse request failed: {}", statusException.getReason());
			return Mono.just(ResponseEntity.status(statusException.getStatusCode()).build());
		}
		log.error("Catalogue parse request failed: {}", error.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
	}

	public record CurrencyResponse(String nid, String name, String fullName) {
	}

	public record DenominationResponse(String nid, String name, Double numericValue) {
	}

	public record CollectibleTypeResponse(String code, String name) {
	}

	public record VariantResponse(
			String nid,
			Integer mintage,
			Boolean dated,
			Integer fromGregorianYear,
			Integer tillGregorianYear,
			Integer dateGregorianYear,
			String comment,
			List<MarkResponse> marks) {
	}

	public record CatalogueItemResponse(
			String nid,
			String title,
			CountryResponse country,
			IssuerResponse issuer,
			CurrencyResponse currency,
			DenominationResponse denomination,
			CollectibleTypeResponse collectibleType,
			String frontImageUrl,
			String backImageUrl,
			List<VariantResponse> variants) {
	}

	public record CatalogueSearchPageResponse(
			List<CatalogueItemResponse> items,
			long totalCount,
			List<NumistaCollectionItemResponse> collectionItems) {

		public CatalogueSearchPageResponse(List<CatalogueItemResponse> items, long totalCount) {
			this(items, totalCount, List.of());
		}
	}
}
