package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Iap2CsmPumpSenderTest {
    public static void main(String[] args) throws Exception {
        final CountDownLatch driverEntered = new CountDownLatch(1);
        final CountDownLatch releaseDriver = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final ByteArrayOutputStream received = new ByteArrayOutputStream();
        final byte[] frame = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        Iap2LinkPump.Driver driver = new Iap2LinkPump.Driver() {
            public void start(long now) throws IOException {
                driverEntered.countDown();
                try {
                    if (!releaseDriver.await(2, TimeUnit.SECONDS)) {
                        throw new IOException("test driver release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test driver interrupted");
                }
            }
            public void onBytes(byte[] bytes, int length, long now) {}
            public void onTimer(long now) {}
            public byte[] drainOutput() { return null; }
            public boolean isTerminal() { return false; }
            public String terminalReason() { return null; }
            public long nextDeadlineMillis() { return Long.MAX_VALUE; }
        };
        Iap2LinkPump.CommandSink sink = new Iap2LinkPump.CommandSink() {
            public void sendControl(byte[] payload, long now) {
                received.write(payload, 0, payload.length);
            }
        };
        Iap2LinkPump.Driver combined = new CombinedDriver(driver, sink);
        Iap2LinkPump.Listener listener = new Iap2LinkPump.Listener() {
            public void onReady() {}
            public void onClosed(String reason) { closed.countDown(); }
            public void onFailure(IOException failure) { throw new AssertionError(failure); }
        };
        Iap2LinkPump pump = new Iap2LinkPump(
                new Iap2StreamDuplexAdapter(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()), combined, listener);
        pump.start();
        truth(driverEntered.await(2, TimeUnit.SECONDS), "pump driver entered");
        Iap2CsmPumpSender sender = new Iap2CsmPumpSender(pump);
        truth(sender.sendEncodedFrame(frame, 4, 1000L), "all CSM chunks admitted");
        releaseDriver.countDown();
        truth(closed.await(2, TimeUnit.SECONDS), "pump drains chunks then observes EOF");
        truth(Arrays.equals(frame, received.toByteArray()), "chunk order and bytes preserved");
        System.out.println("Iap2CsmPumpSenderTest: 4 checks passed");
    }

    private static void truth(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static final class CombinedDriver implements Iap2LinkPump.Driver,
            Iap2LinkPump.CommandSink {
        private final Iap2LinkPump.Driver delegate;
        private final Iap2LinkPump.CommandSink sink;
        CombinedDriver(Iap2LinkPump.Driver delegate, Iap2LinkPump.CommandSink sink) {
            this.delegate = delegate; this.sink = sink;
        }
        public void start(long now) throws IOException { delegate.start(now); }
        public void onBytes(byte[] b, int n, long now) throws IOException { delegate.onBytes(b,n,now); }
        public void onTimer(long now) throws IOException { delegate.onTimer(now); }
        public byte[] drainOutput() throws IOException { return delegate.drainOutput(); }
        public boolean isTerminal() { return delegate.isTerminal(); }
        public String terminalReason() { return delegate.terminalReason(); }
        public long nextDeadlineMillis() { return delegate.nextDeadlineMillis(); }
        public void sendControl(byte[] payload, long now) throws IOException {
            sink.sendControl(payload, now);
        }
    }
}
