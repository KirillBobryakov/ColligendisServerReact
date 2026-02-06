package com.colligendis.server.util;

import java.util.function.Function;

import com.colligendis.server.database.exception.NotFoundError;

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
                String messageMethodName = "";
                if (value instanceof NotFoundError) {
                    messageMethodName = "warn";
                } else {
                    messageMethodName = "error";
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
