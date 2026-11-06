package com.shilapi.xcertplay.compat.mfi;

import com.shilapi.xcertplay.compat.iap2.Iap2CsmReceiveCoordinator;
import com.shilapi.xcertplay.compat.iap2.Iap2ReceiveWindowModel;
import java.io.IOException;
import java.util.List;

/**
 * Owns one MFi authentication exchange for exactly one iAP2 link generation.
 *
 * A new RFCOMM/control-channel generation must create a new owner, receive
 * coordinator, bridge and adapter. Never reset/reuse an old owner for reconnect.
 * The generation value is supplied by the transport/session supervisor.
 */
public final class MfiIap2SessionOwner {
    private final long generation;
    private final Iap2CsmReceiveCoordinator receive;
    private final MfiIap2CsmPumpBridge bridge;
    private final MfiIap2CsmWorker worker;
    private boolean active = true;
    private boolean started;

    public MfiIap2SessionOwner(long generation,
            Iap2CsmReceiveCoordinator receive,
            MfiIap2CsmPumpBridge bridge,
            MfiIap2CsmWorker.Listener listener) {
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        if (receive == null || bridge == null || listener == null)
            throw new NullPointerException("MFi session owner dependency");
        this.generation = generation;
        this.receive = receive;
        this.bridge = bridge;
        this.worker = new MfiIap2CsmWorker(bridge, new GuardedListener(listener));
    }

    public synchronized void start() {
        ensureActive();
        if (started) throw new IllegalStateException("MFi session already started");
        started = true;
        worker.start();
    }

    /**
     * Feed only in-order control-session packets from this link generation.
     * Synchronization with close prevents packets from being admitted after teardown.
     */
    public synchronized int acceptDeliveredPackets(
            List<Iap2ReceiveWindowModel.Packet> packets) {
        ensureActive();
        if (!started) throw new IllegalStateException("MFi session not started");
        return receive.acceptDeliveredPackets(packets);
    }

    /** Invalidate this generation before cancelling provider work and closing its pump. */
    public synchronized void close() {
        if (!active) return;
        active = false;
        worker.stop();
    }

    public synchronized boolean isActive() { return active; }
    public long generation() { return generation; }

    private void ensureActive() {
        if (!active) throw new IllegalStateException("MFi session generation is inactive");
    }

    private final class GuardedListener implements MfiIap2CsmWorker.Listener {
        private final MfiIap2CsmWorker.Listener delegate;

        GuardedListener(MfiIap2CsmWorker.Listener delegate) { this.delegate = delegate; }

        public void onAuthenticationSucceeded() {
            synchronized (MfiIap2SessionOwner.this) {
                if (active) delegate.onAuthenticationSucceeded();
            }
        }

        public void onAuthenticationFailed() {
            synchronized (MfiIap2SessionOwner.this) {
                if (active) delegate.onAuthenticationFailed();
            }
        }

        public void onFailure(IOException failure) {
            synchronized (MfiIap2SessionOwner.this) {
                if (active) delegate.onFailure(failure);
            }
        }
    }
}
