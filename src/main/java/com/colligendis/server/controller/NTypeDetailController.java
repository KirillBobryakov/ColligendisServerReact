package com.colligendis.server.controller;

import com.colligendis.server.controller.CatalogueController.CollectibleTypeResponse;
import com.colligendis.server.controller.CatalogueController.CurrencyResponse;
import com.colligendis.server.controller.CatalogueController.DenominationResponse;
import com.colligendis.server.dto.MarkResponse;
import com.colligendis.server.service.NTypeDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/public/ntype")
@RequiredArgsConstructor
public class NTypeDetailController {

	private final NTypeDetailService nTypeDetailService;

	@GetMapping
	public Mono<ResponseEntity<NTypeDetailResponse>> getNType(@RequestParam String nid) {
		return nTypeDetailService.findByNid(nid)
				.map(ResponseEntity::ok)
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}

	public record NamedEntityResponse(String nid, String name, String rulerType) {
	}

	public record CatalogueReferenceResponse(String catalogue, String number) {
	}

	public record SignatureDetailResponse(String nid, String name, String imageUrl) {
	}

	public record VariantDetailResponse(
			String nid,
			Integer mintage,
			Boolean dated,
			Integer fromGregorianYear,
			Integer tillGregorianYear,
			Integer dateGregorianYear,
			String comment,
			String mintLetter,
			List<CatalogueReferenceResponse> catalogueReferences,
			List<SignatureDetailResponse> signatures,
			List<MarkResponse> marks) {
	}

	public record NTypePartDetailResponse(
			String partType,
			String description,
			String lettering,
			String unabridgedLegend,
			String letteringTranslation,
			String imageUrl) {
	}

	public record NTypeDetailResponse(
			String nid,
			String title,
			CountryController.CountryResponse country,
			IssuerController.IssuerResponse issuer,
			CurrencyResponse currency,
			DenominationResponse denomination,
			CollectibleTypeResponse collectibleType,
			String commemoratedEventName,
			NamedEntityResponse series,
			String yearIssueDate,
			String monthIssueDate,
			String dayIssueDate,
			String demonetized,
			String demonetizationYear,
			String demonetizationMonth,
			String demonetizationDay,
			String compositionAdditionalDetails,
			String compositionTypeName,
			NamedEntityResponse shape,
			String shapeAdditionalDetails,
			Double weight,
			Double size,
			Double size2,
			Double thickness,
			List<String> techniques,
			String techniqueAdditionalDetails,
			String alignment,
			NTypePartDetailResponse obverse,
			NTypePartDetailResponse reverse,
			NTypePartDetailResponse edge,
			NTypePartDetailResponse watermark,
			List<NamedEntityResponse> rulingAuthorities,
			List<NamedEntityResponse> issuingEntities,
			List<CatalogueReferenceResponse> catalogueReferences,
			List<NamedEntityResponse> printers,
			List<String> specifiedMints,
			List<VariantDetailResponse> variants) {
	}
}
