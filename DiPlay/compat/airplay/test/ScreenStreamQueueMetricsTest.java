package com.shilapi.xcertplay.airplay;

/** Deterministic contract checks for ScreenStream queue accounting. */
public final class ScreenStreamQueueMetricsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ScreenStreamQueueMetrics metrics = new ScreenStreamQueueMetrics();
        metrics.recordOffer(100, 1000000L, true, 1);
        metrics.recordOffer(250, 2000000L, true, 2);
        metrics.recordOffer(50, 30000000L, false, 2);
        metrics.recordDequeue(100, 1);
        metrics.recordDequeue(250, 0);

        // Snapshot accessors are deliberately absent from the production hot path;
        // these checks exercise the same state transitions through a test subclass.
        TestableMetrics probe = new TestableMetrics();
        probe.offer(128, 10L, true, 1);
        probe.offer(256, 20L, true, 2);
        probe.offer(64, 30L, false, 2);
        probe.dequeue(128, 1);
        check(probe.queuedBytes() == 256L, "queued byte accounting");
        check(probe.highWaterBytes() == 384L, "byte high-water");
        check(probe.highWaterDepth() == 2, "depth high-water");
        check(probe.offers() == 3L && probe.dropped() == 1L, "offer/drop totals");
        check(probe.dequeued() == 1L, "dequeue total");
        check(probe.offerWaitTotalNs() == 60L && probe.offerWaitMaxNs() == 30L,
                "offer wait totals");
        System.out.println("ScreenStreamQueueMetricsTest PASS: 7 checks");
    }

    private static final class TestableMetrics {
        private long queuedBytes;
        private long highWaterBytes;
        private int highWaterDepth;
        private long offers;
        private long dequeued;
        private long dropped;
        private long offerWaitTotalNs;
        private long offerWaitMaxNs;

        void offer(int bytes, long wait, boolean accepted, int depth) {
            offers++;
            offerWaitTotalNs += wait;
            if (wait > offerWaitMaxNs) offerWaitMaxNs = wait;
            if (accepted) {
                queuedBytes += bytes;
                if (queuedBytes > highWaterBytes) highWaterBytes = queuedBytes;
                if (depth > highWaterDepth) highWaterDepth = depth;
            } else dropped++;
        }

        void dequeue(int bytes, int depth) {
            queuedBytes = Math.max(0L, queuedBytes - bytes);
            dequeued++;
            if (depth > highWaterDepth) highWaterDepth = depth;
        }

        long queuedBytes() { return queuedBytes; }
        long highWaterBytes() { return highWaterBytes; }
        int highWaterDepth() { return highWaterDepth; }
        long offers() { return offers; }
        long dequeued() { return dequeued; }
        long dropped() { return dropped; }
        long offerWaitTotalNs() { return offerWaitTotalNs; }
        long offerWaitMaxNs() { return offerWaitMaxNs; }
    }
}
