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
 * Call from a dedicated application/CSM consumer worker, never from the
 * link-pump worker or its callbacks. This class performs no socket I/O.
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

    /** Process up to maxFrames already queued; useful for bounded batch consumers/tests. */
    public int processAvailable(int maxFrames) throws IOException {
        if (maxFrames < 1 || maxFrames > Iap2CsmReceiveQueueModel.MAX_PENDING_FRAMES)
            throw new IllegalArgumentException("maxFrames must be in 1..64");
        enterConsumer();
        try {
            int processed = 0;
            while (processed < maxFrames && !cancelled.get()) {
                Iap2CsmReceiveQueueModel.Frame inbound = receive.poll();
                if (inbound == null) {
                    if (receive.isClosed()) {
                        cancel();
                        throw new IOException("CSM receive queue closed: " + receive.terminalReason());
                    }
                    break;
                }
                processInbound(inbound);
                processed++;
                if (isTerminalState()) break;
            }
            return processed;
        } catch (RuntimeException failure) {
            cancel();
            throw failure;
        } finally {
            processing.set(false);
        }
    }

    /**
     * Blocking single-frame consumer API. A timeout returns zero; a received frame
     * returns one. The receive queue owns interruption and close semantics.
     */
    public int processNext(long receiveTimeoutMillis) throws IOException {
        if (receiveTimeoutMillis < 0L || receiveTimeoutMillis > 300000L)
            throw new IllegalArgumentException("receive timeout must be in 0..300000");
        enterConsumer();
        try {
            Iap2CsmReceiveQueueModel.Frame inbound = receive.receive(receiveTimeoutMillis);
            if (inbound == null) return 0;
            if (cancelled.get()) throw new IOException("MFi CSM bridge is cancelled");
            processInbound(inbound);
            return 1;
        } catch (IOException failure) {
            if (!cancelled.get()) cancel();
            throw failure;
        } catch (RuntimeException failure) {
            cancel();
            throw failure;
        } finally {
            processing.set(false);
        }
    }

    private void enterConsumer() {
        if (cancelled.get()) throw new IllegalStateException("MFi CSM bridge is cancelled");
        if (!processing.compareAndSet(false, true))
            throw new IllegalStateException("MFi CSM bridge already has an active consumer");
    }

    private void processInbound(Iap2CsmReceiveQueueModel.Frame inbound) throws IOException {
        byte[] completeFrame = Iap2CsmFrameCodec.encode(inbound.messageId, inbound.payload);
        byte[] outbound = adapter.onInboundFrame(completeFrame);
        if (cancelled.get()) throw new IOException("MFi CSM bridge cancelled during exchange");
        if (outbound != null && !sender.sendEncodedFrame(outbound,
                peerMaxControlPayloadBytes, sendTimeoutMillis)) {
            cancel();
            throw new IOException("MFi CSM response could not be admitted to iAP2 pump");
        }
    }

    private boolean isTerminalState() {
        MfiIap2ExchangeCoordinator.State current = adapter.state();
        return current == MfiIap2ExchangeCoordinator.State.FAILED
                || current == MfiIap2ExchangeCoordinator.State.CLOSED
                || current == MfiIap2ExchangeCoordinator.State.SUCCEEDED;
    }

    /** Explicit teardown/reconnect invalidates provider work and closes the old link pump. */
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) return;
        adapter.cancel();
        pump.close();
    }

    public boolean isCancelled() { return cancelled.get(); }
    public MfiIap2ExchangeCoordinator.State state() { return adapter.state(); }
}
