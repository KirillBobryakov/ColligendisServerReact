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
import com.colligendis.server.database.numista.service.AcquisitionPlaceService;
import com.colligendis.server.dto.AcquisitionPlaceResponse;
import com.colligendis.server.dto.CreateAcquisitionPlaceRequest;
import com.colligendis.server.logger.BaseLogger;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/acquisition-places")
@RequiredArgsConstructor
public class AcquisitionPlaceController {

	private final AcquisitionPlaceService acquisitionPlaceService;
	private final ColligendisUserService colligendisUserService;
	private final BaseLogger baseLogger = new BaseLogger();

	@GetMapping
	public Mono<ResponseEntity<List<AcquisitionPlaceResponse>>> list() {
		return currentUser()
				.flatMapMany(user -> acquisitionPlaceService.listForUser(user, baseLogger))
				.map(place -> new AcquisitionPlaceResponse(place.getUuid(), place.getName()))
				.collectList()
				.map(ResponseEntity::ok)
				.onErrorResume(this::toErrorResponse);
	}

	@PostMapping
	public Mono<ResponseEntity<AcquisitionPlaceResponse>> create(
			@Valid @RequestBody CreateAcquisitionPlaceRequest request) {
		if (!StringUtils.hasText(request.name())) {
			return Mono.just(ResponseEntity.badRequest().build());
		}
		return currentUser()
				.flatMap(user -> acquisitionPlaceService.createForUser(user, request.name().trim(), baseLogger))
				.map(place -> ResponseEntity.status(HttpStatus.CREATED)
						.body(new AcquisitionPlaceResponse(place.getUuid(), place.getName())))
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
		log.error("Acquisition place request failed: {}", error.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
	}
}
