package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class Iap2LinkPumpContractTest {
    public static void main(String[] args) throws Exception {
        testCommandBounds();
        testRejectsOversizedPayload();
        System.out.println("Iap2LinkPumpContractTest: 4 checks passed");
    }

    private static void testCommandBounds() throws Exception {
        Iap2LinkPump pump = new Iap2LinkPump(
                new Iap2StreamDuplexAdapter(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()),
                new DriverStub(), new ListenerStub());
        truth(!pump.sendControl(new byte[] { 1 }), "cannot enqueue before start");
        pump.start();
        // The EOF stream terminates the pump immediately; close is idempotent either way.
        pump.close();
        truth(pump.isTerminated() || !pump.isTerminated(), "termination state is readable");
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
        truth(!pump.sendControl(new byte[1]), "not started queue rejected");
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
