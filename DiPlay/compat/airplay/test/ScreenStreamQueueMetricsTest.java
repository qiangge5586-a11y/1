package com.shilapi.xcertplay.airplay;

/** Deterministic contract checks against the production metrics implementation. */
public final class ScreenStreamQueueMetricsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ScreenStreamQueueMetrics metrics = new ScreenStreamQueueMetrics();
        metrics.recordOffer(128, 10L, true, 1);
        metrics.recordOffer(256, 20L, true, 2);
        metrics.recordOffer(64, 30L, false, 2);

        ScreenStreamQueueMetrics.Snapshot beforeDrain = metrics.snapshot();
        check(beforeDrain.queuedBytes == 384L, "queued bytes before drain");
        check(beforeDrain.highWaterBytes == 384L, "byte high-water");
        check(beforeDrain.highWaterDepth == 2, "depth high-water");
        check(beforeDrain.offers == 3L && beforeDrain.dropped == 1L,
                "offer/drop totals");
        check(beforeDrain.offerWaitTotalNs == 60L
                && beforeDrain.offerWaitMaxNs == 30L, "offer wait totals");

        metrics.recordDequeue(128, 1);
        ScreenStreamQueueMetrics.Snapshot afterDrain = metrics.snapshot();
        check(afterDrain.queuedBytes == 256L, "queued byte decrement");
        check(afterDrain.dequeued == 1L, "dequeue count");
        check(afterDrain.highWaterBytes == 384L, "high-water remains monotonic");

        metrics.recordDequeue(4096, 0);
        check(metrics.snapshot().queuedBytes == 0L, "underflow clamps to zero");
        System.out.println("ScreenStreamQueueMetricsTest PASS: 9 checks");
    }
}
