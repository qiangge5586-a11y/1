package com.shilapi.xcertplay.compat.mfi;

import com.shilapi.xcertplay.compat.iap2.Iap2CsmFrameCodec;
import com.shilapi.xcertplay.compat.iap2.Iap2ParameterCodec;
import java.util.Arrays;
import java.util.List;

public final class MfiIap2MessageCodecTest {
    private static int checks;
    private MfiIap2MessageCodecTest() { }
    public static void main(String[] args) {
        byte[] cert = new byte[] { 1, 2, 3, (byte) 0xfe };
        MfiIap2MessageCodec.Frame parsedCert = MfiIap2MessageCodec.parseFrame(
                MfiIap2MessageCodec.encodeCertificate(cert));
        check(parsedCert.messageId == 0xAA01, "certificate message id");
        List<Iap2ParameterCodec.Parameter> certParams = Iap2ParameterCodec.parse(parsedCert.payload());
        check(Arrays.equals(cert, Iap2ParameterCodec.first(certParams, 0).value()), "certificate parameter zero");

        byte[] signature = new byte[] { 9, 8, 7 };
        MfiIap2MessageCodec.Frame parsedSig = MfiIap2MessageCodec.parseFrame(
                MfiIap2MessageCodec.encodeSignature(signature));
        check(parsedSig.messageId == 0xAA03, "signature message id");
        check(Arrays.equals(signature, Iap2ParameterCodec.first(
                Iap2ParameterCodec.parse(parsedSig.payload()), 0).value()), "signature parameter zero");

        byte[] leaf = new byte[] { 0x11, 0x12 };
        byte[] intermediate = new byte[] { 0x21, 0x22, 0x23 };
        MfiIap2MessageCodec.Frame baa = MfiIap2MessageCodec.parseFrame(
                MfiIap2MessageCodec.encodeBaaCertificatePackage(leaf, intermediate));
        List<Iap2ParameterCodec.Parameter> baaParams = Iap2ParameterCodec.parse(baa.payload());
        check(baaParams.size() == 3, "BAA has three parameters");
        check(Arrays.equals(leaf, Iap2ParameterCodec.first(baaParams, 0).value()), "BAA leaf");
        check(Arrays.equals(new byte[] { 1 }, Iap2ParameterCodec.first(baaParams, 1).value()), "BAA type marker");
        check(Arrays.equals(intermediate, Iap2ParameterCodec.first(baaParams, 2).value()), "BAA intermediate");

        byte[] body = Iap2ParameterCodec.encodeList(new Iap2ParameterCodec.Parameter[] {
                new Iap2ParameterCodec.Parameter(0, leaf),
                new Iap2ParameterCodec.Parameter(2, intermediate)
        });
        byte[] passedThrough = MfiIap2MessageCodec.parseFrame(
                MfiIap2MessageCodec.encodeBaaCertificateBody(body)).payload();
        check(Arrays.equals(body, passedThrough), "BAA body is passed through without wrapping");

        byte[] challenge = new byte[] { 3, 4, 5, 6 };
        byte[] challengeFrame = Iap2CsmFrameCodec.encode(0xAA02, Iap2ParameterCodec.encode(0, challenge));
        check(Arrays.equals(challenge, MfiIap2MessageCodec.decodeChallenge(challengeFrame)),
                "challenge parameter zero");
        expectFailure(new Runnable() { public void run() {
            MfiIap2MessageCodec.decodeChallenge(Iap2CsmFrameCodec.encode(
                    0xAA01, Iap2ParameterCodec.encode(0, challenge)));
        }}, "wrong challenge message id");
        expectFailure(new Runnable() { public void run() {
            MfiIap2MessageCodec.decodeChallenge(new byte[] { 0x40, 0x40, 0, 6, (byte) 0xAA, 2 });
        }}, "missing challenge parameter");
        expectFailure(new Runnable() { public void run() {
            Iap2ParameterCodec.parse(new byte[] { 0, 3, 0, 0 });
        }}, "parameter length below header");
        expectFailure(new Runnable() { public void run() {
            Iap2ParameterCodec.parse(new byte[] { 0, 5, 0, 0, 1 });
        }}, "truncated parameter payload");
        expectFailure(new Runnable() { public void run() {
            MfiIap2MessageCodec.parseFrame(new byte[] { 0, 0x41, 0, 6, 0, 1 });
        }}, "bad frame marker");
        expectFailure(new Runnable() { public void run() {
            MfiIap2MessageCodec.parseFrame(new byte[] { 0x40, 0x40, 0, 7, 0, 1 });
        }}, "frame length mismatch");
        System.out.println("MfiIap2MessageCodecTest: " + checks + " checks passed");
    }
    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }
    private static void expectFailure(Runnable action, String name) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(name);
    }
}
