package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayDeque;

/**
 * Offline model of the original Iap2CsmChannel received-frame queue.
 * It models admission and byte accounting only; it performs no transport I/O.
 */
public final class Iap2CsmReceiveQueueModel {
    public static final int MAX_PENDING_FRAMES = 64;
    public static final int MAX_PENDING_FRAME_BYTES = 1024 * 1024;
    public static final int HEADER_BYTES = 6;
    public static final int MAX_FRAME_BYTES = 65535;

    public static final class Frame {
        public final int messageId;
        public final byte[] payload;
        private final int encodedBytes;

        private Frame(int messageId, byte[] payload) {
            this.messageId = messageId;
            this.payload = new byte[payload.length];
            System.arraycopy(payload, 0, this.payload, 0, payload.length);
            this.encodedBytes = HEADER_BYTES + payload.length;
        }
    }

    private final ArrayDeque<Frame> received = new ArrayDeque<Frame>();
    private int receivedBytes;
    private boolean closed;
    private String terminalReason;

    /** Returns false after close or when this enqueue causes the source queue to fail closed. */
    public synchronized boolean enqueue(int messageId, byte[] payload) {
        if (payload == null) throw new NullPointerException("payload");
        if (messageId < 0 || messageId > 65535) {
            throw new IllegalArgumentException("CSM message id must fit in u16");
        }
        if (payload.length > MAX_FRAME_BYTES - HEADER_BYTES) {
            throw new IllegalArgumentException("CSM payload exceeds maximum frame size");
        }
        if (closed) return false;
        int encodedBytes = HEADER_BYTES + payload.length;
        if (received.size() >= MAX_PENDING_FRAMES
                || encodedBytes > MAX_PENDING_FRAME_BYTES - receivedBytes) {
            closed = true;
            terminalReason = "CSM received-frame queue limit exceeded";
            return false;
        }
        received.addLast(new Frame(messageId, payload));
        receivedBytes += encodedBytes;
        return true;
    }

    /** Polls in FIFO order and mirrors the source takeReceived() byte decrement. */
    public synchronized Frame poll() {
        Frame frame = received.pollFirst();
        if (frame != null) receivedBytes -= frame.encodedBytes;
        return frame;
    }

    public synchronized int size() { return received.size(); }
    public synchronized int pendingBytes() { return receivedBytes; }
    public synchronized boolean isClosed() { return closed; }
    public synchronized String getTerminalReason() { return terminalReason; }
}
