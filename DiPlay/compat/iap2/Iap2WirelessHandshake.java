package com.shilapi.xcertplay.compat.iap2;

/**
 * Offline semantic tracker for the two-stage wireless iAP2 handshake.
 *
 * This class does not encode/decode iAP2 packets, open Bluetooth sockets, or
 * create tunnel streams. It tracks confirmed milestones and rejects stale or
 * out-of-order callbacks so that a future transport adapter can be tested
 * without touching a vehicle.
 */
public final class Iap2WirelessHandshake {
    public enum Result { ACCEPTED, DUPLICATE, STALE_SESSION, INVALID_ORDER }
    public enum Phase {
        IDLE, RFCOMM_CONNECTED, BOOTSTRAP_IDENTIFIED, BOOTSTRAP_AUTHENTICATED,
        HANDOFF_NEGOTIATING, TUNNEL_STREAM_RECEIVED, TUNNEL_OPENED,
        TUNNEL_AUTHENTICATED, ACTIVE, FAILED
    }

    private long generation;
    private Phase phase = Phase.IDLE;
    private boolean rfcommConnected, bootstrapIdentified, bootstrapAuthenticated;
    private boolean carPlayAvailabilityReceived, carPlayAvailable, startSessionSent, wifiConfigRequested;
    private boolean wifiConfigSent, transportIdentifierNotified;
    private boolean tunnelStreamReceived, tunnelOpened, tunnelAuthenticated, tunnelControlActive;
    private String failureReason;

    public synchronized long beginSession() {
        generation++;
        phase = Phase.IDLE;
        rfcommConnected = bootstrapIdentified = bootstrapAuthenticated = false;
        carPlayAvailabilityReceived = carPlayAvailable = startSessionSent = wifiConfigRequested = false;
        wifiConfigSent = transportIdentifierNotified = false;
        tunnelStreamReceived = tunnelOpened = tunnelAuthenticated = tunnelControlActive = false;
        failureReason = null;
        return generation;
    }

    public synchronized Result onRfcommConnected(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (rfcommConnected) return Result.DUPLICATE;
        if (phase != Phase.IDLE) return Result.INVALID_ORDER;
        rfcommConnected = true;
        phase = Phase.RFCOMM_CONNECTED;
        return Result.ACCEPTED;
    }

    public synchronized Result onBootstrapIdentificationComplete(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (bootstrapIdentified) return Result.DUPLICATE;
        if (!rfcommConnected || phase == Phase.FAILED) return Result.INVALID_ORDER;
        bootstrapIdentified = true;
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result onBootstrapMfiAuthenticated(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (bootstrapAuthenticated) return Result.DUPLICATE;
        if (!bootstrapIdentified || phase == Phase.FAILED) return Result.INVALID_ORDER;
        bootstrapAuthenticated = true;
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result onCarPlayAvailability(long token, boolean available) {
        if (stale(token)) return Result.STALE_SESSION;
        if (!bootstrapAuthenticated || phase == Phase.FAILED) return Result.INVALID_ORDER;
        if (carPlayAvailabilityReceived && carPlayAvailable == available) return Result.DUPLICATE;
        carPlayAvailabilityReceived = true;
        carPlayAvailable = available;
        if (!available) {
            startSessionSent = false;
            wifiConfigRequested = wifiConfigSent = false;
            transportIdentifierNotified = false;
        }
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result onStartSessionSent(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (startSessionSent) return Result.DUPLICATE;
        if (!carPlayAvailable || phase == Phase.FAILED) return Result.INVALID_ORDER;
        startSessionSent = true;
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result onWifiConfigurationRequested(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (wifiConfigRequested) return Result.DUPLICATE;
        if (!bootstrapAuthenticated || phase == Phase.FAILED) return Result.INVALID_ORDER;
        wifiConfigRequested = true;
        phase = Phase.HANDOFF_NEGOTIATING;
        return Result.ACCEPTED;
    }

    public synchronized Result onWifiConfigurationSent(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (wifiConfigSent) return Result.DUPLICATE;
        if (!wifiConfigRequested || phase == Phase.FAILED) return Result.INVALID_ORDER;
        wifiConfigSent = true;
        phase = Phase.HANDOFF_NEGOTIATING;
        return Result.ACCEPTED;
    }

    public synchronized Result onTransportIdentifierNotified(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (transportIdentifierNotified) return Result.DUPLICATE;
        if (!bootstrapAuthenticated || phase == Phase.FAILED) return Result.INVALID_ORDER;
        transportIdentifierNotified = true;
        phase = Phase.HANDOFF_NEGOTIATING;
        return Result.ACCEPTED;
    }

    public synchronized Result onTunnelStreamReceived(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (tunnelStreamReceived) return Result.DUPLICATE;
        if (!bootstrapAuthenticated || phase == Phase.FAILED) return Result.INVALID_ORDER;
        tunnelStreamReceived = true;
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result onTunnelOpened(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (tunnelOpened) return Result.DUPLICATE;
        if (!tunnelStreamReceived || phase == Phase.FAILED) return Result.INVALID_ORDER;
        tunnelOpened = true;
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result onTunnelMfiAuthenticated(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (tunnelAuthenticated) return Result.DUPLICATE;
        if (!tunnelOpened || phase == Phase.FAILED) return Result.INVALID_ORDER;
        tunnelAuthenticated = true;
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result onTunnelControlActive(long token) {
        if (stale(token)) return Result.STALE_SESSION;
        if (phase == Phase.ACTIVE) return Result.DUPLICATE;
        if (!tunnelAuthenticated || phase == Phase.FAILED) return Result.INVALID_ORDER;
        tunnelControlActive = true;
        refreshPhase();
        return Result.ACCEPTED;
    }

    public synchronized Result fail(long token, String reason) {
        if (stale(token)) return Result.STALE_SESSION;
        if (phase == Phase.FAILED) return Result.DUPLICATE;
        failureReason = reason == null ? "unspecified" : reason;
        phase = Phase.FAILED;
        return Result.ACCEPTED;
    }

    public synchronized Phase getPhase() { return phase; }
    public synchronized long getGeneration() { return generation; }
    public synchronized String getFailureReason() { return failureReason; }
    public synchronized boolean hasCarPlayAvailability() { return carPlayAvailabilityReceived; }
    public synchronized boolean isCarPlayAvailable() { return carPlayAvailabilityReceived && carPlayAvailable; }
    public synchronized boolean isBootstrapReady() {
        return rfcommConnected && bootstrapIdentified && bootstrapAuthenticated;
    }
    public synchronized boolean isHandoffReady() {
        return carPlayAvailable && startSessionSent && wifiConfigRequested
                && wifiConfigSent && transportIdentifierNotified;
    }
    public synchronized boolean isTunnelReady() {
        return tunnelStreamReceived && tunnelOpened && tunnelAuthenticated;
    }

    /** Diagnostic only: tunnel reception is not gated on these optional handoff observations. */
    public synchronized String getMissingHandoffMilestones() {
        StringBuilder b = new StringBuilder();
        appendMissing(b, !carPlayAvailable, "CarPlayAvailability");
        appendMissing(b, !startSessionSent, "StartSessionSent");
        appendMissing(b, !wifiConfigRequested, "WiFiConfigRequested");
        appendMissing(b, !wifiConfigSent, "WiFiConfigSent");
        appendMissing(b, !transportIdentifierNotified, "TransportIdentifierNotified");
        return b.toString();
    }

    private void refreshPhase() {
        if (phase == Phase.FAILED) return;
        if (tunnelControlActive) phase = Phase.ACTIVE;
        else if (tunnelAuthenticated) phase = Phase.TUNNEL_AUTHENTICATED;
        else if (tunnelOpened) phase = Phase.TUNNEL_OPENED;
        else if (tunnelStreamReceived) phase = Phase.TUNNEL_STREAM_RECEIVED;
        else if (carPlayAvailabilityReceived || startSessionSent || wifiConfigRequested || wifiConfigSent || transportIdentifierNotified) phase = Phase.HANDOFF_NEGOTIATING;
        else if (bootstrapAuthenticated) phase = Phase.BOOTSTRAP_AUTHENTICATED;
        else if (bootstrapIdentified) phase = Phase.BOOTSTRAP_IDENTIFIED;
        else if (rfcommConnected) phase = Phase.RFCOMM_CONNECTED;
        else phase = Phase.IDLE;
    }

    private boolean stale(long token) { return token != generation || token == 0; }
    private static void appendMissing(StringBuilder b, boolean missing, String name) {
        if (missing) {
            if (b.length() > 0) b.append(',');
            b.append(name);
        }
    }
}
