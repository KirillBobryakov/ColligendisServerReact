package com.colligendis.server.util;

import java.util.function.Function;
import java.util.function.Supplier;

import com.colligendis.server.database.exception.DatabaseError;

import reactor.core.publisher.Mono;

public sealed interface Either<L, R> permits Left, Right {
	static <L, R> Either<L, R> left(L v) {
		return new Left<>(v);
	}

	static <L, R> Either<L, R> right(R v) {
		return new Right<>(v);
	}

	boolean isLeft();

	boolean isRight();

	L left();

	R right();

	default <T> T fold(Function<L, T> l, Function<R, T> r) {
		return this instanceof Left<L, R> le ? l.apply(le.value())
				: r.apply(((Right<L, R>) this).value());
	}

	/**
	 * Reactive fold: when Left, logs the error using the provided logger and
	 * returns
	 * Mono.empty();
	 * when Right, returns the R value.
	 *
	 * @param orElse the supplier of the Mono<R> to be returned when Left
	 * @return Mono<R> if Right; Mono.empty() if Left
	 */
	default Mono<R> reactiveFoldOrElse(Supplier<? extends Mono<R>> orElse) {
		if (isRight()) {
			return Mono.just(right());
		} else {
			// // Logging side effect
			// if (left() instanceof DatabaseError) {
			// ((DatabaseError) left()).logError();
			// } else {
			// System.err.println("reactiveFoldOrElse: Left value encountered: " + left());
			// }
			return orElse.get(); // must return Mono.empty() or any other Mono<R>
		}
	}

	/**
	 * Simple fold: when Left, logs the error using the provided logger and returns
	 * null;
	 * when Right, returns the R value.
	 *
	 * @param log the logger with an error(String, Object) method (e.g., slf4j
	 *            Logger)
	 * @param <L> the type of the Left (should have a message() method)
	 * @param <R> the type of the Right
	 * @return R if Right; null if Left
	 */
	default <LOG> R simpleFold(LOG log) {
		if (isLeft()) {
			try {
				// Try calling log.error("Error: {}", left().message());
				LOG logger = log;
				Object value = left();
				String messageMethodName = "error";
				if (value instanceof DatabaseError dbErr) {
					String msg = dbErr.message();
					if (msg != null && (msg.contains("No node found") || msg.contains("empty"))) {
						messageMethodName = "debug";
					}
				}
				// Use reflection to call left().message()
				java.lang.reflect.Method messageMethod = value.getClass().getMethod("message");
				Object msg = messageMethod.invoke(value);
				// Use reflection to call log.error(String, Object)
				java.lang.reflect.Method errorMethod = logger.getClass().getMethod(messageMethodName, String.class,
						Object.class);
				errorMethod.invoke(logger, "Error: {}", msg);
			} catch (Exception e) {
				// Fall back to basic log if unable
				try {
					java.lang.reflect.Method errorMethod = log.getClass().getMethod("error", String.class,
							Object[].class);
					errorMethod.invoke(log, "Error: {}", new Object[] { left() });
				} catch (Exception ignore) {
					// ignore
				}
			}
			return null;
		} else {
			return right();
		}
	}

}
