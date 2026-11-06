package com.shilapi.xcertplay.compat.iap2;

import java.util.List;

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
            List<Iap2CsmFrameDecoder.Frame> frames = decoder.feed(packet.payload);
            for (int j = 0; j < frames.size(); j++) {
                Iap2CsmFrameDecoder.Frame frame = frames.get(j);
                if (!queue.enqueue(frame.messageId, frame.payload)) {
                    closed = true;
                    terminalReason = queue.getTerminalReason();
                    return admitted;
                }
                admitted++;
            }
        }
        return admitted;
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
    }
}
