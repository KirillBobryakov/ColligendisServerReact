package com.colligendis.server.controller;

import com.colligendis.server.parser.numista.NumistaPipeline;
import com.colligendis.server.service.CatalogueSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/public/ntype")
@RequiredArgsConstructor
public class NTypeController {

	private final NumistaPipeline numistaPipeline;
	private final CatalogueSummaryService catalogueSummaryService;

	@GetMapping("/parse")
	public Mono<ParseNTypesResponse> parseNType(
			@RequestParam(name = "nid", required = false) String nid,
			@RequestParam(name = "nids", required = false) List<String> nids,
			@RequestParam(name = "issuerNumistaCode", required = false) String issuerNumistaCode,
			@RequestParam(name = "countryNumistaCode", required = false) String countryNumistaCode) {

		if (StringUtils.hasText(issuerNumistaCode)) {
			final String code = issuerNumistaCode.trim();
			log.info("Request to parse all NTypes stored for issuer={}", code);
			return catalogueSummaryService.findNTypeNidsByIssuerNumistaCode(code)
					.flatMap(this::parseNids);
		}

		if (StringUtils.hasText(countryNumistaCode)) {
			final String code = countryNumistaCode.trim();
			log.info("Request to parse all NTypes stored for country={}", code);
			return catalogueSummaryService.findNTypeNidsByCountryNumistaCode(code)
					.flatMap(this::parseNids);
		}

		final List<String> normalizedNids = normalizeNids(nid, nids);
		if (normalizedNids.isEmpty()) {
			return Mono.just(new ParseNTypesResponse(false, List.of(
					new ParseNTypeResponse(null, false, "nid, nids, issuerNumistaCode or countryNumistaCode is required"))));
		}

		log.info("Request to parse NType(s) from Numista, nids={}", normalizedNids);
		return parseNids(normalizedNids);
	}

	private Mono<ParseNTypesResponse> parseNids(List<String> nids) {
		final List<String> normalizedNids = normalizeNids(null, nids);
		if (normalizedNids.isEmpty()) {
			return Mono.just(new ParseNTypesResponse(false, List.of(
					new ParseNTypeResponse(null, false, "no ntypes found to parse"))));
		}

		return Flux.fromIterable(normalizedNids)
				.concatMap(targetNid -> numistaPipeline.pipeline(targetNid)
						.map(parsedPage -> new ParseNTypeResponse(parsedPage.getNid(), true, null))
						.onErrorResume(error -> {
							log.error("Failed to parse NType from Numista, nid={}", targetNid, error);
							return Mono.just(new ParseNTypeResponse(targetNid, false, error.getMessage()));
						}))
				.collectList()
				.map(results -> new ParseNTypesResponse(results.stream().allMatch(ParseNTypeResponse::success), results));
	}

	private List<String> normalizeNids(String nid, List<String> nids) {
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		if (nid != null && !nid.trim().isEmpty()) {
			unique.add(nid.trim());
		}
		if (nids != null) {
			for (String value : nids) {
				if (value == null || value.isBlank()) {
					continue;
				}
				String[] split = value.split(",");
				for (String entry : split) {
					String normalized = entry.trim();
					if (!normalized.isEmpty()) {
						unique.add(normalized);
					}
				}
			}
		}
		return new ArrayList<>(unique);
	}

	public record ParseNTypeResponse(String nid, boolean success, String error) {
	}

	public record ParseNTypesResponse(boolean success, List<ParseNTypeResponse> results) {
	}
}
