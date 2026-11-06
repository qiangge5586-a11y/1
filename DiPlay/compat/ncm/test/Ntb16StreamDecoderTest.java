package com.shilapi.xcertplay.compat.ncm;

import java.util.List;

public final class Ntb16StreamDecoderTest {
    private Ntb16StreamDecoderTest() {
    }

    public static void main(String[] args) {
        decodesFragmentedNtb();
        decodesMultipleNtbBlocks();
        consumesOptionalShortPacketPad();
        doesNotConsumeNextNtbAsPad();
        resynchronizesAfterGarbage();
        enforcesBufferLimit();
        resetClearsPartialState();
        System.out.println("Ntb16StreamDecoderTest: PASS (7 cases)");
    }

    private static void decodesFragmentedNtb() {
        byte[] frame = new byte[] {1, 2, 3, 4, 5};
        byte[] ntb = Ntb16Codec.build(frame, 1);
        Ntb16StreamDecoder decoder = new Ntb16StreamDecoder();
        check(decoder.append(ntb, 0, 7).isEmpty(), "partial header waits");
        check(decoder.getBufferedByteCount() == 7, "partial bytes retained");
        List<byte[]> frames = decoder.append(ntb, 7, ntb.length - 7);
        check(frames.size() == 1, "fragmented NTB decoded");
        arrayEquals(frame, frames.get(0), "fragmented payload");
    }

    private static void decodesMultipleNtbBlocks() {
        byte[] first = Ntb16Codec.build(new byte[] {10}, 1);
        byte[] second = Ntb16Codec.build(new byte[] {20, 21}, 2);
        byte[] joined = concat(first, second);
        List<byte[]> frames = new Ntb16StreamDecoder().append(joined);
        check(frames.size() == 2, "two concatenated NTBs");
        arrayEquals(new byte[] {10}, frames.get(0), "first NTB frame");
        arrayEquals(new byte[] {20, 21}, frames.get(1), "second NTB frame");
    }

    private static void consumesOptionalShortPacketPad() {
        byte[] ntb = Ntb16Codec.build(new byte[484], 3);
        check(ntb.length == 513, "codec emits one pad byte");
        List<byte[]> frames = new Ntb16StreamDecoder().append(ntb);
        check(frames.size() == 1 && frames.get(0).length == 484, "frame before pad");
    }

    private static void doesNotConsumeNextNtbAsPad() {
        byte[] boundary = Ntb16Codec.build(new byte[484], 4);
        byte[] next = Ntb16Codec.build(new byte[] {77}, 5);
        byte[] stream = concat(boundary, next);
        List<byte[]> frames = new Ntb16StreamDecoder().append(stream);
        check(frames.size() == 2, "next NTH16 preserved after optional pad check");
        check(frames.get(0).length == 484, "boundary frame");
        arrayEquals(new byte[] {77}, frames.get(1), "next block frame");
    }

    private static void resynchronizesAfterGarbage() {
        byte[] ntb = Ntb16Codec.build(new byte[] {8, 9}, 6);
        byte[] stream = concat(new byte[] {99, 98, 97}, ntb);
        Ntb16StreamDecoder decoder = new Ntb16StreamDecoder();
        List<byte[]> frames = decoder.append(stream);
        check(frames.size() == 1, "frame found after leading garbage");
        check(decoder.getResyncCount() == 1, "one resync recorded");
        check(decoder.getSkippedByteCount() == 3, "garbage byte count");
    }

    private static void enforcesBufferLimit() {
        final Ntb16StreamDecoder decoder = new Ntb16StreamDecoder(64);
        expectIllegalState(new Runnable() {
            public void run() { decoder.append(new byte[65]); }
        }, "buffer cap");
    }

    private static void resetClearsPartialState() {
        Ntb16StreamDecoder decoder = new Ntb16StreamDecoder();
        byte[] ntb = Ntb16Codec.build(new byte[] {1}, 7);
        decoder.append(ntb, 0, 5);
        decoder.reset();
        check(decoder.getBufferedByteCount() == 0, "reset buffer count");
        check(decoder.getResyncCount() == 0, "reset resync count");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static void arrayEquals(byte[] expected, byte[] actual, String label) {
        check(expected.length == actual.length, label + " length");
        for (int i = 0; i < expected.length; i++) {
            check(expected[i] == actual[i], label + " byte " + i);
        }
    }

    private static void expectIllegalState(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label + " should reject input");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
