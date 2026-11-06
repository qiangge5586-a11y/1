package com.shilapi.xcertplay.compat.bluetooth;

import java.util.concurrent.atomic.AtomicLong;

/** Allocation-free-per-event aggregate metrics for the RFCOMM carrier. */
public final class BluetoothRfcommMetrics {
    private final AtomicLong socketReadCalls = new AtomicLong();
    private final AtomicLong socketReadBytes = new AtomicLong();
    private final AtomicLong socketReadNanos = new AtomicLong();
    private final AtomicLong socketWriteCalls = new AtomicLong();
    private final AtomicLong socketWriteBytes = new AtomicLong();
    private final AtomicLong socketWriteNanos = new AtomicLong();
    private final AtomicLong maxBufferedBytes = new AtomicLong();
    private final AtomicLong closeCalls = new AtomicLong();
    private final AtomicLong closeNanos = new AtomicLong();

    void recordSocketRead(long nanos, int count) {
        socketReadCalls.incrementAndGet();
        if (count > 0) socketReadBytes.addAndGet(count);
        if (nanos > 0L) socketReadNanos.addAndGet(nanos);
    }

    void recordSocketWrite(int count, long nanos) {
        socketWriteCalls.incrementAndGet();
        socketWriteBytes.addAndGet(count);
        if (nanos > 0L) socketWriteNanos.addAndGet(nanos);
    }

    void recordBufferedHighWater(long count) { updateMax(maxBufferedBytes, count); }

    void recordClose(long nanos) {
        closeCalls.incrementAndGet();
        if (nanos > 0L) closeNanos.addAndGet(nanos);
    }

    private static void updateMax(AtomicLong target, long value) {
        long previous = target.get();
        while (value > previous && !target.compareAndSet(previous, value)) previous = target.get();
    }

    public Snapshot snapshot() {
        return new Snapshot(socketReadCalls.get(), socketReadBytes.get(), socketReadNanos.get(),
                socketWriteCalls.get(), socketWriteBytes.get(), socketWriteNanos.get(),
                maxBufferedBytes.get(), closeCalls.get(), closeNanos.get());
    }

    public static final class Snapshot {
        public final long socketReadCalls, socketReadBytes, socketReadNanos;
        public final long socketWriteCalls, socketWriteBytes, socketWriteNanos;
        public final long maxBufferedBytes, closeCalls, closeNanos;

        Snapshot(long readCalls, long readBytes, long readNanos, long writeCalls,
                long writeBytes, long writeNanos, long maxBuffered, long closes, long closeNanos) {
            this.socketReadCalls = readCalls; this.socketReadBytes = readBytes;
            this.socketReadNanos = readNanos; this.socketWriteCalls = writeCalls;
            this.socketWriteBytes = writeBytes; this.socketWriteNanos = writeNanos;
            this.maxBufferedBytes = maxBuffered; this.closeCalls = closes;
            this.closeNanos = closeNanos;
        }
    }
}
