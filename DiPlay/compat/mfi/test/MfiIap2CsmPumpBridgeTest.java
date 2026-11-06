package com.shilapi.xcertplay.compat.mfi;

import com.shilapi.xcertplay.compat.iap2.Iap2CsmFrameCodec;
import com.shilapi.xcertplay.compat.iap2.Iap2CsmReceiveCoordinator;
import com.shilapi.xcertplay.compat.iap2.Iap2LinkPump;
import com.shilapi.xcertplay.compat.iap2.Iap2ReceiveWindowModel;
import com.shilapi.xcertplay.compat.iap2.Iap2StreamDuplexAdapter;
import com.shilapi.xcertplay.compat.iap2.Iap2ParameterCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MfiIap2CsmPumpBridgeTest {
    private static int checks;
    private MfiIap2CsmPumpBridgeTest() { }

    public static void main(String[] args) throws Exception {
        final CountDownLatch driverEntered = new CountDownLatch(1);
        final CountDownLatch releaseDriver = new CountDownLatch(1);
        final CountDownLatch pumpClosed = new CountDownLatch(1);
        final CountDownLatch authenticationSucceeded = new CountDownLatch(1);
        final ByteArrayOutputStream outbound = new ByteArrayOutputStream();
        Iap2LinkPump.Driver base = new Iap2LinkPump.Driver() {
            public void start(long now) throws IOException {
                driverEntered.countDown();
                try {
                    if (!releaseDriver.await(3, TimeUnit.SECONDS))
                        throw new IOException("driver release timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("driver interrupted");
                }
            }
            public void onBytes(byte[] b, int n, long now) { }
            public void onTimer(long now) { }
            public byte[] drainOutput() { return null; }
            public boolean isTerminal() { return false; }
            public String terminalReason() { return null; }
            public long nextDeadlineMillis() { return Long.MAX_VALUE; }
        };
        Iap2LinkPump.Driver combined = new SinkDriver(base, new Iap2LinkPump.CommandSink() {
            public void sendControl(byte[] payload, long now) {
                outbound.write(payload, 0, payload.length);
            }
        });
        Iap2LinkPump pump = new Iap2LinkPump(
                new Iap2StreamDuplexAdapter(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()), combined, new Iap2LinkPump.Listener() {
                    public void onReady() { }
                    public void onClosed(String reason) { pumpClosed.countDown(); }
                    public void onFailure(IOException failure) { throw new AssertionError(failure); }
                });
        pump.start();
        truth(driverEntered.await(2, TimeUnit.SECONDS), "pump worker entered");

        final long generation = 81L;
        MfiOperationContext context = MfiOperationContext.start(5000L, generation,
                new MfiOperationContext.Cancellation());
        final MfiProviderSession provider = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) { return new byte[] { 0x31, 0x32 }; }
            public byte[] signChallenge(byte[] challenge, MfiOperationContext c) {
                truth(Arrays.equals(new byte[] { 0x41, 0x42 }, challenge), "challenge reaches provider");
                return new byte[] { 0x51, 0x52, 0x53 };
            }
        }, context, generation);
        MfiIap2CsmAdapter adapter = new MfiIap2CsmAdapter(
                new MfiIap2ExchangeCoordinator(provider, generation));
        Iap2CsmReceiveCoordinator receive = new Iap2CsmReceiveCoordinator();
        MfiIap2CsmPumpBridge bridge = new MfiIap2CsmPumpBridge(receive, adapter, pump, 64, 1000L);
        MfiIap2CsmWorker worker = new MfiIap2CsmWorker(bridge, new MfiIap2CsmWorker.Listener() {
            public void onAuthenticationSucceeded() { authenticationSucceeded.countDown(); }
            public void onAuthenticationFailed() { throw new AssertionError("unexpected auth failure"); }
            public void onFailure(IOException failure) { throw new AssertionError(failure); }
        });
        worker.start();

        byte[] requestCert = Iap2CsmFrameCodec.encode(0xAA00, new byte[0]);
        byte[] requestChallenge = Iap2CsmFrameCodec.encode(0xAA02,
                Iap2ParameterCodec.encode(0, new byte[] { 0x41, 0x42 }));
        byte[] success = Iap2CsmFrameCodec.encode(0xAA05, new byte[0]);
        byte[] inboundBytes = concat(requestCert, requestChallenge, success);
        List<Iap2ReceiveWindowModel.Packet> delivered =
                new ArrayList<Iap2ReceiveWindowModel.Packet>();
        delivered.add(new Iap2ReceiveWindowModel.Packet(1, 10, inboundBytes));
        truth(receive.acceptDeliveredPackets(delivered) == 3,
                "receive coordinator admits three CSM frames");

        truth(authenticationSucceeded.await(2, TimeUnit.SECONDS),
                "dedicated worker reports authentication success");
        truth(bridge.state() == MfiIap2ExchangeCoordinator.State.SUCCEEDED,
                "peer success is terminal");
        truth(provider.state() == MfiProviderSession.State.CLOSED && context.isCancelled(),
                "provider context released after peer success");

        releaseDriver.countDown();
        truth(pumpClosed.await(2, TimeUnit.SECONDS), "pump drains replies then observes EOF");
        byte[] expected = concat(
                Iap2CsmFrameCodec.encode(0xAA01, Iap2ParameterCodec.encode(0, new byte[] { 0x31, 0x32 })),
                Iap2CsmFrameCodec.encode(0xAA03, Iap2ParameterCodec.encode(0, new byte[] { 0x51, 0x52, 0x53 })));
        truth(Arrays.equals(expected, outbound.toByteArray()),
                "AA01 and AA03 sent in order through pump");
        System.out.println("MfiIap2CsmPumpBridgeTest: " + checks + " checks passed");
    }

    private static byte[] concat(byte[]... values) {
        int size = 0;
        for (int i = 0; i < values.length; i++) size += values[i].length;
        byte[] result = new byte[size];
        int offset = 0;
        for (int i = 0; i < values.length; i++) {
            System.arraycopy(values[i], 0, result, offset, values[i].length);
            offset += values[i].length;
        }
        return result;
    }
    private static void truth(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    private static final class SinkDriver implements Iap2LinkPump.Driver, Iap2LinkPump.CommandSink {
        private final Iap2LinkPump.Driver delegate;
        private final Iap2LinkPump.CommandSink sink;
        SinkDriver(Iap2LinkPump.Driver d, Iap2LinkPump.CommandSink s) { delegate = d; sink = s; }
        public void start(long n) throws IOException { delegate.start(n); }
        public void onBytes(byte[] b, int l, long n) throws IOException { delegate.onBytes(b,l,n); }
        public void onTimer(long n) throws IOException { delegate.onTimer(n); }
        public byte[] drainOutput() throws IOException { return delegate.drainOutput(); }
        public boolean isTerminal() { return delegate.isTerminal(); }
        public String terminalReason() { return delegate.terminalReason(); }
        public long nextDeadlineMillis() { return delegate.nextDeadlineMillis(); }
        public void sendControl(byte[] payload, long now) throws IOException { sink.sendControl(payload, now); }
    }
}
