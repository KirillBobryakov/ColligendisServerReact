package com.colligendis.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthCookieHelper {

	public static final String ACCESS_COOKIE = "colligendis_access_token";
	public static final String REFRESH_COOKIE = "colligendis_refresh_token";
	public static final String CLIENT_HEADER = "X-Colligendis-Client";

	@Value("${auth.cookie.secure:true}")
	private boolean secure;

	@Value("${auth.cookie.same-site:None}")
	private String sameSite;

	@Value("${jwt.expiration}")
	private Long accessMaxAgeMs;

	@Value("${jwt.refresh-expiration}")
	private Long refreshMaxAgeMs;

	public boolean isWebClient(ServerHttpRequest request) {
		return "web".equalsIgnoreCase(request.getHeaders().getFirst(CLIENT_HEADER));
	}

	public void setAuthCookies(ServerHttpResponse response, String accessToken, String refreshToken) {
		response.addCookie(buildCookie(ACCESS_COOKIE, accessToken, accessMaxAgeMs / 1000));
		response.addCookie(buildCookie(REFRESH_COOKIE, refreshToken, refreshMaxAgeMs / 1000));
	}

	public void clearAuthCookies(ServerHttpResponse response) {
		response.addCookie(buildCookie(ACCESS_COOKIE, "", 0));
		response.addCookie(buildCookie(REFRESH_COOKIE, "", 0));
	}

	public String getAccessTokenFromRequest(ServerHttpRequest request) {
		String fromCookie = readCookie(request, ACCESS_COOKIE);
		if (StringUtils.hasText(fromCookie)) {
			return fromCookie;
		}
		return null;
	}

	public String getRefreshTokenFromRequest(ServerHttpRequest request) {
		String fromCookie = readCookie(request, REFRESH_COOKIE);
		if (StringUtils.hasText(fromCookie)) {
			return fromCookie;
		}
		return null;
	}

	private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(secure)
				.path("/")
				.sameSite(sameSite)
				.maxAge(maxAgeSeconds)
				.build();
	}

	private String readCookie(ServerHttpRequest request, String name) {
		HttpCookie cookie = request.getCookies().getFirst(name);
		return cookie != null ? cookie.getValue() : null;
	}

}
