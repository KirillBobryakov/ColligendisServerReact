package com.colligendis.server.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.service.CatalogueSummaryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/public/catalogue-summary")
@RequiredArgsConstructor
public class CatalogueSummaryController {

	private final CatalogueSummaryService catalogueSummaryService;
	private final ColligendisUserService colligendisUserService;
	private final BaseLogger baseLogger = new BaseLogger();

	@GetMapping("/countries")
	public Mono<List<CountrySummaryResponse>> getCountriesSummary() {
		return catalogueSummaryService.getCountriesSummary();
	}

	@GetMapping("/issuers")
	public Mono<List<IssuerSummaryResponse>> getIssuersSummary() {
		return catalogueSummaryService.getIssuersSummary();
	}

	@GetMapping("/countries/load-ntypes-count")
	public Mono<ResponseEntity<CountryLoadNTypesCountResponse>> loadCountryNTypesCount(
			@RequestParam(name = "countryNumistaCode") String countryNumistaCode,
			@RequestParam(name = "withNids", defaultValue = "false") boolean withNids) {
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> catalogueSummaryService.loadCountryNTypesCount(user, countryNumistaCode, withNids))
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	@GetMapping("/issuers/load-ntypes-count")
	public Mono<ResponseEntity<IssuerLoadNTypesCountResponse>> loadIssuerNTypesCount(
			@RequestParam(name = "issuerNumistaCode") String issuerNumistaCode,
			@RequestParam(name = "withNids", defaultValue = "false") boolean withNids) {
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> catalogueSummaryService.loadIssuerNTypesCount(user, issuerNumistaCode, withNids))
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	private <T> Mono<ResponseEntity<T>> toErrorResponse(Throwable error) {
		if (error instanceof ResponseStatusException statusException) {
			log.warn("Catalogue summary request failed: {}", statusException.getReason());
			return Mono.just(ResponseEntity.status(statusException.getStatusCode()).build());
		}
		log.error("Catalogue summary request failed: {}", error.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
	}

	public record CountrySummaryResponse(
			String numistaCode,
			String name,
			long relatedIssuersCount,
			Integer countNTypesOnNumista,
			long countNTypesOnServer) {
	}

	public record IssuerSummaryResponse(
			String numistaCode,
			String name,
			Integer countNTypesOnNumista,
			long countNTypesOnServer) {
	}

	public record CountryLoadNTypesCountResponse(
			String countryNumistaCode,
			Integer countNTypesOnNumista,
			boolean loadedFromNumista,
			List<String> nids) {
	}

	public record IssuerLoadNTypesCountResponse(
			String issuerNumistaCode,
			Integer countNTypesOnNumista,
			boolean loadedFromNumista,
			List<String> nids) {
	}
}
