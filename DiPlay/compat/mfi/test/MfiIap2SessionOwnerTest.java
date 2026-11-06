package com.shilapi.xcertplay.compat.mfi;

import com.shilapi.xcertplay.compat.iap2.Iap2CsmReceiveCoordinator;
import com.shilapi.xcertplay.compat.iap2.Iap2LinkPump;
import com.shilapi.xcertplay.compat.iap2.Iap2StreamDuplexAdapter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

public final class MfiIap2SessionOwnerTest {
    private static int checks;
    private MfiIap2SessionOwnerTest() { }

    public static void main(String[] args) throws Exception {
        rejectsInvalidGeneration();
        closeInvalidatesPacketAdmission();
        System.out.println("MfiIap2SessionOwnerTest: " + checks + " checks passed");
    }

    private static void rejectsInvalidGeneration() {
        try {
            new MfiIap2SessionOwner(0L, new Iap2CsmReceiveCoordinator(),
                    null, listener());
            throw new AssertionError("zero generation accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void closeInvalidatesPacketAdmission() throws Exception {
        final Iap2LinkPump pump = new Iap2LinkPump(
                new Iap2StreamDuplexAdapter(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()),
                new Iap2LinkPump.Driver() {
                    public void start(long n) { }
                    public void onBytes(byte[] b, int l, long n) { }
                    public void onTimer(long n) { }
                    public byte[] drainOutput() { return null; }
                    public boolean isTerminal() { return false; }
                    public String terminalReason() { return null; }
                    public long nextDeadlineMillis() { return Long.MAX_VALUE; }
                }, new Iap2LinkPump.Listener() {
                    public void onReady() { }
                    public void onClosed(String reason) { }
                    public void onFailure(IOException failure) { }
                });
        MfiOperationContext context = MfiOperationContext.start(5000L, 91L,
                new MfiOperationContext.Cancellation());
        MfiProviderSession provider = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) { return new byte[] { 1 }; }
            public byte[] signChallenge(byte[] challenge, MfiOperationContext c) {
                return new byte[] { 2 };
            }
        }, context, 91L);
        MfiIap2CsmAdapter adapter = new MfiIap2CsmAdapter(
                new MfiIap2ExchangeCoordinator(provider, 91L));
        Iap2CsmReceiveCoordinator receive = new Iap2CsmReceiveCoordinator();
        MfiIap2CsmPumpBridge bridge = new MfiIap2CsmPumpBridge(
                receive, adapter, pump, 64, 100L);
        MfiIap2SessionOwner owner = new MfiIap2SessionOwner(
                91L, receive, bridge, listener());
        check(owner.generation() == 91L, "generation retained");
        owner.start();
        check(owner.isActive(), "active after start");
        owner.close();
        owner.close();
        check(!owner.isActive(), "close is idempotent and invalidates generation");
        try {
            owner.acceptDeliveredPackets(Collections.<com.shilapi.xcertplay.compat.iap2.Iap2ReceiveWindowModel.Packet>emptyList());
            throw new AssertionError("closed generation accepted packets");
        } catch (IllegalStateException expected) {
            checks++;
        }
        check(context.isCancelled(), "close cancels generation provider context");
    }

    private static MfiIap2CsmWorker.Listener listener() {
        return new MfiIap2CsmWorker.Listener() {
            public void onAuthenticationSucceeded() { }
            public void onAuthenticationFailed() { }
            public void onFailure(IOException failure) { }
        };
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
