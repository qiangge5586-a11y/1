package com.shilapi.xcertplay.compat.iap2;

/**
 * Offline composition boundary for the audited iAP2 link sub-models.
 *
 * This class coordinates lifecycle/readiness only. Peer window values are
 * supplied by the caller from a separately verified negotiation result;
 * it does not claim to be the original transport engine and performs no I/O.
 */
public final class Iap2LinkEngineModel {
    public enum State { IDLE, NEGOTIATING, READY, CLOSED }

    private final Iap2LinkNegotiationModel negotiation;
    private final Iap2OutboundWindowModel outbound;
    private final Iap2ReceiveWindowModel inbound;
    private boolean started;
    private boolean closed;

    public Iap2LinkEngineModel(Iap2LinkNegotiationModel negotiation,
            Iap2OutboundWindowModel outbound, Iap2ReceiveWindowModel inbound) {
        if (negotiation == null || outbound == null || inbound == null) {
            throw new NullPointerException("link model component");
        }
        this.negotiation = negotiation;
        this.outbound = outbound;
        this.inbound = inbound;
    }

    public synchronized void start(boolean initiator, long nowMillis) {
        if (closed) throw new IllegalStateException("link is closed");
        if (started) return;
        started = true;
        negotiation.start(initiator, nowMillis);
    }

    public synchronized void feedNegotiation(byte[] bytes, long nowMillis) {
        requireStarted();
        if (closed) return;
        negotiation.feed(bytes, nowMillis);
    }

    public synchronized boolean initiateSynchronizationIfDetecting(long nowMillis) {
        requireStarted();
        if (closed) return false;
        return negotiation.initiateSynchronizationIfDetecting(nowMillis);
    }

    public synchronized State advanceNegotiationTime(long nowMillis) {
        requireStarted();
        if (closed) return State.CLOSED;
        negotiation.advanceTime(nowMillis);
        if (negotiation.getState() == Iap2LinkNegotiationModel.State.DEAD) {
            closed = true;
            return State.CLOSED;
        }
        return state();
    }

    public synchronized Iap2OutboundWindowModel.Result send(
            int sessionId, byte[] payload, long nowMillis) {
        requireReady();
        return outbound.send(sessionId, payload, nowMillis);
    }

    public synchronized Iap2OutboundWindowModel.Result acknowledge(
            int acknowledgement, long nowMillis) {
        requireReady();
        return outbound.acknowledge(acknowledgement, nowMillis);
    }

    public synchronized Iap2OutboundWindowModel.Result requestMissing(
            byte[] missingSequences, long nowMillis) {
        requireReady();
        return outbound.requestMissing(missingSequences, nowMillis);
    }

    public synchronized Iap2ReceiveWindowModel.Result receive(
            Iap2ReceiveWindowModel.Packet packet, long nowMillis) {
        requireReady();
        return inbound.receive(packet, nowMillis);
    }

    /** Advances both independent timers at one deterministic clock instant. */
    public synchronized TimerResult advanceTimers(long nowMillis) {
        requireReady();
        return new TimerResult(outbound.advanceTime(nowMillis), inbound.advanceTime(nowMillis));
    }

    public synchronized void endOfStream() {
        if (closed) return;
        closed = true;
        if (started) negotiation.feedEof();
    }

    public synchronized State state() {
        if (closed || negotiation.getState() == Iap2LinkNegotiationModel.State.DEAD
                || outbound.getTerminalReason() != null
                || inbound.getTerminalReason() != null) {
            return State.CLOSED;
        }
        if (negotiation.isReady()) return State.READY;
        if (started) return State.NEGOTIATING;
        return State.IDLE;
    }

    public synchronized byte[] takeNegotiationOutput() {
        return negotiation.takeOutput();
    }

    public synchronized String getTerminalReason() {
        if (outbound.getTerminalReason() != null) return outbound.getTerminalReason();
        if (inbound.getTerminalReason() != null) return inbound.getTerminalReason();
        return negotiation.getTerminalReason();
    }

    private void requireStarted() {
        if (!started) throw new IllegalStateException("link has not been started");
    }

    private void requireReady() {
        if (state() != State.READY) {
            throw new IllegalStateException("iAP2 link is not ready: " + state());
        }
    }

    public static final class TimerResult {
        public final Iap2OutboundWindowModel.Result outbound;
        public final Iap2ReceiveWindowModel.Result inbound;
        private TimerResult(Iap2OutboundWindowModel.Result outbound,
                Iap2ReceiveWindowModel.Result inbound) {
            this.outbound = outbound;
            this.inbound = inbound;
        }
    }
}
