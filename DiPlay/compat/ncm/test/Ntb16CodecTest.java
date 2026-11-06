package com.shilapi.xcertplay.compat.ncm;

import java.util.List;

public final class Ntb16CodecTest {
    private Ntb16CodecTest() {
    }

    public static void main(String[] args) {
        roundTrip();
        sequenceIsLittleEndian();
        exactUsbPacketBoundaryGetsZlpMarker();
        invalidSignatureReturnsNoFrames();
        invalidRangeIsRejected();
        invalidHeaderReturnsNoFrames();
        maximumFrameLengthFitsU16BlockLength();
        rejectsInvalidBuildArguments();
        System.out.println("Ntb16CodecTest: PASS (8 cases)");
    }

    private static void roundTrip() {
        byte[] frame = new byte[] {0x00, 0x01, 0x02, 0x7F, (byte) 0x80, (byte) 0xFF};
        byte[] ntb = Ntb16Codec.build(frame, 7);
        List<byte[]> decoded = Ntb16Codec.parse(ntb);
        check(decoded.size() == 1, "round trip datagram count");
        arrayEquals(frame, decoded.get(0), "round trip payload");
    }

    private static void sequenceIsLittleEndian() {
        byte[] ntb = Ntb16Codec.build(new byte[] {1}, 0x1234);
        check((ntb[6] & 0xFF) == 0x34, "sequence low byte");
        check((ntb[7] & 0xFF) == 0x12, "sequence high byte");
    }

    private static void exactUsbPacketBoundaryGetsZlpMarker() {
        byte[] frame = new byte[484]; // 484 + 28-byte NTB header/table = 512
        byte[] ntb = Ntb16Codec.build(frame, 1);
        check(ntb.length == 513, "one-byte ZLP marker");
        check(Ntb16Codec.parse(ntb).size() == 1, "parser respects declared block length");
    }

    private static void invalidSignatureReturnsNoFrames() {
        byte[] ntb = Ntb16Codec.build(new byte[] {9, 8, 7}, 2);
        ntb[0] ^= 0x7F;
        check(Ntb16Codec.parse(ntb).isEmpty(), "bad NTH signature");
    }

    private static void invalidRangeIsRejected() {
        expectIllegalArgument(new Runnable() {
            public void run() {
                Ntb16Codec.parse(new byte[8], 7, 2);
            }
        }, "range validation");
    }

    private static void invalidHeaderReturnsNoFrames() {
        byte[] ntb = Ntb16Codec.build(new byte[] {1, 2, 3}, 3);
        ntb[4] = 0x0B; // NTH16 header length must be 12
        ntb[5] = 0;
        check(Ntb16Codec.parse(ntb).isEmpty(), "bad NTH header length");
    }

    private static void maximumFrameLengthFitsU16BlockLength() {
        byte[] frame = new byte[65507];
        byte[] ntb = Ntb16Codec.build(frame, 65535);
        check(ntb.length == 65535, "maximum NTB length");
        List<byte[]> decoded = Ntb16Codec.parse(ntb);
        check(decoded.size() == 1, "maximum frame decoded");
        check(decoded.get(0).length == 65507, "maximum frame size");
    }

    private static void rejectsInvalidBuildArguments() {
        expectIllegalArgument(new Runnable() {
            public void run() {
                Ntb16Codec.build(new byte[0], 0);
            }
        }, "empty frame");
        expectIllegalArgument(new Runnable() {
            public void run() {
                Ntb16Codec.build(new byte[] {1}, 65536);
            }
        }, "sequence overflow");
    }

    private static void arrayEquals(byte[] expected, byte[] actual, String label) {
        check(expected.length == actual.length, label + " length");
        for (int i = 0; i < expected.length; i++) {
            check(expected[i] == actual[i], label + " byte " + i);
        }
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label + " should reject input");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
