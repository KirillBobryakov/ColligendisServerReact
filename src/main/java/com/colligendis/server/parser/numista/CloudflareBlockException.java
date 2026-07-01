package com.colligendis.server.parser.numista;

/**
 * Thrown when Numista returns a Cloudflare anti-bot challenge page instead of
 * the requested content. Cloudflare ties its {@code cf_clearance} cookie to the
 * browser's TLS/JA3 fingerprint, so even a fresh cookie does not satisfy the
 * check when the request originates from a non-browser HTTP client (Reactor
 * Netty). Retrying immediately will not help.
 */
public class CloudflareBlockException extends NumistaPageLoadException {

	public CloudflareBlockException(String url) {
		super(
				"Cloudflare anti-bot challenge detected (the server's TLS fingerprint differs from a browser's). " +
						"URL: " + url,
				url, 200);
	}
}
