package com.shilapi.xcertplay.compat.mfi;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One monotonic deadline and cancellation boundary for a complete MFi operation.
 * Providers must pass the same instance through certificate reads and challenge signing.
 * Java 7 / Android API 17 compatible; this class does not implement an MFi provider.
 */
public final class MfiOperationContext {
    public static final long MAX_TIMEOUT_MILLIS = 300000L;

    public interface NanoClock {
        long nanoTime();
    }

    public static final class Cancellation {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        public void cancel() { cancelled.set(true); }
        public boolean isCancelled() { return cancelled.get(); }
    }

    public static final class OperationCancelledException extends IllegalStateException {
        public OperationCancelledException(String message) { super(message); }
    }

    public static final class OperationTimeoutException extends IllegalStateException {
        public OperationTimeoutException(String message) { super(message); }
    }

    private static final NanoClock SYSTEM_CLOCK = new NanoClock() {
        public long nanoTime() { return System.nanoTime(); }
    };

    private final NanoClock clock;
    private final long startedAtNanos;
    private final long timeoutNanos;
    private final long generation;
    private final Cancellation cancellation;

    public static MfiOperationContext start(long timeoutMillis, long generation,
            Cancellation cancellation) {
        return new MfiOperationContext(SYSTEM_CLOCK, System.nanoTime(),
                timeoutMillis, generation, cancellation);
    }

    public MfiOperationContext(NanoClock clock, long startedAtNanos,
            long timeoutMillis, long generation, Cancellation cancellation) {
        if (clock == null) throw new IllegalArgumentException("clock == null");
        if (cancellation == null) throw new IllegalArgumentException("cancellation == null");
        if (timeoutMillis < 0 || timeoutMillis > MAX_TIMEOUT_MILLIS)
            throw new IllegalArgumentException("timeoutMillis must be in 0..300000");
        this.clock = clock;
        this.startedAtNanos = startedAtNanos;
        this.timeoutNanos = timeoutMillis * 1000000L;
        this.generation = generation;
        this.cancellation = cancellation;
    }

    public long generation() { return generation; }
    public long deadlineNanos() { return startedAtNanos + timeoutNanos; }
    public boolean isCancelled() { return cancellation.isCancelled(); }
    public void cancel() { cancellation.cancel(); }

    /** Remaining time, rounded up to milliseconds and never reset by sub-operations. */
    public long remainingMillis() {
        long elapsed = clock.nanoTime() - startedAtNanos;
        if (elapsed < 0L || elapsed >= timeoutNanos) return 0L;
        long remaining = timeoutNanos - elapsed;
        return (remaining + 999999L) / 1000000L;
    }

    public long remainingNanos() {
        long elapsed = clock.nanoTime() - startedAtNanos;
        if (elapsed < 0L || elapsed >= timeoutNanos) return 0L;
        return timeoutNanos - elapsed;
    }

    /**
     * Call before and after every blocking provider/bus operation.
     * Interrupt status is observed, never cleared; stale generations are cancelled.
     */
    public void checkActive(long activeGeneration) {
        if (Thread.currentThread().isInterrupted())
            throw new OperationCancelledException("MFi operation interrupted");
        if (cancellation.isCancelled())
            throw new OperationCancelledException("MFi operation cancelled");
        if (activeGeneration != generation)
            throw new OperationCancelledException("MFi session generation changed");
        if (remainingNanos() == 0L)
            throw new OperationTimeoutException("MFi operation deadline exceeded");
    }
}
