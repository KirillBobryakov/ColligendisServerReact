package com.colligendis.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.dto.CollectibleTypeParseSummaryResponse;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.parser.numista.init_parser.CollectibleTypeTreeParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
@RequestMapping("/api/collectible-types")
@RequiredArgsConstructor
public class CollectibleTypeAdminController {

	private final CollectibleTypeTreeParser collectibleTypeTreeParser;
	private final ColligendisUserService colligendisUserService;
	private final BaseLogger baseLogger = new BaseLogger();

	@PostMapping("/parse-init")
	public Mono<ResponseEntity<CollectibleTypeParseSummaryResponse>> parseCollectibleTypesInit() {
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> {
					if (user.getRoles() == null || !user.getRoles().contains("ADMIN")) {
						return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
					}
					return Mono.fromCallable(() -> collectibleTypeTreeParser.parseAndSave(user))
							.subscribeOn(Schedulers.boundedElastic())
							.map(result -> {
								if (result == null) {
									log.warn("Collectible type init parse failed for admin {}", user.getUsername());
									return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
								}
								log.info("Collectible type init parse completed by admin {}: total={} created={} updated={}",
										user.getUsername(), result.totalTypes(), result.createdCount(),
										result.updatedCount());
								return ResponseEntity.ok(result);
							});
				});
	}
}
