package com.shilapi.xcertplay.compat.iap2;

import java.util.List;

public final class Iap2CsmFrameCodecTest {
    private static int checks;

    public static void main(String[] args) {
        byte[] payload = new byte[] { 0x11, 0x22, (byte) 0xFE };
        byte[] frame = Iap2CsmFrameCodec.encode(0x1234, payload);
        eq(9, frame.length, "frame length");
        eq(0x40, frame[0] & 255, "start high");
        eq(0x40, frame[1] & 255, "start low");
        eq(0, frame[2] & 255, "length high");
        eq(9, frame[3] & 255, "length low");
        eq(0x12, frame[4] & 255, "message id high");
        eq(0x34, frame[5] & 255, "message id low");
        Iap2CsmFrameDecoder decoder = new Iap2CsmFrameDecoder();
        List<Iap2CsmFrameDecoder.Frame> decoded = decoder.feed(frame);
        eq(1, decoded.size(), "one decoded frame");
        eq(0x1234, decoded.get(0).messageId, "message id round trip");
        bytes(payload, decoded.get(0).payload, "payload round trip");
        byte[] empty = Iap2CsmFrameCodec.encode(0, new byte[0]);
        eq(6, empty.length, "empty frame has header only");
        byte[] maximum = Iap2CsmFrameCodec.encode(65535,
                new byte[Iap2CsmFrameCodec.MAX_PAYLOAD_BYTES]);
        eq(65535, maximum.length, "maximum frame length");
        rejects(65536, new byte[0], "message id overflow");
        rejects(0, new byte[Iap2CsmFrameCodec.MAX_PAYLOAD_BYTES + 1], "payload overflow");
        System.out.println("Iap2CsmFrameCodecTest: " + checks + " checks passed");
    }

    private static void rejects(int id, byte[] payload, String label) {
        checks++;
        try { Iap2CsmFrameCodec.encode(id, payload); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(label + " was accepted");
    }
    private static void eq(int expected, int actual, String label) {
        checks++;
        if (expected != actual) throw new AssertionError(label + ": " + expected + " != " + actual);
    }
    private static void bytes(byte[] expected, byte[] actual, String label) {
        checks++;
        if (expected.length != actual.length) throw new AssertionError(label + " length");
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) throw new AssertionError(label + " byte " + i);
        }
    }
}
