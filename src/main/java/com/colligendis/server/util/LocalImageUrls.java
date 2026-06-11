package com.colligendis.server.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class LocalImageUrls {

	private LocalImageUrls() {
	}

	public static String toClientUrl(String localPath) {
		return toClientUrl(localPath, false);
	}

	public static String toClientUrl(String localPath, boolean small) {
		if (localPath == null || localPath.isBlank()) {
			return "";
		}
		final String base = "/api/public/images/local?path="
				+ URLEncoder.encode(localPath.trim(), StandardCharsets.UTF_8);
		return small ? base + "&size=SMALL" : base;
	}
}
