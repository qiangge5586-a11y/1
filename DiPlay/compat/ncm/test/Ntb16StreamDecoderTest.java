package com.shilapi.xcertplay.compat.ncm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.ByteArrayOutputStream;

public final class Ntb16StreamDecoderTest {
    private Ntb16StreamDecoderTest() {
    }

    public static void main(String[] args) {
        decodesFragmentedNtb();
        decodesMultipleNtbBlocks();
        consumesOptionalShortPacketPad();
        doesNotConsumeNextNtbAsPad();
        resynchronizesAfterGarbage();
        resynchronizesAcrossInputChunks();
        consumesPadArrivingInSeparateChunk();
        preservesPartialNextHeaderAfterBoundaryBlock();
        enforcesBufferLimit();
        resetClearsPartialState();
        randomizedChunkingPreservesFrameOrder();
        System.out.println("Ntb16StreamDecoderTest: PASS (11 cases)");
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

    private static void resynchronizesAcrossInputChunks() {
        byte[] ntb = Ntb16Codec.build(new byte[] {31, 32}, 8);
        Ntb16StreamDecoder decoder = new Ntb16StreamDecoder();
        check(decoder.append(new byte[] {99, 98, 97}).isEmpty(), "garbage prefix waits for more bytes");
        check(decoder.append(ntb, 0, 5).isEmpty(), "partial header after garbage waits");
        List<byte[]> frames = decoder.append(ntb, 5, ntb.length - 5);
        check(frames.size() == 1, "resync finds NTB after chunk boundary");
        arrayEquals(new byte[] {31, 32}, frames.get(0), "resynced fragmented frame");
        check(decoder.getSkippedByteCount() == 3, "cross-chunk garbage count");
    }

    private static void consumesPadArrivingInSeparateChunk() {
        byte[] boundary = Ntb16Codec.build(new byte[484], 9);
        byte[] next = Ntb16Codec.build(new byte[] {88}, 10);
        Ntb16StreamDecoder decoder = new Ntb16StreamDecoder();
        List<byte[]> first = decoder.append(boundary, 0, 512);
        check(first.size() == 1 && first.get(0).length == 484, "boundary NTB emitted before pad");
        check(decoder.getBufferedByteCount() == 0, "no pad buffered yet");
        byte[] remainder = concat(new byte[] {0}, next);
        List<byte[]> second = decoder.append(remainder);
        check(second.size() == 1, "separate pad consumed before next NTB");
        arrayEquals(new byte[] {88}, second.get(0), "frame after separately delivered pad");
    }

    private static void preservesPartialNextHeaderAfterBoundaryBlock() {
        byte[] boundaryWithPad = Ntb16Codec.build(new byte[484], 11);
        byte[] boundaryBlock = new byte[512];
        System.arraycopy(boundaryWithPad, 0, boundaryBlock, 0, 512);
        byte[] next = Ntb16Codec.build(new byte[] {66, 67}, 12);
        Ntb16StreamDecoder decoder = new Ntb16StreamDecoder();
        List<byte[]> first = decoder.append(boundaryBlock);
        check(first.size() == 1, "boundary block emitted");
        check(decoder.append(next, 0, 4).isEmpty(), "partial next NTH16 retained rather than mistaken for pad");
        List<byte[]> second = decoder.append(next, 4, next.length - 4);
        check(second.size() == 1, "next NTB decoded after header completion");
        arrayEquals(new byte[] {66, 67}, second.get(0), "partial next header payload");
    }

    private static void randomizedChunkingPreservesFrameOrder() {
        Random random = new Random(0x4E434D31L);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        List<byte[]> expected = new ArrayList<byte[]>();
        for (int i = 0; i < 40; i++) {
            byte[] frame = new byte[1 + random.nextInt(300)];
            random.nextBytes(frame);
            expected.add(frame);
            byte[] ntb = Ntb16Codec.build(frame, i);
            stream.write(ntb, 0, ntb.length);
            if (i != 39) {
                int garbageLength = 1 + random.nextInt(5);
                for (int g = 0; g < garbageLength; g++) stream.write(0x55);
            }
        }

        byte[] input = stream.toByteArray();
        Ntb16StreamDecoder decoder = new Ntb16StreamDecoder();
        List<byte[]> actual = new ArrayList<byte[]>();
        int offset = 0;
        while (offset < input.length) {
            int count = Math.min(1 + random.nextInt(41), input.length - offset);
            actual.addAll(decoder.append(input, offset, count));
            offset += count;
        }
        check(actual.size() == expected.size(), "random chunking frame count");
        for (int i = 0; i < expected.size(); i++) {
            arrayEquals(expected.get(i), actual.get(i), "random chunk frame " + i);
        }
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
