package com.shilapi.xcertplay.compat.mfi;

import com.shilapi.xcertplay.compat.iap2.Iap2CsmFrameCodec;
import com.shilapi.xcertplay.compat.iap2.Iap2CsmPumpSender;
import com.shilapi.xcertplay.compat.iap2.Iap2CsmReceiveCoordinator;
import com.shilapi.xcertplay.compat.iap2.Iap2CsmReceiveQueueModel;
import com.shilapi.xcertplay.compat.iap2.Iap2LinkPump;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-consumer bridge between the bounded CSM receive queue, MFi adapter,
 * and the single-owner iAP2 link pump.
 *
 * Call processAvailable() from a dedicated application/CSM worker, never from
 * the link-pump worker or its callbacks. The bridge performs no socket I/O.
 */
public final class MfiIap2CsmPumpBridge {
    private final Iap2CsmReceiveCoordinator receive;
    private final MfiIap2CsmAdapter adapter;
    private final Iap2CsmPumpSender sender;
    private final Iap2LinkPump pump;
    private final int peerMaxControlPayloadBytes;
    private final long sendTimeoutMillis;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public MfiIap2CsmPumpBridge(Iap2CsmReceiveCoordinator receive,
            MfiIap2CsmAdapter adapter, Iap2LinkPump pump,
            int peerMaxControlPayloadBytes, long sendTimeoutMillis) {
        if (receive == null || adapter == null || pump == null)
            throw new NullPointerException("MFi CSM bridge dependency");
        if (peerMaxControlPayloadBytes < 1
                || peerMaxControlPayloadBytes > Iap2LinkPump.MAX_CONTROL_PAYLOAD)
            throw new IllegalArgumentException("invalid peer maximum control payload");
        if (sendTimeoutMillis < 0L) throw new IllegalArgumentException("negative send timeout");
        this.receive = receive;
        this.adapter = adapter;
        this.pump = pump;
        this.sender = new Iap2CsmPumpSender(pump);
        this.peerMaxControlPayloadBytes = peerMaxControlPayloadBytes;
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    /**
     * Processes up to maxFrames already admitted by Iap2CsmReceiveCoordinator.
     * Returns the number of inbound frames processed. AA04/AA05 are terminal
     * authentication results and correctly produce no outbound frame.
     */
    public int processAvailable(int maxFrames) throws IOException {
        if (maxFrames < 1 || maxFrames > Iap2CsmReceiveQueueModel.MAX_PENDING_FRAMES)
            throw new IllegalArgumentException("maxFrames must be in 1..64");
        if (cancelled.get()) throw new IOException("MFi CSM bridge is cancelled");
        if (!processing.compareAndSet(false, true))
            throw new IllegalStateException("MFi CSM bridge already has an active consumer");
        int processed = 0;
        try {
            while (processed < maxFrames && !cancelled.get()) {
                Iap2CsmReceiveQueueModel.Frame inbound = receive.poll();
                if (inbound == null) {
                    if (receive.isClosed()) {
                        cancel();
                        throw new IOException("CSM receive queue closed: " + receive.terminalReason());
                    }
                    break;
                }
                byte[] completeFrame = Iap2CsmFrameCodec.encode(inbound.messageId, inbound.payload);
                byte[] outbound = adapter.onInboundFrame(completeFrame);
                processed++;
                if (cancelled.get()) throw new IOException("MFi CSM bridge cancelled during exchange");
                if (outbound != null && !sender.sendEncodedFrame(outbound,
                        peerMaxControlPayloadBytes, sendTimeoutMillis)) {
                    cancel();
                    throw new IOException("MFi CSM response could not be admitted to iAP2 pump");
                }
                if (adapter.state() == MfiIap2ExchangeCoordinator.State.FAILED
                        || adapter.state() == MfiIap2ExchangeCoordinator.State.CLOSED) {
                    break;
                }
                if (adapter.state() == MfiIap2ExchangeCoordinator.State.SUCCEEDED) break;
            }
            return processed;
        } catch (RuntimeException failure) {
            cancel();
            throw failure;
        } finally {
            processing.set(false);
        }
    }

    /** Session teardown/reconnect invalidates provider work and closes the old link pump. */
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) return;
        adapter.cancel();
        pump.close();
    }

    public boolean isCancelled() { return cancelled.get(); }
    public MfiIap2ExchangeCoordinator.State state() { return adapter.state(); }
}
