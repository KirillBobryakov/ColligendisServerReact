package com.colligendis.server.logger;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Logs every HTTP response to the application console: status, duration, and
 * body
 * (truncated for large JSON; binary responses log byte size only).
 */
@Slf4j
@Component
public class HttpResponseLoggingWebFilter implements WebFilter {

	private static final int MAX_BODY_LOG_CHARS = 100;
	private static final String RESPONSE_LOGGED_ATTR = HttpResponseLoggingWebFilter.class.getName() + ".logged";

	@Override
	public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
		final long startNanos = System.nanoTime();
		final String requestLine = formatRequestLine(exchange.getRequest());

		log.info("HTTP request: {}", requestLine);

		final ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
			@Override
			public @NonNull Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
				return super.writeWith(Flux.from(body).buffer().map(dataBuffers -> {
					final DataBuffer joined = bufferFactory().join(dataBuffers);
					final byte[] content = new byte[joined.readableByteCount()];
					joined.read(content);
					DataBufferUtils.release(joined);
					logResponseBody(requestLine, getStatusCode(), getHeaders().getContentType(), content,
							startNanos);
					exchange.getAttributes().put(RESPONSE_LOGGED_ATTR, Boolean.TRUE);
					return bufferFactory().wrap(content);
				}));
			}
		};

		return chain.filter(exchange.mutate().response(decoratedResponse).build())
				.doFinally(signal -> {
					if (Boolean.TRUE.equals(exchange.getAttribute(RESPONSE_LOGGED_ATTR))) {
						return;
					}
					logResponseSummary(requestLine, exchange.getResponse().getStatusCode(), startNanos);
				});
	}

	private static String formatRequestLine(ServerHttpRequest request) {
		final String path = request.getURI().getRawPath();
		final String query = request.getURI().getRawQuery();
		return request.getMethod() + " " + path
				+ (query != null && !query.isEmpty() ? "?" + query : "");
	}

	private void logResponseBody(
			String requestLine,
			HttpStatusCode status,
			MediaType contentType,
			byte[] content,
			long startNanos) {
		final long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
		if (isBinary(contentType)) {
			log.info("HTTP response: {} -> {} {} bytes ({} ms)", requestLine, status, content.length, elapsedMs);
			return;
		}
		String body = new String(content, StandardCharsets.UTF_8);
		if (body.length() > MAX_BODY_LOG_CHARS) {
			body = body.substring(0, MAX_BODY_LOG_CHARS) + "...(truncated)";
		}
		log.info("HTTP response: {} -> {} ({} ms) body={}", requestLine, status, elapsedMs, body);
	}

	private void logResponseSummary(String requestLine, HttpStatusCode status, long startNanos) {
		final long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
		log.info("HTTP response: {} -> {} ({} ms)", requestLine, status, elapsedMs);
	}

	private static boolean isBinary(MediaType contentType) {
		if (contentType == null) {
			return false;
		}
		return "image".equalsIgnoreCase(contentType.getType())
				|| MediaType.APPLICATION_OCTET_STREAM.includes(contentType);
	}
}
