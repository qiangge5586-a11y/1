package com.shilapi.xcertplay.compat.bluetooth;

public final class BluetoothRfcommMetricsTest {
    public static void main(String[] args) {
        BluetoothRfcommMetrics metrics = new BluetoothRfcommMetrics();
        metrics.recordSocketRead(100L, 16);
        metrics.recordSocketRead(200L, -1);
        metrics.recordSocketWrite(7, 50L);
        metrics.recordBufferedHighWater(128);
        metrics.recordBufferedHighWater(64);
        metrics.recordClose(300L);
        BluetoothRfcommMetrics.Snapshot snapshot = metrics.snapshot();
        truth(snapshot.socketReadCalls == 2L, "socket read calls include EOF");
        truth(snapshot.socketReadBytes == 16L, "read byte count excludes EOF");
        truth(snapshot.socketReadNanos == 300L, "read duration aggregated");
        truth(snapshot.socketWriteCalls == 1L && snapshot.socketWriteBytes == 7L,
                "write calls and bytes aggregated");
        truth(snapshot.maxBufferedBytes == 128L, "ring high-water mark is monotonic");
        truth(snapshot.closeCalls == 1L && snapshot.closeNanos == 300L,
                "close latency aggregated");
        System.out.println("BluetoothRfcommMetricsTest: PASS");
    }

    private static void truth(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
