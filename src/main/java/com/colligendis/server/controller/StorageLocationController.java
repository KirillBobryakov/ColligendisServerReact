package com.colligendis.server.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.service.StorageLocationService;
import com.colligendis.server.dto.CreateStorageLocationRequest;
import com.colligendis.server.dto.StorageLocationResponse;
import com.colligendis.server.logger.BaseLogger;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/storage-locations")
@RequiredArgsConstructor
public class StorageLocationController {

	private final StorageLocationService storageLocationService;
	private final ColligendisUserService colligendisUserService;
	private final BaseLogger baseLogger = new BaseLogger();

	@GetMapping
	public Mono<ResponseEntity<List<StorageLocationResponse>>> list() {
		return currentUser()
				.flatMapMany(user -> storageLocationService.listForUser(user, baseLogger))
				.map(location -> new StorageLocationResponse(location.getUuid(), location.getName()))
				.collectList()
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	@PostMapping
	public Mono<ResponseEntity<StorageLocationResponse>> create(
			@Valid @RequestBody CreateStorageLocationRequest request) {
		if (!StringUtils.hasText(request.name())) {
			return Mono.just(ResponseEntity.badRequest().build());
		}
		return currentUser()
				.flatMap(user -> storageLocationService.createForUser(user, request.name().trim(), baseLogger))
				.map(location -> ResponseEntity.status(HttpStatus.CREATED)
						.body(new StorageLocationResponse(location.getUuid(), location.getName())))
				.onErrorResume(this::toErrorResponse);
	}

	private Mono<ColligendisUser> currentUser() {
		return ReactiveSecurityContextHolder.getContext()
				.flatMap(securityContext -> {
					if (securityContext.getAuthentication() == null
							|| !securityContext.getAuthentication().isAuthenticated()) {
						return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
					}
					String username = securityContext.getAuthentication().getName();
					return colligendisUserService.findUserByUsername(username, baseLogger);
				})
				.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")));
	}

	private <T> Mono<ResponseEntity<T>> toErrorResponse(Throwable error) {
		if (error instanceof ResponseStatusException statusException) {
			return Mono.just(ResponseEntity.status(statusException.getStatusCode()).build());
		}
		log.error("Storage location request failed: {}", error.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
	}
}
