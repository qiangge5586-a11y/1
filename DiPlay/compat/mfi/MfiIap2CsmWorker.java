package com.shilapi.xcertplay.compat.mfi;

import java.io.IOException;

/**
 * Dedicated CSM consumer thread. Never runs on the iAP2 link-pump worker.
 * It blocks on the bounded receive coordinator and lets the bridge enqueue
 * responses through the pump's producer API.
 */
public final class MfiIap2CsmWorker {
    public interface Listener {
        void onAuthenticationSucceeded();
        void onAuthenticationFailed();
        void onFailure(IOException failure);
    }

    private final MfiIap2CsmPumpBridge bridge;
    private final Listener listener;
    private Thread thread;
    private volatile boolean stopping;
    private volatile boolean running;

    public MfiIap2CsmWorker(MfiIap2CsmPumpBridge bridge, Listener listener) {
        if (bridge == null || listener == null) throw new NullPointerException("worker dependency");
        this.bridge = bridge;
        this.listener = listener;
    }

    public synchronized void start() {
        if (thread != null) throw new IllegalStateException("CSM worker already started");
        if (stopping) throw new IllegalStateException("CSM worker is stopped");
        thread = new Thread(new Runnable() {
            public void run() { runLoop(); }
        }, "iAP2-MFi-CSM-Worker");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops this worker and tears down its old link generation. */
    public void stop() {
        stopping = true;
        bridge.cancel();
        Thread current;
        synchronized (this) { current = thread; }
        if (current != null) current.interrupt();
    }

    public boolean isRunning() { return running; }

    private void runLoop() {
        running = true;
        try {
            while (!stopping) {
                bridge.processNext(250L);
                MfiIap2ExchangeCoordinator.State state = bridge.state();
                if (state == MfiIap2ExchangeCoordinator.State.SUCCEEDED) {
                    listener.onAuthenticationSucceeded();
                    return;
                }
                if (state == MfiIap2ExchangeCoordinator.State.FAILED) {
                    listener.onAuthenticationFailed();
                    return;
                }
                if (state == MfiIap2ExchangeCoordinator.State.CLOSED) return;
            }
        } catch (IOException failure) {
            if (!stopping) listener.onFailure(failure);
        } catch (RuntimeException failure) {
            if (!stopping) listener.onFailure(new IOException(
                    "MFi CSM worker failure: " + failure.getMessage()));
        } finally {
            running = false;
        }
    }
}
