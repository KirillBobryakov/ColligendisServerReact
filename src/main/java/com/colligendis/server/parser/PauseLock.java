package com.colligendis.server.parser;

import java.util.concurrent.atomic.AtomicBoolean;

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
 *         .then(doSomething())
 *         .doFinally(signal -> pauseLock.resume());
 * </pre>
 */
@Slf4j
public class PauseLock {

    private final String name;
    private final AtomicBoolean lock = new AtomicBoolean(false);
    private volatile boolean paused = false;
    private volatile Sinks.One<Void> gate = Sinks.one();

    public PauseLock(String name) {
        this.name = name;
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
        if (log.isDebugEnabled()) {
            log.debug("{}: pause requested, currently paused: {}", name, paused);
        }

        if (lock.compareAndSet(false, true)) {
            paused = true;
            gate = Sinks.one();
            log.info("{}: pause activated", name);
            return true;
        } else {
            log.debug("{}: pause already active", name);
            return false;
        }
    }

    /**
     * Resumes processing. All waiting {@link #awaitIfPaused()} calls will complete.
     */
    public void resume() {
        if (log.isDebugEnabled()) {
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
        if (log.isDebugEnabled()) {
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
