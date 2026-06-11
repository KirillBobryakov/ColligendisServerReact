package com.colligendis.server.parser.numista;

import java.net.URI;

/**
 * Numista catalogue pages use relative image hrefs such as {@code ../photos/...}.
 * Those paths are stored as-is in Neo4j; API responses resolve them against the
 * English catalogue base URL.
 */
public final class NumistaCatalogueImageUrls {

	public static final String CATALOGUE_BASE = "https://en.numista.com/catalogue";

	private NumistaCatalogueImageUrls() {
	}

	/**
	 * Converts an href from Numista HTML into the canonical stored form ({@code ../photos/...}).
	 */
	public static String toStoredPicturePath(String href) {
		if (href == null || href.isBlank()) {
			return href;
		}
		String t = href.trim();
		if (t.startsWith("../")) {
			return t;
		}
		try {
			if (t.startsWith("//")) {
				t = "https:" + t;
			}
			if (t.startsWith("http://") || t.startsWith("https://")) {
				URI uri = URI.create(t);
				String path = uri.getPath();
				if (path != null && path.startsWith("/catalogue/")) {
					return ".." + path.substring("/catalogue".length());
				}
			} else if (t.startsWith("/catalogue/")) {
				return ".." + t.substring("/catalogue".length());
			}
		} catch (IllegalArgumentException ignored) {
			// leave unchanged
		}
		return t;
	}

	/**
	 * Builds an absolute image URL for clients. Stored {@code ../segment} becomes
	 * {@code https://en.numista.com/catalogue/segment}.
	 */
	public static String toAbsolutePictureUrl(String stored) {
		if (stored == null || stored.isBlank()) {
			return "";
		}
		String t = stored.trim();
		if (t.startsWith("../")) {
			return CATALOGUE_BASE + t.substring(2);
		}
		return t;
	}
}
