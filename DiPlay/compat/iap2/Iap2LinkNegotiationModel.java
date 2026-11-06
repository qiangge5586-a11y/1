package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic offline model of the original Iap2LinkEngine startup and
 * synchronization negotiation path. No sockets, Bluetooth, USB or crypto.
 */
public final class Iap2LinkNegotiationModel {
    public enum State { IDLE, DETECTING, NEGOTIATING, NORMAL, DEAD }

    public static final int MAX_SYNC_ATTEMPTS = 20;
    public static final long SYNC_RESEND_MILLIS = 500L;
    public static final long MARKER_RESEND_MILLIS = 1000L;

    public static final class Config {
        public final int maxOutgoing;
        public final int acknowledgementTimeoutMillis;
        public final int controlSessionVersion;

        public Config(int maxOutgoing, int acknowledgementTimeoutMillis,
                int controlSessionVersion) {
            checkByte(maxOutgoing, "maxOutgoing");
            checkU16(acknowledgementTimeoutMillis, "acknowledgementTimeoutMillis");
            checkByte(controlSessionVersion, "controlSessionVersion");
            this.maxOutgoing = maxOutgoing;
            this.acknowledgementTimeoutMillis = acknowledgementTimeoutMillis;
            this.controlSessionVersion = controlSessionVersion;
        }

        public static Config wirelessDefault() {
            return new Config(4, 500, 2);
        }
    }

    private State state = State.IDLE;
    private final Config config;
    private final Iap2LinkFrameDecoder decoder = new Iap2LinkFrameDecoder();
    private final ArrayDeque<byte[]> output = new ArrayDeque<byte[]>();
    private byte[] detectingBytes = new byte[0];
    private int sentSequence = Iap2LinkFrameCodec.INITIAL_SEQUENCE;
    private int lastReceivedInOrder;
    private int syncAttempts;
    private int peerMaxLength = -1;
    private boolean peerSynchronizationReceived;
    private Long markerDeadline;
    private Long synchronizationDeadline;
    private String terminalReason;

    public Iap2LinkNegotiationModel() {
        this(Config.wirelessDefault());
    }

    public Iap2LinkNegotiationModel(Config config) {
        if (config == null) throw new NullPointerException("config");
        this.config = config;
    }

    /** wiredInitiator=true starts synchronization immediately after sending the marker. */
    public synchronized void start(boolean wiredInitiator, long nowMillis) {
        if (state != State.IDLE) return;
        state = State.DETECTING;
        appendOutput(copy(Iap2LinkFrameCodec.IAP2_MARKER));
        markerDeadline = Long.valueOf(nowMillis + MARKER_RESEND_MILLIS);
        if (wiredInitiator) enterNegotiating(nowMillis);
    }

    /**
     * For a non-initiating peer, the first six bytes must be the exact startup
     * marker. Remaining bytes in the same read are parsed as link frames.
     */
    public synchronized void feed(byte[] bytes, long nowMillis) {
        if (bytes == null) throw new NullPointerException("bytes");
        if (state == State.DEAD || bytes.length == 0) return;
        if (state == State.DETECTING) {
            detectingBytes = concat(detectingBytes, bytes);
            if (detectingBytes.length < Iap2LinkFrameCodec.IAP2_MARKER.length) return;
            if (!startsWith(detectingBytes, Iap2LinkFrameCodec.IAP2_MARKER)) {
                die("iAP2 marker was not received");
                return;
            }
            byte[] remainder = copyRange(detectingBytes,
                    Iap2LinkFrameCodec.IAP2_MARKER.length, detectingBytes.length);
            detectingBytes = new byte[0];
            enterNegotiating(nowMillis);
            if (remainder.length > 0) feedFrames(remainder, nowMillis);
            return;
        }
        feedFrames(bytes, nowMillis);
    }

    private void feedFrames(byte[] bytes, long nowMillis) {
        try {
            List<Iap2LinkFrameDecoder.Frame> frames = decoder.feed(bytes);
            for (int i = 0; i < frames.size() && state != State.DEAD; i++) {
                handleFrame(frames.get(i), nowMillis);
            }
        } catch (RuntimeException e) {
            die("iAP2 link frame decoder: " + e.getMessage());
        }
    }

    private void handleFrame(Iap2LinkFrameDecoder.Frame frame, long nowMillis) {
        if ((frame.control & Iap2LinkFrameCodec.CONTROL_RESET) != 0) {
            die("peer sent iAP2 reset");
            return;
        }
        if ((frame.control & Iap2LinkFrameCodec.CONTROL_SYNCHRONIZE) != 0) {
            int[] sync = Iap2LinkFrameCodec.decodeSynchronizationPayload(
                    frame.payload == null ? new byte[0] : frame.payload);
            if (sync != null && state == State.NEGOTIATING) {
                peerMaxLength = sync[1];
                peerSynchronizationReceived = true;
                lastReceivedInOrder = frame.sequence;
                sendAcknowledgement();
            }
        }
        if ((frame.control & Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT) != 0
                && state == State.NEGOTIATING) {
            if (peerSynchronizationReceived && peerMaxLength > 10) {
                state = State.NORMAL;
                synchronizationDeadline = null;
            } else {
                die("peer iAP2 maxLength cannot carry pending control packets");
            }
        }
    }

    private void enterNegotiating(long nowMillis) {
        state = State.NEGOTIATING;
        markerDeadline = null;
        syncAttempts = 0;
        sendSynchronization();
        if (state != State.DEAD) synchronizationDeadline = Long.valueOf(nowMillis + SYNC_RESEND_MILLIS);
    }

    /** Mirrors the non-initiating wireless runPump() 800 ms fallback. */
    public synchronized boolean initiateSynchronizationIfDetecting(long nowMillis) {
        if (state != State.DETECTING || detectingBytes.length != 0) return false;
        enterNegotiating(nowMillis);
        return state == State.NEGOTIATING;
    }

    public synchronized void advanceTime(long nowMillis) {
        while (state != State.DEAD) {
            if (state == State.DETECTING && markerDeadline != null
                    && markerDeadline.longValue() <= nowMillis) {
                markerDeadline = Long.valueOf(nowMillis + MARKER_RESEND_MILLIS);
                appendOutput(copy(Iap2LinkFrameCodec.IAP2_MARKER));
                continue;
            }
            if (state == State.NEGOTIATING && synchronizationDeadline != null
                    && synchronizationDeadline.longValue() <= nowMillis) {
                synchronizationDeadline = null;
                syncAttempts++;
                if (syncAttempts > MAX_SYNC_ATTEMPTS) {
                    die("iAP2 SYN unanswered after " + syncAttempts + " attempts");
                } else {
                    sendSynchronization();
                    if (state != State.DEAD) {
                        synchronizationDeadline = Long.valueOf(nowMillis + SYNC_RESEND_MILLIS);
                    }
                }
                continue;
            }
            return;
        }
    }

    public synchronized void feedEof() {
        die("iAP2 link EOF");
    }

    private void sendSynchronization() {
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                config.maxOutgoing, 65535, 4000, config.acknowledgementTimeoutMillis,
                4, 3, new int[][] {{10, 0, config.controlSessionVersion},
                    {11, 2, 1}, {12, 1, 2}});
        appendOutput(Iap2LinkFrameCodec.encode(Iap2LinkFrameCodec.CONTROL_SYNCHRONIZE,
                sentSequence, lastReceivedInOrder, 0, sync));
    }

    private void sendAcknowledgement() {
        appendOutput(Iap2LinkFrameCodec.encode(Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT,
                sentSequence, lastReceivedInOrder, 0, null));
    }

    private void die(String reason) {
        if (state == State.DEAD) return;
        state = State.DEAD;
        terminalReason = reason;
        markerDeadline = null;
        synchronizationDeadline = null;
    }

    private void appendOutput(byte[] bytes) {
        if (bytes != null && bytes.length > 0) output.add(bytes);
    }

    public synchronized byte[] takeOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (!output.isEmpty()) {
            byte[] next = output.removeFirst();
            out.write(next, 0, next.length);
        }
        return out.toByteArray();
    }

    public synchronized State getState() { return state; }
    public synchronized boolean isReady() { return state == State.NORMAL; }
    public synchronized int getSyncAttempts() { return syncAttempts; }
    public synchronized int getPeerMaxLength() { return peerMaxLength; }
    public synchronized String getTerminalReason() { return terminalReason; }
    public synchronized int getPendingFrameBytes() { return decoder.pendingBytes(); }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
    private static byte[] copy(byte[] a) { return copyRange(a, 0, a.length); }
    private static byte[] copyRange(byte[] a, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(a, from, out, 0, out.length);
        return out;
    }
    private static void checkByte(int value, String name) {
        if (value < 0 || value > 255) throw new IllegalArgumentException(name);
    }
    private static void checkU16(int value, String name) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException(name);
    }
}
