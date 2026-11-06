package com.shilapi.xcertplay.compat.ncm;

import java.util.List;
import java.util.Random;

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
        parsesMultipleDatagramsInOneNdp();
        parsesChainedNdpsAndStopsOnCycle();
        rejectsShortNdpTable();
        skipsDatagramOutsideDeclaredBlock();
        parsesBlockInsideLargerSourceBuffer();
        malformedRandomBuffersNeverThrow();
        System.out.println("Ntb16CodecTest: PASS (14 cases)");
    }

    private static void roundTrip() {
        byte[] frame = new byte[] {0x00, 0x01, 0x02, 0x7F, (byte) 0x80, (byte) 0xFF};
        List<byte[]> decoded = Ntb16Codec.parse(Ntb16Codec.build(frame, 7));
        check(decoded.size() == 1, "round trip datagram count");
        arrayEquals(frame, decoded.get(0), "round trip payload");
    }

    private static void sequenceIsLittleEndian() {
        byte[] ntb = Ntb16Codec.build(new byte[] {1}, 0x1234);
        check(u16(ntb, 6) == 0x1234, "sequence little endian");
    }

    private static void exactUsbPacketBoundaryGetsZlpMarker() {
        byte[] ntb = Ntb16Codec.build(new byte[484], 1);
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
            public void run() { Ntb16Codec.parse(new byte[8], 7, 2); }
        }, "range validation");
    }

    private static void invalidHeaderReturnsNoFrames() {
        byte[] ntb = Ntb16Codec.build(new byte[] {1, 2, 3}, 3);
        putU16(ntb, 4, 11);
        check(Ntb16Codec.parse(ntb).isEmpty(), "bad NTH header length");
    }

    private static void maximumFrameLengthFitsU16BlockLength() {
        byte[] ntb = Ntb16Codec.build(new byte[65507], 65535);
        check(ntb.length == 65535, "maximum NTB length");
        List<byte[]> decoded = Ntb16Codec.parse(ntb);
        check(decoded.size() == 1 && decoded.get(0).length == 65507,
                "maximum frame decoded");
    }

    private static void rejectsInvalidBuildArguments() {
        expectIllegalArgument(new Runnable() {
            public void run() { Ntb16Codec.build(new byte[0], 0); }
        }, "empty frame");
        expectIllegalArgument(new Runnable() {
            public void run() { Ntb16Codec.build(new byte[] {1}, 65536); }
        }, "sequence overflow");
    }

    private static void parsesMultipleDatagramsInOneNdp() {
        byte[] block = new byte[36];
        nth(block, 36, 12);
        ndp(block, 12, 20, 0);
        putU16(block, 20, 32); putU16(block, 22, 2);
        putU16(block, 24, 34); putU16(block, 26, 2);
        putU16(block, 28, 0);  putU16(block, 30, 0);
        block[32] = 0x11; block[33] = 0x12;
        block[34] = 0x21; block[35] = 0x22;
        List<byte[]> frames = Ntb16Codec.parse(block);
        check(frames.size() == 2, "two datagrams from one NDP");
        arrayEquals(new byte[] {0x11, 0x12}, frames.get(0), "first datagram");
        arrayEquals(new byte[] {0x21, 0x22}, frames.get(1), "second datagram");
    }

    private static void parsesChainedNdpsAndStopsOnCycle() {
        byte[] block = new byte[48];
        nth(block, 48, 12);
        ndp(block, 12, 16, 28);
        putU16(block, 20, 44); putU16(block, 22, 2);
        putU16(block, 24, 0); putU16(block, 26, 0);
        ndp(block, 28, 16, 12);
        putU16(block, 36, 46); putU16(block, 38, 2);
        putU16(block, 40, 0); putU16(block, 42, 0);
        block[44] = 0x31; block[45] = 0x32;
        block[46] = 0x41; block[47] = 0x42;
        List<byte[]> frames = Ntb16Codec.parse(block);
        check(frames.size() == 2, "two chained NDPs parsed once despite cycle");
        arrayEquals(new byte[] {0x31, 0x32}, frames.get(0), "first chained NDP");
        arrayEquals(new byte[] {0x41, 0x42}, frames.get(1), "second chained NDP");
    }

    private static void rejectsShortNdpTable() {
        byte[] block = Ntb16Codec.build(new byte[] {1}, 1);
        putU16(block, 16, 8); // header without the required terminating entry
        check(Ntb16Codec.parse(block).isEmpty(), "NDP shorter than header plus terminator");
    }

    private static void skipsDatagramOutsideDeclaredBlock() {
        byte[] block = Ntb16Codec.build(new byte[] {1, 2}, 1);
        putU16(block, 20, 65530);
        putU16(block, 22, 10);
        check(Ntb16Codec.parse(block).isEmpty(), "out-of-block datagram skipped");
    }

    private static void parsesBlockInsideLargerSourceBuffer() {
        byte[] ntb = Ntb16Codec.build(new byte[] {5, 6, 7}, 9);
        byte[] wrapped = new byte[ntb.length + 10];
        System.arraycopy(ntb, 0, wrapped, 5, ntb.length);
        List<byte[]> frames = Ntb16Codec.parse(wrapped, 5, ntb.length);
        check(frames.size() == 1, "nonzero source offset");
        arrayEquals(new byte[] {5, 6, 7}, frames.get(0), "offset payload");
    }

    private static void malformedRandomBuffersNeverThrow() {
        Random random = new Random(0x4E544231L);
        for (int i = 0; i < 2000; i++) {
            int length = random.nextInt(257);
            byte[] source = new byte[length + 7];
            random.nextBytes(source);
            int offset = random.nextInt(8);
            Ntb16Codec.parse(source, offset, length);
        }
    }

    private static void nth(byte[] b, int blockLength, int ndpOffset) {
        putU32(b, 0, Ntb16Codec.NTH16_SIGNATURE);
        putU16(b, 4, 12); putU16(b, 6, 1);
        putU16(b, 8, blockLength); putU16(b, 10, ndpOffset);
    }

    private static void ndp(byte[] b, int offset, int length, int next) {
        putU32(b, offset, Ntb16Codec.NDP16_SIGNATURE);
        putU16(b, offset + 4, length);
        putU16(b, offset + 6, next);
    }

    private static int u16(byte[] b, int offset) {
        return (b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8);
    }

    private static void putU16(byte[] b, int offset, int value) {
        b[offset] = (byte) value; b[offset + 1] = (byte) (value >>> 8);
    }

    private static void putU32(byte[] b, int offset, int value) {
        b[offset] = (byte) value;
        b[offset + 1] = (byte) (value >>> 8);
        b[offset + 2] = (byte) (value >>> 16);
        b[offset + 3] = (byte) (value >>> 24);
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
        if (!condition) throw new AssertionError(message);
    }
}
