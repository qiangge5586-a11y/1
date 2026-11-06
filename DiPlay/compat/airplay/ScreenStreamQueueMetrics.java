package com.shilapi.xcertplay.airplay;

import android.util.Log;

/**
 * Low-frequency aggregate instrumentation for the original ScreenStream
 * decrypt queue. This class observes queue pressure only; it never changes
 * admission, ordering, timeout, or drop policy.
 */
public final class ScreenStreamQueueMetrics {
    private static final String TAG = "DiPlay-ScreenQueue";
    private static final long REPORT_INTERVAL_NS = 5000000000L;

    private long queuedBytes;
    private long highWaterBytes;
    private int highWaterDepth;
    private long offers;
    private long dequeued;
    private long dropped;
    private long offerWaitTotalNs;
    private long offerWaitMaxNs;
    private long lastReportNs;

    public synchronized void recordOffer(int bodyBytes, long waitNs,
            boolean accepted, int depthAfterOffer) {
        offers++;
        if (waitNs > 0L) {
            offerWaitTotalNs += waitNs;
            if (waitNs > offerWaitMaxNs) offerWaitMaxNs = waitNs;
        }
        if (accepted) {
            queuedBytes += Math.max(0, bodyBytes);
            if (queuedBytes > highWaterBytes) highWaterBytes = queuedBytes;
            if (depthAfterOffer > highWaterDepth) highWaterDepth = depthAfterOffer;
        } else {
            dropped++;
        }
        reportIfDue();
    }

    public synchronized void recordDequeue(int bodyBytes, int depthAfterDequeue) {
        queuedBytes -= Math.max(0, bodyBytes);
        if (queuedBytes < 0L) queuedBytes = 0L;
        dequeued++;
        if (depthAfterDequeue > highWaterDepth) highWaterDepth = depthAfterDequeue;
        reportIfDue();
    }

    private void reportIfDue() {
        long now = System.nanoTime();
        if (lastReportNs != 0L && now - lastReportNs < REPORT_INTERVAL_NS) return;
        lastReportNs = now;
        Log.i(TAG, "offers=" + offers
                + " dequeued=" + dequeued
                + " dropped=" + dropped
                + " queuedBytes=" + queuedBytes
                + " highWaterBytes=" + highWaterBytes
                + " highWaterDepth=" + highWaterDepth
                + " offerWaitTotalMs=" + (offerWaitTotalNs / 1000000L)
                + " offerWaitMaxMs=" + (offerWaitMaxNs / 1000000L));
    }
}
