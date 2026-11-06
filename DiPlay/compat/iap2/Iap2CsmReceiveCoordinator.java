package com.shilapi.xcertplay.compat.iap2;

import java.util.List;
import java.io.IOException;

/**
 * Offline receive-side composition: ordered iAP2 control-session payloads
 * are incrementally decoded as CSM frames and admitted to the bounded queue.
 *
 * The caller must pass only packets delivered in-order by the link receive
 * window. This class performs no transport I/O and is not a production engine.
 */
public final class Iap2CsmReceiveCoordinator {
    private Iap2CsmFrameDecoder decoder = new Iap2CsmFrameDecoder();
    private Iap2CsmReceiveQueueModel queue = new Iap2CsmReceiveQueueModel();
    private boolean closed;
    private String terminalReason;

    /**
     * Consumes packets from Iap2ReceiveWindowModel.Result.delivered in their
     * original order. Non-control sessions are ignored.
     *
     * @return number of complete CSM frames admitted during this call
     */
    public synchronized int acceptDeliveredPackets(
            List<Iap2ReceiveWindowModel.Packet> packets) {
        if (packets == null) throw new NullPointerException("packets");
        if (closed) return 0;
        int admitted = 0;
        for (int i = 0; i < packets.size(); i++) {
            Iap2ReceiveWindowModel.Packet packet = packets.get(i);
            if (packet == null) throw new NullPointerException("packet");
            if (packet.sessionId != Iap2LinkFrameCodec.CONTROL_SESSION_ID) continue;
            List<Iap2CsmFrameDecoder.Frame> frames;
            try {
                frames = decoder.feed(packet.payload);
            } catch (RuntimeException malformed) {
                closed = true;
                terminalReason = "Invalid CSM control payload";
                notifyAll();
                throw malformed;
            }
            for (int j = 0; j < frames.size(); j++) {
                Iap2CsmFrameDecoder.Frame frame = frames.get(j);
                if (!queue.enqueue(frame.messageId, frame.payload)) {
                    closed = true;
                    terminalReason = queue.getTerminalReason();
                    notifyAll();
                    return admitted;
                }
                admitted++;
                notifyAll();
            }
        }
        return admitted;
    }

    /** Blocking receive API with the original CSM channel's bounded timeout contract. */
    public synchronized Iap2CsmReceiveQueueModel.Frame receive(long timeoutMillis)
            throws IOException {
        if (timeoutMillis < 0L || timeoutMillis > 300000L) {
            throw new IllegalArgumentException("timeoutMillis must be between 0 and 300000");
        }
        Iap2CsmReceiveQueueModel.Frame frame = queue.poll();
        if (frame != null) return frame;
        long deadline = System.nanoTime() + timeoutMillis * 1000000L;
        while (true) {
            if (closed || queue.isClosed()) {
                throw new IOException(terminalReason == null
                        ? queue.getTerminalReason() : terminalReason);
            }
            if (timeoutMillis == 0L) return null;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) return null;
            long millis = remaining / 1000000L;
            int nanos = (int) (remaining % 1000000L);
            try {
                wait(millis, nanos);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("CSM receive interrupted", interrupted);
            }
            frame = queue.poll();
            if (frame != null) return frame;
        }
    }

    public synchronized Iap2CsmReceiveQueueModel.Frame poll() {
        return queue.poll();
    }

    public synchronized int queuedFrames() { return queue.size(); }
    public synchronized int queuedBytes() { return queue.pendingBytes(); }
    public synchronized int decoderPendingBytes() { return decoder.pendingBytes(); }
    public synchronized boolean isClosed() { return closed || queue.isClosed(); }
    public synchronized String terminalReason() {
        return terminalReason != null ? terminalReason : queue.getTerminalReason();
    }

    /** Reset all stream and queue state when a new transport session is created. */
    public synchronized void reset() {
        decoder = new Iap2CsmFrameDecoder();
        queue = new Iap2CsmReceiveQueueModel();
        closed = false;
        terminalReason = null;
        notifyAll();
    }
}
