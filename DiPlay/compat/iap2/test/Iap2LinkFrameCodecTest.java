package com.shilapi.xcertplay.compat.iap2;

import java.util.Arrays;

public final class Iap2LinkFrameCodecTest {
    private static int checks;

    public static void main(String[] args) {
        sourceDerivedAckOnlyGoldenVector();
        sourceDerivedDataFrameGoldenVector();
        rejectsInvalidHeaderAndPayloadChecksums();
        validatesHeaderFieldsAndFrameBounds();
        wrapsEightBitSequences();
        System.out.println("Iap2LinkFrameCodecTest: PASS (" + checks + " checks)");
    }

    private static void sourceDerivedAckOnlyGoldenVector() {
        byte[] expected = hex("FF 5A 00 09 40 63 00 00 FB");
        byte[] actual = Iap2LinkFrameCodec.encode(0x40, 99, 0, 0, null);
        bytes(expected, actual);
        Iap2LinkFrameCodec.Header h = Iap2LinkFrameCodec.parseHeader(Arrays.copyOf(actual, 9));
        notNull(h);
        eq(9, h.length); eq(0x40, h.control); eq(99, h.sequence);
        eq(0, h.acknowledgement); eq(0, h.sessionId);
    }

    private static void sourceDerivedDataFrameGoldenVector() {
        byte[] expected = hex("FF 5A 00 0D 40 64 63 0A 89 01 02 03 FA");
        byte[] actual = Iap2LinkFrameCodec.encode(0x40, 100, 99, 10,
                new byte[] { 1, 2, 3 });
        bytes(expected, actual);
        Iap2LinkFrameCodec.Header h = Iap2LinkFrameCodec.parseHeader(Arrays.copyOf(actual, 9));
        notNull(h);
        eq(13, h.length); eq(100, h.sequence); eq(99, h.acknowledgement); eq(10, h.sessionId);
        truth(Iap2LinkFrameCodec.payloadChecksumValid(Arrays.copyOfRange(actual, 9, actual.length)));
    }

    private static void rejectsInvalidHeaderAndPayloadChecksums() {
        byte[] frame = Iap2LinkFrameCodec.encode(0x40, 100, 99, 10, new byte[] { 1, 2, 3 });
        frame[8] ^= 1;
        isNull(Iap2LinkFrameCodec.parseHeader(Arrays.copyOf(frame, 9)));
        byte[] body = hex("01 02 03 FA");
        truth(Iap2LinkFrameCodec.payloadChecksumValid(body));
        body[3] ^= 1;
        falsity(Iap2LinkFrameCodec.payloadChecksumValid(body));
        byte[] badMarker = hex("FF 55 00 09 40 63 00 00");
        byte[] markerHeader = new byte[9];
        System.arraycopy(badMarker, 0, markerHeader, 0, 8);
        markerHeader[8] = (byte) Iap2LinkFrameCodec.checksum(markerHeader, 8);
        isNull(Iap2LinkFrameCodec.parseHeader(markerHeader));
    }

    private static void validatesHeaderFieldsAndFrameBounds() {
        byte[] tooShort = Iap2LinkFrameCodec.encode(0x40, 1, 0, 0, null);
        tooShort[2] = 0;
        tooShort[3] = 8;
        tooShort[8] = (byte) Iap2LinkFrameCodec.checksum(tooShort, 8);
        isNull(Iap2LinkFrameCodec.parseHeader(tooShort));
        byte[] maxPayload = new byte[Iap2LinkFrameCodec.MAX_PAYLOAD_BYTES];
        byte[] maxFrame = Iap2LinkFrameCodec.encode(0x40, 1, 0, 10, maxPayload);
        eq(65535, maxFrame.length);
        expectIllegalArgument(new Runnable() {
            public void run() {
                Iap2LinkFrameCodec.encode(0x40, 1, 0, 10, new byte[65526]);
            }
        });
        expectIllegalArgument(new Runnable() {
            public void run() {
                Iap2LinkFrameCodec.encode(0x40, 256, 0, 10, null);
            }
        });
    }

    private static void wrapsEightBitSequences() {
        eq(0, Iap2LinkFrameCodec.nextSequence(255));
        eq(100, Iap2LinkFrameCodec.nextSequence(99));
        eq(2, Iap2LinkFrameCodec.sequenceDistance(1, Integer.valueOf(255)));
        eq(255, Iap2LinkFrameCodec.sequenceDistance(254, Integer.valueOf(255)));
        eq(0, Iap2LinkFrameCodec.sequenceDistance(42, null));
    }

    private static byte[] hex(String value) {
        String[] parts = value.split(" ");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = (byte) Integer.parseInt(parts[i], 16);
        return out;
    }
    private static void bytes(byte[] a, byte[] b) { checks++; if (!Arrays.equals(a,b)) throw new AssertionError("byte vector mismatch"); }
    private static void eq(int a, int b) { checks++; if (a != b) throw new AssertionError(a + " != " + b); }
    private static void truth(boolean b) { checks++; if (!b) throw new AssertionError("expected true"); }
    private static void falsity(boolean b) { checks++; if (b) throw new AssertionError("expected false"); }
    private static void notNull(Object o) { checks++; if (o == null) throw new AssertionError("expected non-null"); }
    private static void isNull(Object o) { checks++; if (o != null) throw new AssertionError("expected null"); }
    private static void expectIllegalArgument(Runnable r) {
        checks++;
        try { r.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
