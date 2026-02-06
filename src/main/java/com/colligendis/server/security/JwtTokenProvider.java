package com.colligendis.server.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        long now = System.currentTimeMillis();
        log.debug("Generating JWT token for user: {}", authentication.getName());
        
        String token = Jwts.builder()
                .subject(authentication.getName())
                .claim("authorities", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
        
        log.info("JWT token generated successfully for user: {}", authentication.getName());
        return token;
    }

    public String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        log.debug("Generating refresh token for user: {}", username);
        
        String token = Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
        
        log.info("Refresh token generated successfully for user: {}", username);
        return token;
    }

    public String getUsernameFromToken(String token) {
        log.debug("Extracting username from token");
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        String username = claims.getSubject();
        log.debug("Username extracted: {}", username);
        return username;
    }

    public boolean validateToken(String token) {
        try {
            log.debug("Validating JWT token");
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            log.debug("JWT token is valid");
            return true;
        } catch (JwtException e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
}
