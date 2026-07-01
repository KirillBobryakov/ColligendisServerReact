package com.colligendis.server.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colligendis.server.dto.MarkResponse;
import com.colligendis.server.service.MarkQueryService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/public/marks")
@RequiredArgsConstructor
public class MarkController {

	private final MarkQueryService markQueryService;

	@GetMapping
	public Mono<ResponseEntity<MarkResponse>> getByNid(@RequestParam String nid) {
		return markQueryService.findByNid(nid)
				.map(ResponseEntity::ok)
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}

	@GetMapping("/by-variant")
	public Mono<List<MarkResponse>> getByVariantNid(@RequestParam String variantNid) {
		return markQueryService.findByVariantNid(variantNid);
	}
}
