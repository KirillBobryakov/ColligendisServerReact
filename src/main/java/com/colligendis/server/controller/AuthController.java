package com.colligendis.server.controller;

import com.colligendis.server.dto.AuthRequest;
import com.colligendis.server.dto.AuthResponse;
import com.colligendis.server.dto.RefreshTokenRequest;
import com.colligendis.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody AuthRequest authRequest) {
        log.info("Login request received for user: {}", authRequest.getUsername());
        
        return authService.login(authRequest)
                .map(response -> {
                    log.info("Login successful for user: {}", authRequest.getUsername());
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("Login failed: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        log.info("Token refresh request received");
        
        return authService.refreshToken(refreshTokenRequest.getRefreshToken())
                .map(response -> {
                    log.info("Token refreshed successfully");
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("Token refresh failed: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        log.debug("Health check request");
        return Mono.just(ResponseEntity.ok("Auth service is running"));
    }
}
