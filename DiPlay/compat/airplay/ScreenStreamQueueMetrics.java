package com.shilapi.xcertplay.airplay;

/**
 * Low-frequency aggregate instrumentation for the original ScreenStream
 * decrypt queue. This class observes queue pressure only; it never changes
 * admission, ordering, timeout, or drop policy.
 *
 * The owner should call snapshot() only for diagnostics/session summaries,
 * never once per frame. This helper is not wired into original ScreenStream
 * until the corresponding Smali call sites are patched and verified.
 */
public final class ScreenStreamQueueMetrics {
    private long queuedBytes;
    private long highWaterBytes;
    private int highWaterDepth;
    private long offers;
    private long dequeued;
    private long dropped;
    private long offerWaitTotalNs;
    private long offerWaitMaxNs;

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
    }

    public synchronized void recordDequeue(int bodyBytes, int depthAfterDequeue) {
        queuedBytes -= Math.max(0, bodyBytes);
        if (queuedBytes < 0L) queuedBytes = 0L;
        dequeued++;
        if (depthAfterDequeue > highWaterDepth) highWaterDepth = depthAfterDequeue;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(queuedBytes, highWaterBytes, highWaterDepth,
                offers, dequeued, dropped, offerWaitTotalNs, offerWaitMaxNs);
    }

    public static final class Snapshot {
        public final long queuedBytes;
        public final long highWaterBytes;
        public final int highWaterDepth;
        public final long offers;
        public final long dequeued;
        public final long dropped;
        public final long offerWaitTotalNs;
        public final long offerWaitMaxNs;

        private Snapshot(long queuedBytes, long highWaterBytes, int highWaterDepth,
                long offers, long dequeued, long dropped,
                long offerWaitTotalNs, long offerWaitMaxNs) {
            this.queuedBytes = queuedBytes;
            this.highWaterBytes = highWaterBytes;
            this.highWaterDepth = highWaterDepth;
            this.offers = offers;
            this.dequeued = dequeued;
            this.dropped = dropped;
            this.offerWaitTotalNs = offerWaitTotalNs;
            this.offerWaitMaxNs = offerWaitMaxNs;
        }
    }
}
