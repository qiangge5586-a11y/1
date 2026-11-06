package com.shilapi.xcertplay.compat.iap2;

public final class Iap2LinkModelPumpDriverTest {
    public static void main(String[] args) throws Exception {
        Iap2LinkEngineModel engine = new Iap2LinkEngineModel(
                new Iap2LinkNegotiationModel(Iap2LinkNegotiationModel.Config.wirelessDefault()));
        Iap2LinkModelPumpDriver driver = new Iap2LinkModelPumpDriver(engine, false);
        driver.start(1000L);
        byte[] marker = driver.drainOutput();
        equal(6, marker.length, "startup marker length");
        for (int i = 0; i < marker.length; i++) {
            equal(Iap2LinkFrameCodec.IAP2_MARKER[i] & 255, marker[i] & 255,
                    "startup marker byte " + i);
        }
        truth(!driver.isReady(), "not ready before peer synchronization");
        truth(!driver.isTerminal(), "not terminal after startup marker");
        equal(2000L, driver.nextDeadlineMillis(), "exact marker resend deadline");
        driver.onTimer(1100L);
        truth(!driver.isTerminal(), "timer poll keeps detecting state alive");
        equal(2000L, driver.nextDeadlineMillis(), "deadline remains stable before expiry");
        driver.onTimer(2000L);
        equal(3000L, driver.nextDeadlineMillis(), "marker deadline advances by source interval");
        System.out.println("Iap2LinkModelPumpDriverTest: 13 checks passed");
    }

    private static void equal(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": " + expected + " != " + actual);
    }
    private static void truth(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
