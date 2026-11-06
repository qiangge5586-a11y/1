package com.shilapi.xcertplay.compat.iap2;

public final class Iap2WirelessDeadlineBudgetTest {
    private static int checks;

    public static void main(String[] args) {
        phaseDeadlineAndTransition();
        phaseCannotExtendSessionDeadline();
        expiryIsStickyAndBlocksTransitions();
        invalidBudgetsAreRejected();
        overflowSaturates();
        System.out.println("Iap2WirelessDeadlineBudgetTest: PASS (" + checks + " checks)");
    }

    private static void phaseDeadlineAndTransition() {
        Iap2WirelessDeadlineBudget b = Iap2WirelessDeadlineBudget.start(100L, 10000L);
        truth(b.enterPhase("RFCOMM", 100L, 3000L));
        eq(3100L, b.getPhaseDeadlineMillis());
        eq(3000L, b.remainingMillis(100L));
        eq(null, b.expireIfDue(3099L));
        truth(b.enterPhase("BOOTSTRAP_AUTH", 2000L, 4000L));
        eq(6000L, b.getPhaseDeadlineMillis());
        eq("BOOTSTRAP_AUTH", b.getPhase());
    }

    private static void phaseCannotExtendSessionDeadline() {
        Iap2WirelessDeadlineBudget b = Iap2WirelessDeadlineBudget.start(0L, 5000L);
        truth(b.enterPhase("TUNNEL", 1000L, 9000L));
        eq(5000L, b.getPhaseDeadlineMillis());
        eq(1000L, b.remainingMillis(4000L));
        eq("SESSION_TOTAL", b.expireIfDue(5000L));
    }

    private static void expiryIsStickyAndBlocksTransitions() {
        Iap2WirelessDeadlineBudget b = Iap2WirelessDeadlineBudget.start(0L, 8000L);
        truth(b.enterPhase("RFCOMM", 0L, 1000L));
        eq("RFCOMM", b.expireIfDue(1000L));
        eq("RFCOMM", b.expireIfDue(1500L));
        falsity(b.enterPhase("TUNNEL", 1500L, 5000L));
        eq(0L, b.remainingMillis(1500L));
    }

    private static void invalidBudgetsAreRejected() {
        expectIllegal(new Runnable() {
            public void run() { Iap2WirelessDeadlineBudget.start(0L, 0L); }
        });
        expectIllegal(new Runnable() {
            public void run() {
                Iap2WirelessDeadlineBudget.start(0L, 10L).enterPhase("X", 0L, 0L);
            }
        });
    }

    private static void overflowSaturates() {
        Iap2WirelessDeadlineBudget b =
                Iap2WirelessDeadlineBudget.start(Long.MAX_VALUE - 5L, 10L);
        eq(Long.MAX_VALUE, b.getSessionDeadlineMillis());
        truth(b.enterPhase("NEAR_MAX", Long.MAX_VALUE - 5L, 10L));
        eq(Long.MAX_VALUE, b.getPhaseDeadlineMillis());
        eq(5L, b.remainingMillis(Long.MAX_VALUE - 5L));
    }

    private static void expectIllegal(Runnable r) {
        checks++;
        try { r.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }
    private static void truth(boolean v) { checks++; if (!v) throw new AssertionError("expected true"); }
    private static void falsity(boolean v) { checks++; if (v) throw new AssertionError("expected false"); }
    private static void eq(long e, long a) { checks++; if (e != a) throw new AssertionError(e + " != " + a); }
    private static void eq(String e, String a) {
        checks++;
        if (e == null ? a != null : !e.equals(a)) throw new AssertionError(e + " != " + a);
    }
}
