package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Iap2LinkPumpContractTest {
    public static void main(String[] args) throws Exception {
        testCommandBoundsAndEofShutdown();
        testRejectsOversizedPayload();
        System.out.println("Iap2LinkPumpContractTest: 4 checks passed");
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

    private static void truth(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static final class DriverStub implements Iap2LinkPump.Driver, Iap2LinkPump.CommandSink {
        public void start(long now) {}
        public void onBytes(byte[] bytes, int length, long now) {}
        public void onTimer(long now) {}
        public byte[] drainOutput() { return null; }
        public boolean isTerminal() { return false; }
        public String terminalReason() { return null; }
        public long nextDeadlineMillis() { return Long.MAX_VALUE; }
        public void sendControl(byte[] payload, long now) {}
    }

    private static final class ListenerStub implements Iap2LinkPump.Listener {
        public void onReady() {}
        public void onClosed(String reason) {}
        public void onFailure(IOException failure) {}
    }
}
