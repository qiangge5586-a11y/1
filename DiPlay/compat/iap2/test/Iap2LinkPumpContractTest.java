package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Iap2LinkPumpContractTest {
    public static void main(String[] args) throws Exception {
        testCommandBoundsAndEofShutdown();
        testRejectsOversizedPayload();
        testProducerWaitsForCapacityAndHonorsTimeout();
        System.out.println("Iap2LinkPumpContractTest: 11 checks passed");
    }

    private static void testCommandBoundsAndEofShutdown() throws Exception {
        final CountDownLatch closed = new CountDownLatch(1);
        final String[] reason = new String[1];
        Iap2LinkPump.Listener listener = new Iap2LinkPump.Listener() {
            public void onReady() {}
            public void onClosed(String value) { reason[0] = value; closed.countDown(); }
            public void onFailure(IOException failure) { throw new AssertionError(failure); }
        };
        Iap2LinkPump pump = new Iap2LinkPump(
                new Iap2StreamDuplexAdapter(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()),
                new DriverStub(), listener);
        truth(!pump.sendControl(new byte[] { 1 }), "cannot enqueue before start");
        pump.start();
        truth(closed.await(2, TimeUnit.SECONDS), "EOF closes pump");
        truth("iAP2 transport EOF".equals(reason[0]), "EOF reason preserved");
        truth(pump.isTerminated(), "pump reports terminated");
        Iap2PumpMetrics.Snapshot metrics = pump.metricsSnapshot();
        truth(metrics.readCalls == 1L, "metrics count EOF read");
        truth(metrics.readBytes == 0L, "metrics exclude EOF from byte count");
    }

    private static void testRejectsOversizedPayload() throws Exception {
        Iap2LinkPump pump = new Iap2LinkPump(
                new Iap2StreamDuplexAdapter(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()),
                new DriverStub(), new ListenerStub());
        boolean rejected = false;
        try { pump.sendControl(new byte[65526]); }
        catch (IllegalArgumentException expected) { rejected = true; }
        truth(rejected, "payload above 65525 rejected");
    }


    private static void testProducerWaitsForCapacityAndHonorsTimeout() throws Exception {
        final CountDownLatch driverEntered = new CountDownLatch(1);
        final CountDownLatch releaseDriver = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final CountDownLatch delivered = new CountDownLatch(Iap2LinkPump.MAX_COMMANDS + 1);
        final boolean[] admitted = new boolean[1];
        Iap2LinkPump.Driver driver = new DriverStub() {
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
            public void sendControl(byte[] payload, long now) { delivered.countDown(); }
        };
        Iap2LinkPump.Listener listener = new ListenerStub() {
            public void onClosed(String reason) { closed.countDown(); }
        };
        final WakeableTimeoutInputStream input = new WakeableTimeoutInputStream();
        final Iap2LinkPump pump = new Iap2LinkPump(
                new Iap2StreamDuplexAdapter(input, new ByteArrayOutputStream()), driver, listener);
        pump.start();
        truth(driverEntered.await(2, TimeUnit.SECONDS), "pump driver entered blocking start");
        boolean filled = true;
        for (int i = 0; i < Iap2LinkPump.MAX_COMMANDS; i++) {
            filled &= pump.sendControl(new byte[] { (byte) i });
        }
        truth(filled, "fill bounded command queue to configured limit");
        truth(pump.metricsSnapshot().maxQueueCommands == Iap2LinkPump.MAX_COMMANDS,
                "metrics capture maximum command queue depth");
        truth(!pump.sendControlAwaitCapacity(new byte[] { 99 }, 20L),
                "capacity wait honors timeout while queue remains full");
        Thread producer = new Thread(new Runnable() {
            public void run() {
                admitted[0] = pump.sendControlAwaitCapacity(new byte[] { 100 }, 1500L);
            }
        }, "iAP2-capacity-test-producer");
        producer.start();
        releaseDriver.countDown();
        producer.join(2000L);
        truth(!producer.isAlive(), "capacity waiter wakes after worker dequeue");
        truth(admitted[0], "capacity waiter admitted after dequeue");
        input.offer(1); // Wake the worker; subsequent reads emulate RFCOMM read timeouts.
        truth(delivered.await(5, TimeUnit.SECONDS), "all 65 commands delivered to driver across bounded turns");
        truth(pump.metricsSnapshot().commandsDelivered == Iap2LinkPump.MAX_COMMANDS + 1L,
                "metrics count delivered commands");
        input.finish();
        truth(closed.await(2, TimeUnit.SECONDS), "pump closes after test stream EOF");
    }

    private static void truth(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    /**
     * Deterministic carrier test double: waits briefly for input, then reports the
     * SocketTimeoutException that a timeout-configured RFCOMM socket would produce.
     */
    private static final class WakeableTimeoutInputStream extends java.io.InputStream {
        private int pending = -1;
        private boolean closed;

        public synchronized void offer(int value) {
            pending = value & 255;
            notifyAll();
        }

        public synchronized void finish() {
            closed = true;
            notifyAll();
        }

        public synchronized int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 255;
        }

        public synchronized int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (pending < 0 && !closed) {
                try {
                    wait(25L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test carrier interrupted");
                }
            }
            if (pending >= 0) {
                buffer[offset] = (byte) pending;
                pending = -1;
                return 1;
            }
            if (closed) return -1;
            throw new SocketTimeoutException("simulated RFCOMM read timeout");
        }
    }

    private static class DriverStub implements Iap2LinkPump.Driver, Iap2LinkPump.CommandSink {
        public void start(long now) throws IOException {}
        public void onBytes(byte[] bytes, int length, long now) {}
        public void onTimer(long now) {}
        public byte[] drainOutput() { return null; }
        public boolean isTerminal() { return false; }
        public String terminalReason() { return null; }
        public long nextDeadlineMillis() { return Long.MAX_VALUE; }
        public void sendControl(byte[] payload, long now) {}
    }

    private static class ListenerStub implements Iap2LinkPump.Listener {
        public void onReady() {}
        public void onClosed(String reason) {}
        public void onFailure(IOException failure) {}
    }
}
