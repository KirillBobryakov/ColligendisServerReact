package com.colligendis.server.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.dto.DeleteNumistaCollectionRequest;
import com.colligendis.server.dto.NumistaCollectionItemResponse;
import com.colligendis.server.dto.SaveNumistaCollectionRequest;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.service.NumistaCollectionApiService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/collection")
@RequiredArgsConstructor
public class NumistaCollectionController {

	private final NumistaCollectionApiService collectionApiService;
	private final ColligendisUserService colligendisUserService;
	private final BaseLogger baseLogger = new BaseLogger();

	@PostMapping("/items")
	public Mono<ResponseEntity<NumistaCollectionItemResponse>> save(
			@Valid @RequestBody SaveNumistaCollectionRequest request) {
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> collectionApiService.save(request, user, baseLogger))
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	@DeleteMapping("/items")
	public Mono<ResponseEntity<Void>> delete(@Valid @RequestBody DeleteNumistaCollectionRequest request) {
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> collectionApiService.delete(request, user, baseLogger))
				.then(Mono.just(ResponseEntity.noContent().<Void>build()))
				.onErrorResume(this::deleteErrorResponse);
	}

	@GetMapping("/items")
	public Mono<ResponseEntity<List<NumistaCollectionItemResponse>>> list(
			@RequestParam String coinId,
			@RequestParam String versionId) {
		if (!StringUtils.hasText(coinId) || !StringUtils.hasText(versionId)) {
			return Mono.just(ResponseEntity.badRequest().build());
		}
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMapMany(user -> collectionApiService.listByCoinAndVersion(
						user, coinId.trim(), versionId.trim(), baseLogger))
				.collectList()
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	@PostMapping("/refresh")
	public Mono<ResponseEntity<List<NumistaCollectionItemResponse>>> refresh(
			@RequestParam String issuerNumistaCode) {
		if (!StringUtils.hasText(issuerNumistaCode)) {
			return Mono.just(ResponseEntity.badRequest().build());
		}
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> collectionApiService.refreshFromNumistaPage(
						issuerNumistaCode.trim(), user, baseLogger))
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	/**
	 * Fetches ALL pages of the user's collection from Numista (no issuer filter),
	 * ensures every referenced NType is up-to-date in the database, and persists
	 * all collection items.
	 */
	@PostMapping("/refresh-all")
	public Mono<ResponseEntity<List<NumistaCollectionItemResponse>>> refreshAll() {
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> collectionApiService.refreshFullCollection(user, baseLogger))
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	private Mono<ResponseEntity<Void>> deleteErrorResponse(Throwable error) {
		HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
		if (error instanceof ResponseStatusException statusException) {
			log.warn("Collection delete failed: {}", statusException.getReason());
			status = statusException.getStatusCode();
		} else {
			log.error("Collection delete failed: {}", error.getMessage());
		}
		return Mono.just(ResponseEntity.status(status).body(null));
	}

	private <T> Mono<ResponseEntity<T>> toErrorResponse(Throwable error) {
		if (error instanceof ResponseStatusException statusException) {
			String message = statusException.getReason();
			log.warn("Collection request failed: {}", message);
			return Mono.just(ResponseEntity.status(statusException.getStatusCode()).<T>build());
		}
		log.error("Collection request failed: {}", error.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<T>build());
	}
}
