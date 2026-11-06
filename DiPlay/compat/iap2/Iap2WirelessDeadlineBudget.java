package com.shilapi.xcertplay.compat.iap2;

/**
 * Caller-configured, monotonic deadline budget for one wireless session.
 * Protocol retry intervals remain owned by their protocol layers; this class
 * only bounds phase waits and the overall session without inventing defaults.
 */
public final class Iap2WirelessDeadlineBudget {
    private final long sessionDeadlineMillis;
    private long phaseDeadlineMillis;
    private String phase = "SESSION";
    private String expiredPhase;

    private Iap2WirelessDeadlineBudget(long nowMillis, long totalBudgetMillis) {
        sessionDeadlineMillis = addSaturated(nowMillis, totalBudgetMillis);
        phaseDeadlineMillis = sessionDeadlineMillis;
    }

    public static Iap2WirelessDeadlineBudget start(long nowMillis, long totalBudgetMillis) {
        if (totalBudgetMillis <= 0L) {
            throw new IllegalArgumentException("totalBudgetMillis must be positive");
        }
        return new Iap2WirelessDeadlineBudget(nowMillis, totalBudgetMillis);
    }

    /**
     * Starts a named phase. Its deadline is capped by the original session
     * deadline, so retries or phase changes can never extend the whole session.
     */
    public synchronized boolean enterPhase(String name, long nowMillis, long phaseBudgetMillis) {
        if (name == null || name.length() == 0) throw new IllegalArgumentException("phase name");
        if (phaseBudgetMillis <= 0L) {
            throw new IllegalArgumentException("phaseBudgetMillis must be positive");
        }
        if (expiredPhase != null || expireIfDue(nowMillis) != null) return false;
        phase = name;
        long proposed = addSaturated(nowMillis, phaseBudgetMillis);
        phaseDeadlineMillis = earlierDeadline(proposed, sessionDeadlineMillis);
        return true;
    }

    /** Returns the expired phase, or null while the current phase remains in budget. */
    public synchronized String expireIfDue(long nowMillis) {
        if (expiredPhase != null) return expiredPhase;
        if (nowMillis >= sessionDeadlineMillis) {
            expiredPhase = "SESSION_TOTAL";
        } else if (nowMillis >= phaseDeadlineMillis) {
            expiredPhase = phase;
        }
        return expiredPhase;
    }

    public synchronized long remainingMillis(long nowMillis) {
        if (expireIfDue(nowMillis) != null) return 0L;
        long remaining = phaseDeadlineMillis - nowMillis;
        return remaining < 0L ? Long.MAX_VALUE : remaining;
    }

    public synchronized long remainingSessionMillis(long nowMillis) {
        if (expireIfDue(nowMillis) != null) return 0L;
        long remaining = sessionDeadlineMillis - nowMillis;
        return remaining < 0L ? Long.MAX_VALUE : remaining;
    }

    public synchronized String getPhase() { return phase; }
    public synchronized String getExpiredPhase() { return expiredPhase; }
    public long getSessionDeadlineMillis() { return sessionDeadlineMillis; }
    public synchronized long getPhaseDeadlineMillis() { return phaseDeadlineMillis; }

    private static long earlierDeadline(long a, long b) {
        return a <= b ? a : b;
    }

    private static long addSaturated(long value, long positiveDelta) {
        if (positiveDelta > 0L && value > Long.MAX_VALUE - positiveDelta) {
            return Long.MAX_VALUE;
        }
        return value + positiveDelta;
    }
}
