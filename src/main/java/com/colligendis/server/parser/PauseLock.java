package com.colligendis.server.parser;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Serializes exclusive parser work (e.g. bulk Numista PHP refresh) across concurrent
 * {@link com.colligendis.server.parser.numista.NumistaPipeline} runs.
 *
 * <p>
 * Use {@link #awaitIdle()} before non-exclusive reads while another parse may be
 * refreshing shared data. Use {@link #runExclusiveOrElse(Supplier, Supplier)} when
 * a cache miss may trigger refresh: one caller runs {@code exclusiveWork}, the rest
 * wait and run {@code afterIdleWork}.
 */
@Slf4j
public class PauseLock {

	private final String name;
	private final Semaphore mutex = new Semaphore(1);
	private final BaseLogger baseLogger;

	public PauseLock(String name) {
		this(name, null);
	}

	public PauseLock(String name, BaseLogger baseLogger) {
		this.name = name;
		this.baseLogger = baseLogger;
	}

	/**
	 * Waits until no exclusive work holds the lock, then completes without acquiring it.
	 */
	public Mono<Void> awaitIdle() {
		return Mono.fromCallable(() -> {
			debug("awaitIdle: waiting for exclusive work to finish");
			mutex.acquire();
			mutex.release();
			debug("awaitIdle: lock is idle");
			return null;
		})
				.subscribeOn(Schedulers.boundedElastic())
				.then();
	}

	/**
	 * Runs {@code work} exclusively. Concurrent callers block until the current work
	 * finishes, then each runs {@code work} in turn.
	 */
	public <T> Mono<T> runExclusive(Supplier<Mono<T>> work) {
		return Mono.fromCallable(() -> {
			debug("runExclusive: acquiring lock");
			mutex.acquire();
			debug("runExclusive: lock acquired");
			return true;
		})
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(acquired -> Mono.defer(work)
						.doFinally(signal -> {
							mutex.release();
							debug("runExclusive: lock released ({})", signal);
						}));
	}

	/**
	 * If the lock is free, runs {@code exclusiveWork} under the lock. Otherwise waits for
	 * idle and runs {@code afterIdleWork} without holding the lock.
	 */
	public <T> Mono<T> runExclusiveOrElse(Supplier<Mono<T>> exclusiveWork, Supplier<Mono<T>> afterIdleWork) {
		return Mono.fromCallable(() -> mutex.tryAcquire())
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(acquired -> {
					if (acquired) {
						debug("runExclusiveOrElse: running exclusive work");
						return Mono.defer(exclusiveWork)
								.doFinally(signal -> {
									mutex.release();
									debug("runExclusiveOrElse: exclusive work finished ({})", signal);
								});
					}
					debug("runExclusiveOrElse: waiting for exclusive work, then follow-up");
					return awaitIdle().then(Mono.defer(afterIdleWork));
				});
	}

	private void debug(String format, Object... args) {
		if (baseLogger != null) {
			baseLogger.debugOrange(name + ": " + format, args);
		} else {
			log.debug(name + ": " + format, args);
		}
	}

	public String getName() {
		return name;
	}
}
