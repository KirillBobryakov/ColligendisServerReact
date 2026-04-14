package com.colligendis.server.parser;

import java.util.concurrent.atomic.AtomicBoolean;

import com.colligendis.server.logger.BaseLogger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * A reusable pause/resume mechanism for reactive parsers.
 * 
 * Can be used as instance field or static field:
 * 
 * <pre>
 * // Instance usage
 * private final PauseLock pauseLock = new PauseLock("MyParser");
 * 
 * // Static usage
 * private static final PauseLock pauseLock = new PauseLock("MyParser");
 * </pre>
 * 
 * Usage in reactive chains:
 * 
 * <pre>
 * pauseLock.awaitIfPaused()
 * 		.then(doSomething())
 * 		.doFinally(signal -> pauseLock.resume());
 * </pre>
 */
@Slf4j
public class PauseLock {

	private final String name;
	private final AtomicBoolean lock = new AtomicBoolean(false);
	private volatile boolean paused = false;
	private volatile Sinks.One<Void> gate = Sinks.one();
	/** When null, coordination messages go to SLF4J (use for JVM-wide shared locks). */
	private final BaseLogger baseLogger;

	/**
	 * Global coordination lock: safe to share across all concurrent parses of this
	 * parser type. Pass a per-call {@link BaseLogger} only if pause traces must
	 * appear in the page pipeline log (rare); for preventing duplicate DB writes,
	 * prefer this constructor.
	 */
	public PauseLock(String name) {
		this(name, null);
	}

	public PauseLock(String name, BaseLogger baseLogger) {
		this.name = name;
		this.baseLogger = baseLogger;
	}

	/**
	 * Activates pause. Subsequent calls to {@link #awaitIfPaused()} will block
	 * until {@link #resume()} is called.
	 * 
	 * Thread-safe: only the first concurrent call activates the pause.
	 * 
	 * @return true if this call acquired the lock (caller should do the work),
	 *         false if another thread already holds the lock (caller should wait)
	 */
	public boolean pause() {
		if (baseLogger != null) {
			baseLogger.debugOrange("{}: pause requested, currently paused: {}", name, paused);
		} else {
			log.debug("{}: pause requested, currently paused: {}", name, paused);
		}

		if (lock.compareAndSet(false, true)) {
			paused = true;
			gate = Sinks.one();
			if (baseLogger != null) {
				baseLogger.debugOrange("{}: pause activated", name);
			} else {
				log.debug("{}: pause activated", name);
			}
			return true;
		} else {
			if (baseLogger != null) {
				baseLogger.debugOrange("{}: pause already active", name);
			} else {
				log.debug("{}: pause already active", name);
			}
			return false;
		}
	}

	/**
	 * Resumes processing. All waiting {@link #awaitIfPaused()} calls will complete.
	 */
	public void resume() {
		if (baseLogger != null) {
			baseLogger.debugGreen("{}: resume requested, currently paused: {}", name, paused);
		} else {
			log.debug("{}: resume requested, currently paused: {}", name, paused);
		}
		paused = false;
		lock.set(false);
		gate.tryEmitEmpty();
		log.info("{}: resumed", name);
	}

	/**
	 * Returns a Mono that completes immediately if not paused,
	 * or waits until {@link #resume()} is called if paused.
	 */
	public Mono<Void> awaitIfPaused() {
		if (!paused) {
			return Mono.empty();
		}
		if (baseLogger != null) {
			baseLogger.debugOrange("{}: awaiting resume", name);
		} else {
			log.debug("{}: awaiting resume", name);
		}
		return gate.asMono();
	}

	/**
	 * @return true if currently paused
	 */
	public boolean isPaused() {
		return paused;
	}

	/**
	 * @return the name of this pause lock (for logging/debugging)
	 */
	public String getName() {
		return name;
	}
}
