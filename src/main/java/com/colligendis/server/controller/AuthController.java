package com.colligendis.server.controller;

import com.colligendis.server.dto.AuthRequest;
import com.colligendis.server.dto.AuthResponse;
import com.colligendis.server.dto.MeResponse;
import com.colligendis.server.dto.RefreshTokenRequest;
import com.colligendis.server.dto.SignupRequest;
import com.colligendis.server.security.AuthCookieHelper;
import com.colligendis.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final AuthCookieHelper authCookieHelper;

	@PostMapping("/signup")
	public Mono<ResponseEntity<AuthResponse>> signup(
			@Valid @RequestBody SignupRequest signupRequest,
			ServerWebExchange exchange) {
		log.info("Signup request received for email: {}", signupRequest.getEmail());
		return authService.signup(signupRequest)
				.map(response -> toClientResponse(exchange, response))
				.onErrorResume(this::toErrorResponse);
	}

	@PostMapping("/login")
	public Mono<ResponseEntity<AuthResponse>> login(
			@Valid @RequestBody AuthRequest authRequest,
			ServerWebExchange exchange) {
		log.info("Login request received for user: {}", authRequest.getUsername());
		return authService.login(authRequest)
				.map(response -> toClientResponse(exchange, response))
				.onErrorResume(this::toErrorResponse);
	}

	@PostMapping("/refresh")
	public Mono<ResponseEntity<AuthResponse>> refreshToken(
			@RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
			ServerWebExchange exchange) {
		log.info("Token refresh request received");

		String refreshToken = resolveRefreshToken(refreshTokenRequest, exchange);
		if (!StringUtils.hasText(refreshToken)) {
			return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
		}

		return authService.refreshToken(refreshToken)
				.map(response -> toClientResponse(exchange, response))
				.onErrorResume(this::toErrorResponse);
	}

	@PostMapping("/logout")
	public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
		authCookieHelper.clearAuthCookies(exchange.getResponse());
		return Mono.just(ResponseEntity.noContent().build());
	}

	@GetMapping("/me")
	public Mono<ResponseEntity<MeResponse>> me() {
		return ReactiveSecurityContextHolder.getContext()
				.flatMap(securityContext -> {
					Authentication authentication = securityContext.getAuthentication();
					if (authentication == null || !authentication.isAuthenticated()) {
						return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<MeResponse>build());
					}
					return authService.currentUser(authentication.getName())
							.map(ResponseEntity::ok);
				})
				.defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build())
				.onErrorResume(error -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
	}

	@GetMapping("/health")
	public Mono<ResponseEntity<String>> health() {
		log.debug("Health check request");
		return Mono.just(ResponseEntity.ok("Auth service is running"));
	}

	private ResponseEntity<AuthResponse> toClientResponse(ServerWebExchange exchange, AuthResponse response) {
		if (authCookieHelper.isWebClient(exchange.getRequest())) {
			authCookieHelper.setAuthCookies(
					exchange.getResponse(),
					response.getToken(),
					response.getRefreshToken());
			return ResponseEntity.ok(new AuthResponse(null, null, response.getUsername()));
		}
		return ResponseEntity.ok(response);
	}

	private String resolveRefreshToken(RefreshTokenRequest refreshTokenRequest, ServerWebExchange exchange) {
		if (refreshTokenRequest != null && StringUtils.hasText(refreshTokenRequest.getRefreshToken())) {
			return refreshTokenRequest.getRefreshToken();
		}
		return authCookieHelper.getRefreshTokenFromRequest(exchange.getRequest());
	}

	private Mono<ResponseEntity<AuthResponse>> toErrorResponse(Throwable error) {
		if (error instanceof ResponseStatusException statusException) {
			log.error("Auth request failed: {}", error.getMessage());
			return Mono.just(ResponseEntity.status(statusException.getStatusCode()).build());
		}
		log.error("Auth request failed: {}", error.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}
}
