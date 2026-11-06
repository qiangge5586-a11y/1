package com.shilapi.xcertplay.compat.iap2;

/** Deterministic arithmetic mirror of Iap2CsmChannel's one-deadline send budget. */
public final class Iap2CsmSendDeadline {
    public static final long MAX_TIMEOUT_MILLIS = 300000L;
    private final long deadlineNanos;

    public Iap2CsmSendDeadline(long startedAtNanos, long timeoutMillis) {
        if (timeoutMillis < 0 || timeoutMillis > MAX_TIMEOUT_MILLIS)
            throw new IllegalArgumentException("timeoutMillis must be in 0..300000");
        this.deadlineNanos = startedAtNanos + 1000000L * timeoutMillis;
    }

    /** Returns ceil(remaining nanoseconds / 1ms), clamped to original channel maximum. */
    public long remainingMillis(long nowNanos) {
        long remaining = deadlineNanos - nowNanos;
        if (remaining <= 0) return 0L;
        long roundedUp = (remaining + 999999L) / 1000000L;
        return Math.min(MAX_TIMEOUT_MILLIS, roundedUp);
    }

    public long deadlineNanos() { return deadlineNanos; }
}
