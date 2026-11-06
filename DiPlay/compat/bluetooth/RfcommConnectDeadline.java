package com.shilapi.xcertplay.compat.bluetooth;

/** Monotonic overall deadline shared by all RFCOMM fallback attempts. */
public final class RfcommConnectDeadline {
    private RfcommConnectDeadline() {}

    public static long deadlineAfter(long nowNanos, long timeoutMillis) {
        if (timeoutMillis <= 0L || timeoutMillis > 300000L) {
            throw new IllegalArgumentException("timeoutMillis must be in 1..300000");
        }
        return nowNanos + timeoutMillis * 1000000L;
    }

    public static long remainingNanos(long nowNanos, long deadlineNanos) {
        long remaining = deadlineNanos - nowNanos;
        return remaining > 0L ? remaining : 0L;
    }

    public static long remainingMillisCeil(long nowNanos, long deadlineNanos) {
        long remaining = remainingNanos(nowNanos, deadlineNanos);
        if (remaining == 0L) return 0L;
        return 1L + (remaining - 1L) / 1000000L;
    }
}
