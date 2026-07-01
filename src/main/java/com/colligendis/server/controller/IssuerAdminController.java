package com.colligendis.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.colligendis.server.controller.IssuerController.IssuerResponse;
import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.numista.model.Issuer;
import com.colligendis.server.database.numista.service.CountryService;
import com.colligendis.server.database.numista.service.IssuerService;
import com.colligendis.server.database.numista.service.SubjectService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.database.result.CreateRelationshipExecutionStatus;
import com.colligendis.server.database.result.FindExecutionStatus;
import com.colligendis.server.dto.CreateIssuerRequest;
import com.colligendis.server.logger.BaseLogger;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/issuers")
@RequiredArgsConstructor
public class IssuerAdminController {

	private final IssuerService issuerService;
	private final CountryService countryService;
	private final SubjectService subjectService;
	private final ColligendisUserService colligendisUserService;
	private final BaseLogger baseLogger = new BaseLogger();

	@PostMapping
	public Mono<ResponseEntity<IssuerResponse>> createIssuer(@Valid @RequestBody CreateIssuerRequest request) {
		return colligendisUserService.requireAuthenticatedUser(baseLogger)
				.flatMap(user -> {
					if (user.getRoles() == null || !user.getRoles().contains("ADMIN")) {
						return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
					}
					return createIssuerForUser(request, user);
				});
	}

	private Mono<ResponseEntity<IssuerResponse>> createIssuerForUser(CreateIssuerRequest request,
			ColligendisUser user) {
		final boolean hasCountry = StringUtils.hasText(request.countryNumistaCode());
		final boolean hasSubject = StringUtils.hasText(request.subjectNumistaCode());
		if (hasCountry == hasSubject) {
			return Mono.just(ResponseEntity.badRequest().build());
		}

		final String numistaCode = request.numistaCode().trim();
		final String name = request.name().trim();
		if (!StringUtils.hasText(numistaCode) || !StringUtils.hasText(name)) {
			return Mono.just(ResponseEntity.badRequest().build());
		}

		final Issuer issuer = new Issuer();
		issuer.setNumistaCode(numistaCode);
		issuer.setName(name);

		final Mono<ColligendisUser> userMono = Mono.just(user);

		return issuerService.findByNumistaCode(numistaCode, baseLogger)
				.flatMap(findResult -> {
					if (findResult.getStatus() == FindExecutionStatus.FOUND) {
						return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
					}
					return issuerService.create(issuer, userMono, baseLogger)
							.flatMap(createResult -> {
								if (createResult.getStatus() != CreateNodeExecutionStatus.WAS_CREATED
										|| createResult.getNode() == null) {
									log.warn("Failed to create issuer numistaCode={} status={}", numistaCode,
											createResult.getStatus());
									return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
								}
								final Issuer created = createResult.getNode();
								if (hasCountry) {
									return relateToCountry(request.countryNumistaCode().trim(), created, userMono);
								}
								return relateToSubject(request.subjectNumistaCode().trim(), created, userMono);
							});
				});
	}

	private Mono<ResponseEntity<IssuerResponse>> relateToCountry(String countryNumistaCode, Issuer issuer,
			Mono<ColligendisUser> userMono) {
		return countryService.findByNumistaCode(countryNumistaCode, baseLogger)
				.flatMap(countryResult -> {
					if (countryResult.getStatus() != FindExecutionStatus.FOUND || countryResult.getNode() == null) {
						return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
					}
					return issuerService.relateToCountry(issuer, countryResult.getNode(), userMono, baseLogger)
							.map(relResult -> toCreateResponse(relResult, issuer));
				});
	}

	private Mono<ResponseEntity<IssuerResponse>> relateToSubject(String subjectNumistaCode, Issuer issuer,
			Mono<ColligendisUser> userMono) {
		return subjectService.findByNumistaCode(subjectNumistaCode, baseLogger)
				.flatMap(subjectResult -> {
					if (subjectResult.getStatus() != FindExecutionStatus.FOUND || subjectResult.getNode() == null) {
						return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
					}
					return issuerService.relateToSubject(issuer, subjectResult.getNode(), userMono, baseLogger)
							.map(relResult -> toCreateResponse(relResult, issuer));
				});
	}

	private ResponseEntity<IssuerResponse> toCreateResponse(
			com.colligendis.server.database.result.ExecutionResult<com.colligendis.server.database.AbstractNode, CreateRelationshipExecutionStatus> relResult,
			Issuer issuer) {
		final CreateRelationshipExecutionStatus status = relResult.getStatus();
		if (status != CreateRelationshipExecutionStatus.WAS_CREATED
				&& status != CreateRelationshipExecutionStatus.IS_ALREADY_EXISTS) {
			log.warn("Failed to relate issuer status={}", status);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new IssuerResponse(issuer.getNumistaCode(), issuer.getName()));
	}

}
