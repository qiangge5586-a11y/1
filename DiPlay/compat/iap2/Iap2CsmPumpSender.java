package com.shilapi.xcertplay.compat.iap2;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Producer-side CSM frame sender over the bounded single-owner link pump.
 * Call from an application/CSM producer thread, never from the pump worker.
 * If a multi-chunk frame cannot be fully admitted before the shared deadline,
 * the link is closed so a truncated CSM frame cannot be followed by later data.
 */
public final class Iap2CsmPumpSender {
    private final Iap2LinkPump pump;

    public Iap2CsmPumpSender(Iap2LinkPump pump) {
        if (pump == null) throw new NullPointerException("pump");
        this.pump = pump;
    }

    public boolean sendEncodedFrame(byte[] encodedFrame, int peerMaxControlPayloadBytes,
            long timeoutMillis) {
        if (encodedFrame == null) throw new NullPointerException("encodedFrame");
        if (timeoutMillis < 0L) throw new IllegalArgumentException("negative timeout");
        List<byte[]> chunks = Iap2CsmSendPlan.splitForLink(
                encodedFrame, peerMaxControlPayloadBytes);
        long startedAt = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for (int i = 0; i < chunks.size(); i++) {
            long elapsed = System.nanoTime() - startedAt;
            long remainingNanos = timeoutNanos - elapsed;
            long remainingMillis = remainingNanos <= 0L ? 0L
                    : TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            if (!pump.sendControlAwaitCapacity(chunks.get(i), remainingMillis)) {
                pump.close();
                return false;
            }
        }
        return true;
    }
}
