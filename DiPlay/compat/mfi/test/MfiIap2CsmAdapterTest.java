package com.shilapi.xcertplay.compat.mfi;

import com.shilapi.xcertplay.compat.iap2.Iap2CsmFrameCodec;
import com.shilapi.xcertplay.compat.iap2.Iap2ParameterCodec;
import java.util.Arrays;

public final class MfiIap2CsmAdapterTest {
    private static int checks;
    private MfiIap2CsmAdapterTest() { }

    public static void main(String[] args) {
        final long generation = 72L;
        MfiOperationContext context = MfiOperationContext.start(5000L, generation,
                new MfiOperationContext.Cancellation());
        MfiProviderSession provider = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) {
                return new byte[] { 0x31, 0x32, 0x33 };
            }
            public byte[] signChallenge(byte[] challenge, MfiOperationContext c) {
                check(Arrays.equals(new byte[] { 0x51, 0x52 }, challenge), "provider receives extracted challenge");
                return new byte[] { 0x61, 0x62 };
            }
        }, context, generation);
        MfiIap2CsmAdapter adapter = new MfiIap2CsmAdapter(
                new MfiIap2ExchangeCoordinator(provider, generation));

        byte[] certReply = adapter.onInboundFrame(Iap2CsmFrameCodec.encode(0xAA00, new byte[0]));
        MfiIap2MessageCodec.Frame certFrame = MfiIap2MessageCodec.parseFrame(certReply);
        check(certFrame.messageId == 0xAA01, "AA00 maps to AA01");
        check(Arrays.equals(new byte[] { 0x31, 0x32, 0x33 }, Iap2ParameterCodec.first(
                Iap2ParameterCodec.parse(certFrame.payload()), 0).value()), "certificate frame body");

        byte[] challenge = Iap2CsmFrameCodec.encode(0xAA02, Iap2ParameterCodec.encode(0,
                new byte[] { 0x51, 0x52 }));
        byte[] signatureReply = adapter.onInboundFrame(challenge);
        MfiIap2MessageCodec.Frame signatureFrame = MfiIap2MessageCodec.parseFrame(signatureReply);
        check(signatureFrame.messageId == 0xAA03, "AA02 maps to AA03");
        check(Arrays.equals(new byte[] { 0x61, 0x62 }, Iap2ParameterCodec.first(
                Iap2ParameterCodec.parse(signatureFrame.payload()), 0).value()), "signature frame body");

        check(adapter.onInboundFrame(Iap2CsmFrameCodec.encode(0xAA05, new byte[0])) == null,
                "AA05 has no outbound reply");
        check(adapter.state() == MfiIap2ExchangeCoordinator.State.SUCCEEDED, "AA05 terminal success");

        MfiOperationContext badContext = MfiOperationContext.start(5000L, generation + 1,
                new MfiOperationContext.Cancellation());
        MfiProviderSession badProvider = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) { return new byte[] { 1 }; }
            public byte[] signChallenge(byte[] b, MfiOperationContext c) { return new byte[] { 2 }; }
        }, badContext, generation + 1);
        final MfiIap2CsmAdapter badAdapter = new MfiIap2CsmAdapter(
                new MfiIap2ExchangeCoordinator(badProvider, generation + 1));
        expectFailure(new Runnable() { public void run() {
            badAdapter.onInboundFrame(Iap2CsmFrameCodec.encode(0xAA02, Iap2ParameterCodec.encode(0,
                    new byte[] { 1 })));
        }}, "out-of-order challenge rejected");
        check(badAdapter.state() == MfiIap2ExchangeCoordinator.State.FAILED,
                "protocol failure fails adapter session");
        System.out.println("MfiIap2CsmAdapterTest: " + checks + " checks passed");
    }

    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }
    private static void expectFailure(Runnable action, String name) {
        checks++;
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(name);
    }
}
