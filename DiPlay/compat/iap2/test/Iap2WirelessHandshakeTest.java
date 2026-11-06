package com.shilapi.xcertplay.compat.iap2;

public final class Iap2WirelessHandshakeTest {
    private static int checks;
    public static void main(String[] args) {
        testHappyPathAndDuplicateEvents();
        testOrderingAndMissingMilestones();
        testTunnelMayArriveBeforeOptionalHandoffObservations();
        testStaleCallbacksAfterReconnect();
        testFailureIsTerminalUntilNewGeneration();
        testAvailabilityFalseAndLateNotificationsDoNotRegressTunnelPhase();
        System.out.println("Iap2WirelessHandshakeTest: PASS (" + checks + " checks)");
    }

    private static void testHappyPathAndDuplicateEvents() {
        Iap2WirelessHandshake h = new Iap2WirelessHandshake();
        long g = h.beginSession();
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onRfcommConnected(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onBootstrapIdentificationComplete(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onBootstrapMfiAuthenticated(g));
        check(h.isBootstrapReady());
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onCarPlayAvailability(g, true));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onStartSessionSent(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onWifiConfigurationRequested(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onWifiConfigurationSent(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTransportIdentifierNotified(g));
        check(h.isHandoffReady());
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelStreamReceived(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelOpened(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelMfiAuthenticated(g));
        check(h.isTunnelReady());
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelControlActive(g));
        eq(Iap2WirelessHandshake.Phase.ACTIVE, h.getPhase());
        eq(Iap2WirelessHandshake.Result.DUPLICATE, h.onTunnelControlActive(g));
        eq(Iap2WirelessHandshake.Result.DUPLICATE, h.onTunnelStreamReceived(g));
    }

    private static void testOrderingAndMissingMilestones() {
        Iap2WirelessHandshake h = new Iap2WirelessHandshake();
        long g = h.beginSession();
        eq(Iap2WirelessHandshake.Result.INVALID_ORDER, h.onBootstrapMfiAuthenticated(g));
        eq(Iap2WirelessHandshake.Result.INVALID_ORDER, h.onTunnelOpened(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onRfcommConnected(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onBootstrapIdentificationComplete(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onBootstrapMfiAuthenticated(g));
        eq("CarPlayAvailability,StartSessionSent,WiFiConfigRequested,WiFiConfigSent,TransportIdentifierNotified",
                h.getMissingHandoffMilestones());
        eq(Iap2WirelessHandshake.Result.INVALID_ORDER, h.onWifiConfigurationSent(g));
    }

    private static void testTunnelMayArriveBeforeOptionalHandoffObservations() {
        Iap2WirelessHandshake h = new Iap2WirelessHandshake();
        long g = h.beginSession();
        bootstrap(h, g);
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelStreamReceived(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelOpened(g));
        check(!h.isHandoffReady());
        eq(Iap2WirelessHandshake.Phase.TUNNEL_OPENED, h.getPhase());
    }

    private static void testStaleCallbacksAfterReconnect() {
        Iap2WirelessHandshake h = new Iap2WirelessHandshake();
        long old = h.beginSession();
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onRfcommConnected(old));
        long current = h.beginSession();
        check(current > old);
        eq(Iap2WirelessHandshake.Result.STALE_SESSION, h.onBootstrapIdentificationComplete(old));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onRfcommConnected(current));
        eq(Iap2WirelessHandshake.Phase.RFCOMM_CONNECTED, h.getPhase());
    }

    private static void testFailureIsTerminalUntilNewGeneration() {
        Iap2WirelessHandshake h = new Iap2WirelessHandshake();
        long g = h.beginSession();
        bootstrap(h, g);
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.fail(g, "RFCOMM EOF"));
        eq("RFCOMM EOF", h.getFailureReason());
        eq(Iap2WirelessHandshake.Result.INVALID_ORDER, h.onTunnelStreamReceived(g));
        eq(Iap2WirelessHandshake.Result.DUPLICATE, h.fail(g, "again"));
        long next = h.beginSession();
        check(next != g);
        eq(Iap2WirelessHandshake.Phase.IDLE, h.getPhase());
        eq(null, h.getFailureReason());
    }


    private static void testAvailabilityFalseAndLateNotificationsDoNotRegressTunnelPhase() {
        Iap2WirelessHandshake h = new Iap2WirelessHandshake();
        long g = h.beginSession();
        bootstrap(h, g);
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onCarPlayAvailability(g, false));
        check(h.hasCarPlayAvailability());
        check(!h.isCarPlayAvailable());
        eq(Iap2WirelessHandshake.Result.DUPLICATE, h.onCarPlayAvailability(g, false));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onCarPlayAvailability(g, true));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelStreamReceived(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelOpened(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelMfiAuthenticated(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onTunnelControlActive(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onWifiConfigurationRequested(g));
        eq(Iap2WirelessHandshake.Phase.ACTIVE, h.getPhase());
    }

    private static void bootstrap(Iap2WirelessHandshake h, long g) {
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onRfcommConnected(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onBootstrapIdentificationComplete(g));
        eq(Iap2WirelessHandshake.Result.ACCEPTED, h.onBootstrapMfiAuthenticated(g));
    }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check failed"); }
    private static void eq(Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
