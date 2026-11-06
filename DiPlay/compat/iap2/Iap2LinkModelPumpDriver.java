package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Adapter that drives the offline, source-audited Java model through Iap2LinkPump.
 * This is an integration harness only: it is not the original production engine.
 */
public final class Iap2LinkModelPumpDriver implements Iap2LinkPump.Driver,
        Iap2LinkPump.CommandSink, Iap2LinkPump.Readiness {
    private static final int CONTROL_SESSION_ID = Iap2LinkFrameCodec.CONTROL_SESSION_ID;
    private final Iap2LinkEngineModel engine;
    private final boolean initiator;
    private long lastNowMillis;

    public Iap2LinkModelPumpDriver(Iap2LinkEngineModel engine, boolean initiator) {
        if (engine == null) throw new NullPointerException("engine");
        this.engine = engine;
        this.initiator = initiator;
    }

    public void start(long nowMillis) {
        lastNowMillis = nowMillis;
        engine.start(initiator, nowMillis);
    }

    public void onBytes(byte[] bytes, int length, long nowMillis) throws IOException {
        if (bytes == null || length < 0 || length > bytes.length) {
            throw new IOException("invalid iAP2 input range");
        }
        lastNowMillis = nowMillis;
        if (engine.state() == Iap2LinkEngineModel.State.CLOSED) return;
        byte[] copy = new byte[length];
        System.arraycopy(bytes, 0, copy, 0, length);
        engine.feedNegotiation(copy, nowMillis);
    }

    public void onTimer(long nowMillis) throws IOException {
        lastNowMillis = nowMillis;
        Iap2LinkEngineModel.State state = engine.state();
        if (state == Iap2LinkEngineModel.State.NEGOTIATING) {
            engine.advanceNegotiationTime(nowMillis);
        } else if (state == Iap2LinkEngineModel.State.READY) {
            engine.advanceTimers(nowMillis);
        }
    }

    public byte[] drainOutput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] negotiation = engine.takeNegotiationOutput();
        if (negotiation != null && negotiation.length > 0) {
            output.write(negotiation, 0, negotiation.length);
        }
        List<byte[]> frames = engine.drainWireFrames();
        for (int i = 0; i < frames.size(); i++) {
            byte[] frame = frames.get(i);
            output.write(frame, 0, frame.length);
        }
        return output.toByteArray();
    }

    public void sendControl(byte[] payload, long nowMillis) throws IOException {
        if (payload == null) throw new NullPointerException("payload");
        lastNowMillis = nowMillis;
        Iap2LinkEngineModel.State state = engine.state();
        if (state == Iap2LinkEngineModel.State.CLOSED) {
            throw new IOException("iAP2 link model is closed");
        }
        if (state == Iap2LinkEngineModel.State.READY) {
            engine.send(CONTROL_SESSION_ID, payload, nowMillis);
        } else {
            if (!engine.queueBeforeReady(CONTROL_SESSION_ID, payload)) {
                throw new IOException(engine.getTerminalReason() == null
                        ? "iAP2 pre-ready queue rejected control payload"
                        : engine.getTerminalReason());
            }
        }
    }

    public boolean isTerminal() {
        return engine.state() == Iap2LinkEngineModel.State.CLOSED;
    }

    public String terminalReason() {
        String reason = engine.getTerminalReason();
        return reason == null ? "iAP2 link model closed" : reason;
    }

    /** Model deadlines are not exposed yet; pump polls at its bounded 100 ms cadence. */
    public long nextDeadlineMillis() {
        return lastNowMillis + 100L;
    }

    public boolean isReady() {
        return engine.state() == Iap2LinkEngineModel.State.READY;
    }
}
