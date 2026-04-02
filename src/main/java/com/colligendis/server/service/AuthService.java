package com.colligendis.server.service;

import com.colligendis.server.dto.AuthRequest;
import com.colligendis.server.dto.AuthResponse;
import com.colligendis.server.logger.LogExecutionTime;
import com.colligendis.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final JwtTokenProvider jwtTokenProvider;
	private final UserService userService;

	@LogExecutionTime
	public Mono<AuthResponse> login(AuthRequest authRequest) {
		log.info("Login attempt for user: {}", authRequest.getUsername());

		return userService.findByUsername(authRequest.getUsername())
				.flatMap(user -> {
					// In production, verify password with passwordEncoder.matches()
					log.debug("User authenticated: {}", user.getUsername());

					Authentication authentication = new UsernamePasswordAuthenticationToken(
							user.getUsername(), null, new ArrayList<>());

					String token = jwtTokenProvider.generateToken(authentication);
					String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

					log.info("Login successful for user: {}", user.getUsername());

					return Mono.just(new AuthResponse(token, refreshToken, user.getUsername()));
				})
				.switchIfEmpty(Mono.defer(() -> {
					log.warn("Login failed: User not found - {}", authRequest.getUsername());
					return Mono.error(new RuntimeException("Invalid credentials"));
				}));
	}

	@LogExecutionTime
	public Mono<AuthResponse> refreshToken(String refreshToken) {
		log.info("Refreshing token");

		try {
			if (jwtTokenProvider.validateToken(refreshToken)) {
				String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

				log.debug("Generating new tokens for user: {}", username);

				Authentication authentication = new UsernamePasswordAuthenticationToken(
						username, null, new ArrayList<>());

				String newToken = jwtTokenProvider.generateToken(authentication);
				String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

				log.info("Token refreshed successfully for user: {}", username);

				return Mono.just(new AuthResponse(newToken, newRefreshToken, username));
			} else {
				log.warn("Token refresh failed: Invalid refresh token");
				return Mono.error(new RuntimeException("Invalid refresh token"));
			}
		} catch (Exception e) {
			log.error("Token refresh failed: {}", e.getMessage());
			return Mono.error(new RuntimeException("Token refresh failed", e));
		}
	}
}
