package com.colligendis.server.service;

import com.colligendis.server.database.ColligendisUser;
import com.colligendis.server.database.ColligendisUserService;
import com.colligendis.server.database.result.CreateNodeExecutionStatus;
import com.colligendis.server.dto.AuthRequest;
import com.colligendis.server.dto.AuthResponse;
import com.colligendis.server.dto.MeResponse;
import com.colligendis.server.dto.SignupRequest;
import com.colligendis.server.logger.BaseLogger;
import com.colligendis.server.logger.LogExecutionTime;
import com.colligendis.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final JwtTokenProvider jwtTokenProvider;
	private final ColligendisUserService colligendisUserService;
	private final PasswordEncoder passwordEncoder;
	private final BaseLogger baseLogger = new BaseLogger();

	@LogExecutionTime
	public Mono<AuthResponse> signup(SignupRequest signupRequest) {
		final String email = signupRequest.getEmail().trim().toLowerCase();
		final String username = resolveUsername(signupRequest.getUsername(), email);

		log.info("Signup attempt for email: {}", email);

		return colligendisUserService.existsByUsername(username, baseLogger)
				.flatMap(usernameExists -> {
					if (usernameExists) {
						return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken"));
					}
					if (username.equals(email)) {
						return Mono.just(false);
					}
					return colligendisUserService.existsByUsername(email, baseLogger);
				})
				.flatMap(emailExists -> {
					if (emailExists) {
						return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered"));
					}

					ColligendisUser user = new ColligendisUser();
					user.setUsername(username);
					user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));

					return colligendisUserService.create(user, baseLogger);
				})
				.flatMap(result -> {
					if (result.getStatus() != CreateNodeExecutionStatus.WAS_CREATED || result.getNode() == null) {
						result.logError(baseLogger);
						return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signup failed"));
					}
					return issueTokens(username);
				})
				.doOnSuccess(response -> log.info("Signup successful for user: {}", response.getUsername()));
	}

	@LogExecutionTime
	public Mono<AuthResponse> login(AuthRequest authRequest) {
		final String identifier = authRequest.getUsername().trim();
		log.info("Login attempt for user: {}", identifier);

		return resolveUser(identifier)
				.switchIfEmpty(Mono.defer(() -> {
					log.warn("Login failed: User not found - {}", identifier);
					return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
				}))
				.flatMap(user -> {
					if (!passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
						log.warn("Login failed: Invalid password for user - {}", identifier);
						return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
					}
					log.debug("User authenticated: {}", user.getUsername());
					return issueTokens(user.getUsername());
				})
				.doOnSuccess(response -> log.info("Login successful for user: {}", response.getUsername()));
	}

	@LogExecutionTime
	public Mono<AuthResponse> refreshToken(String refreshToken) {
		log.info("Refreshing token");

		if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
			log.warn("Token refresh failed: Invalid refresh token");
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
		}

		try {
			String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
			log.debug("Generating new tokens for user: {}", username);
			return issueTokens(username)
					.doOnSuccess(response -> log.info("Token refreshed successfully for user: {}", username));
		} catch (Exception e) {
			log.error("Token refresh failed: {}", e.getMessage());
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token refresh failed"));
		}
	}

	@LogExecutionTime
	public Mono<MeResponse> currentUser(String username) {
		return colligendisUserService.findUserByUsername(username, baseLogger)
				.map(user -> new MeResponse(
						user.getUsername(),
						emailOrNull(user.getUsername()),
						user.getRoles() != null ? user.getRoles() : java.util.List.of()))
				.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")));
	}

	private Mono<ColligendisUser> resolveUser(String identifier) {
		final String username = identifier.contains("@") ? identifier.toLowerCase() : identifier;
		return colligendisUserService.findUserByUsername(username, baseLogger);
	}

	private Mono<AuthResponse> issueTokens(String username) {
		Authentication authentication = new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
		String token = jwtTokenProvider.generateToken(authentication);
		String refreshToken = jwtTokenProvider.generateRefreshToken(username);
		return Mono.just(new AuthResponse(token, refreshToken, username));
	}

	private String resolveUsername(String requestedUsername, String email) {
		if (requestedUsername != null && !requestedUsername.isBlank()) {
			return requestedUsername.trim();
		}
		return email;
	}

	private String emailOrNull(String username) {
		return username.contains("@") ? username : null;
	}
}
