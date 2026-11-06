package com.shilapi.xcertplay.compat.mfi;

public final class MfiBackendSelectionPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        requiresExplicitSelection();
        localIsTestOnly();
        productionTargetsRemainExplicit();
        failuresNeverSwitchBackends();
        System.out.println("MfiBackendSelectionPolicyTest: PASS (" + checks + " checks)");
    }

    private static void requiresExplicitSelection() {
        expectSelectionFailure(new Runnable() { public void run() {
            MfiBackendSelectionPolicy.resolve(null, false);
        }});
        expectSelectionFailure(new Runnable() { public void run() {
            MfiBackendSelectionPolicy.resolve("  ", false);
        }});
        expectSelectionFailure(new Runnable() { public void run() {
            MfiBackendSelectionPolicy.resolve("unknown", false);
        }});
    }

    private static void localIsTestOnly() {
        expectSelectionFailure(new Runnable() { public void run() {
            MfiBackendSelectionPolicy.resolve("LOCAL", false);
        }});
        eq(MfiBackendSelectionPolicy.Backend.LOCAL,
                MfiBackendSelectionPolicy.resolve("local", true));
    }

    private static void productionTargetsRemainExplicit() {
        eq(MfiBackendSelectionPolicy.Backend.USB_CH341,
                MfiBackendSelectionPolicy.resolve("USB_CH341", false));
        eq(MfiBackendSelectionPolicy.Backend.I2C,
                MfiBackendSelectionPolicy.resolve("i2c", false));
        eq(MfiBackendSelectionPolicy.Backend.REMOTE,
                MfiBackendSelectionPolicy.resolve("REMOTE", false));
    }

    private static void failuresNeverSwitchBackends() {
        for (MfiBackendSelectionPolicy.Backend backend :
                MfiBackendSelectionPolicy.Backend.values()) {
            truth(!MfiBackendSelectionPolicy.mayAutomaticallyFallback(backend));
        }
    }

    private static void eq(Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(expected + " != " + actual);
    }
    private static void truth(boolean value) {
        checks++;
        if (!value) throw new AssertionError("expected true");
    }
    private static void expectSelectionFailure(Runnable action) {
        checks++;
        try { action.run(); }
        catch (MfiBackendSelectionPolicy.SelectionException expected) { return; }
        throw new AssertionError("expected selection failure");
    }
}
