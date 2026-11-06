package com.shilapi.xcertplay.compat.iap2;

import java.util.concurrent.atomic.AtomicLong;

/** Aggregate, allocation-free-per-event metrics for the iAP2 pump (API 17 compatible). */
public final class Iap2PumpMetrics {
    private final AtomicLong readCalls = new AtomicLong();
    private final AtomicLong readBytes = new AtomicLong();
    private final AtomicLong readNanos = new AtomicLong();
    private final AtomicLong driverBytes = new AtomicLong();
    private final AtomicLong driverNanos = new AtomicLong();
    private final AtomicLong timerCalls = new AtomicLong();
    private final AtomicLong timerNanos = new AtomicLong();
    private final AtomicLong writeCalls = new AtomicLong();
    private final AtomicLong writeBytes = new AtomicLong();
    private final AtomicLong writeNanos = new AtomicLong();
    private final AtomicLong commandsDelivered = new AtomicLong();
    private final AtomicLong maxQueueCommands = new AtomicLong();
    private final AtomicLong maxQueueBytes = new AtomicLong();

    void recordRead(long nanos, int count) {
        readCalls.incrementAndGet();
        if (count > 0) readBytes.addAndGet(count);
        if (nanos > 0L) readNanos.addAndGet(nanos);
    }

    void recordDriverBytes(int count, long nanos) {
        driverBytes.addAndGet(count);
        if (nanos > 0L) driverNanos.addAndGet(nanos);
    }

    void recordTimer(long nanos) {
        timerCalls.incrementAndGet();
        if (nanos > 0L) timerNanos.addAndGet(nanos);
    }

    void recordWrite(int count, long nanos) {
        writeCalls.incrementAndGet();
        writeBytes.addAndGet(count);
        if (nanos > 0L) writeNanos.addAndGet(nanos);
    }

    void recordCommandDelivered() { commandsDelivered.incrementAndGet(); }

    void recordQueueDepth(long commands, long bytes) {
        updateMax(maxQueueCommands, commands);
        updateMax(maxQueueBytes, bytes);
    }

    private static void updateMax(AtomicLong target, long value) {
        long previous = target.get();
        while (value > previous && !target.compareAndSet(previous, value)) {
            previous = target.get();
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(readCalls.get(), readBytes.get(), readNanos.get(),
                driverBytes.get(), driverNanos.get(), timerCalls.get(), timerNanos.get(),
                writeCalls.get(), writeBytes.get(), writeNanos.get(), commandsDelivered.get(),
                maxQueueCommands.get(), maxQueueBytes.get());
    }

    public static final class Snapshot {
        public final long readCalls, readBytes, readNanos;
        public final long driverBytes, driverNanos;
        public final long timerCalls, timerNanos;
        public final long writeCalls, writeBytes, writeNanos;
        public final long commandsDelivered, maxQueueCommands, maxQueueBytes;

        Snapshot(long readCalls, long readBytes, long readNanos,
                long driverBytes, long driverNanos, long timerCalls, long timerNanos,
                long writeCalls, long writeBytes, long writeNanos, long commandsDelivered,
                long maxQueueCommands, long maxQueueBytes) {
            this.readCalls = readCalls; this.readBytes = readBytes; this.readNanos = readNanos;
            this.driverBytes = driverBytes; this.driverNanos = driverNanos;
            this.timerCalls = timerCalls; this.timerNanos = timerNanos;
            this.writeCalls = writeCalls; this.writeBytes = writeBytes; this.writeNanos = writeNanos;
            this.commandsDelivered = commandsDelivered;
            this.maxQueueCommands = maxQueueCommands; this.maxQueueBytes = maxQueueBytes;
        }
    }
}
