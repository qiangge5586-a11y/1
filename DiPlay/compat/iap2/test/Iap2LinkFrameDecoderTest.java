package com.shilapi.xcertplay.compat.iap2;

import java.util.List;
import java.util.Random;

public final class Iap2LinkFrameDecoderTest {
    private static int checks;

    public static void main(String[] args) {
        fragmentedAndCoalescedFrames();
        everySplitBoundaryAndBytewiseFeed();
        resynchronizesAndCountsInvalidCandidates();
        rejectsPayloadChecksumAndContinues();
        randomizedFragmentationAndCoalescing();
        decodesMaximumFrameInBoundedChunks();
        drainsMaximumFrameAndFollowingControlFrameAtBufferBoundary();
        enforcesOriginalReadChunkBound();
        resetsSessionStateAndDiagnostics();
        System.out.println("Iap2LinkFrameDecoderTest: PASS (" + checks + " checks)");
    }

    private static void fragmentedAndCoalescedFrames() {
        Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        byte[] ack = Iap2LinkFrameCodec.encode(0x40, 99, 98, 0, null);
        byte[] data = Iap2LinkFrameCodec.encode(0x40, 100, 99, 10, new byte[] {4,5,6});
        List<Iap2LinkFrameDecoder.Frame> out = d.feed(slice(ack, 0, 4));
        eq(0, out.size()); eq(4, d.pendingBytes());
        out = d.feed(concat(slice(ack, 4, ack.length), data));
        eq(2, out.size());
        eq(0x40, out.get(0).control); eq(99, out.get(0).sequence);
        isNull(out.get(0).payload);
        eq(100, out.get(1).sequence); eq(99, out.get(1).acknowledgement);
        eq(10, out.get(1).sessionId); bytes(new byte[] {4,5,6}, out.get(1).payload);
        eq(0, d.pendingBytes());
    }

    private static void everySplitBoundaryAndBytewiseFeed() {
        byte[] payload = new byte[] {0, 1, 0x5a, (byte)0xff, 0x40, 0x20};
        byte[] frame = Iap2LinkFrameCodec.encode(0x40, 201, 200, 10, payload);
        for (int split = 1; split < frame.length; split++) {
            Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
            eq(0, d.feed(slice(frame, 0, split)).size());
            List<Iap2LinkFrameDecoder.Frame> out = d.feed(slice(frame, split, frame.length));
            eq(1, out.size()); eq(201, out.get(0).sequence); bytes(payload, out.get(0).payload);
            eq(0, d.pendingBytes());
        }
        Iap2LinkFrameDecoder bytewise = new Iap2LinkFrameDecoder();
        for (int i = 0; i < frame.length; i++) {
            List<Iap2LinkFrameDecoder.Frame> out = bytewise.feed(slice(frame, i, i + 1));
            if (i + 1 < frame.length) eq(0, out.size());
            else { eq(1, out.size()); bytes(payload, out.get(0).payload); }
        }
        eq(0, bytewise.pendingBytes());
    }

    private static void resynchronizesAndCountsInvalidCandidates() {
        Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        byte[] invalidHeader = Iap2LinkFrameCodec.encode(0x40, 1, 0, 0, null);
        invalidHeader[8] ^= 0x01;
        byte[] valid = Iap2LinkFrameCodec.encode(0x40, 2, 1, 10, new byte[] {7});
        byte[] garbage = new byte[] {0x12, 0x34, (byte)0xff, 0x00, 0x55};
        List<Iap2LinkFrameDecoder.Frame> out = d.feed(concat(garbage, concat(invalidHeader, valid)));
        eq(1, out.size()); eq(2, out.get(0).sequence);
        eq(1L, d.getInvalidHeaders());
        eq(5L, d.getDiscardedBytes());
    }

    private static void rejectsPayloadChecksumAndContinues() {
        Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        byte[] bad = Iap2LinkFrameCodec.encode(0x40, 5, 4, 10, new byte[] {1,2,3});
        bad[bad.length - 1] ^= 0x01;
        byte[] good = Iap2LinkFrameCodec.encode(0x40, 6, 5, 10, new byte[] {9});
        List<Iap2LinkFrameDecoder.Frame> out = d.feed(concat(bad, good));
        eq(1, out.size()); eq(6, out.get(0).sequence); bytes(new byte[] {9}, out.get(0).payload);
        eq(1L, d.getInvalidPayloadChecksums());
    }

    private static void randomizedFragmentationAndCoalescing() {
        Random random = new Random(0x1A2F2026L);
        Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        for (int n = 0; n < 100; n++) {
            int size = random.nextInt(512);
            byte[] payload = new byte[size]; random.nextBytes(payload);
            int seq = random.nextInt(256);
            byte[] encoded = Iap2LinkFrameCodec.encode(0x40, seq, (seq - 1) & 255, 10, payload);
            int offset = 0;
            while (offset < encoded.length) {
                int count = Math.min(encoded.length - offset, random.nextInt(97) + 1);
                List<Iap2LinkFrameDecoder.Frame> out = d.feed(slice(encoded, offset, offset + count));
                offset += count;
                if (offset < encoded.length) eq(0, out.size());
                else { eq(1, out.size()); eq(seq, out.get(0).sequence); bytes(payload, out.get(0).payload); }
            }
            eq(0, d.pendingBytes());
        }
    }

    private static void decodesMaximumFrameInBoundedChunks() {
        Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        byte[] payload = new byte[Iap2LinkFrameCodec.MAX_PAYLOAD_BYTES];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte)(i * 13);
        byte[] encoded = Iap2LinkFrameCodec.encode(0x40, 77, 76, 10, payload);
        eq(65535, encoded.length);
        int offset = 0;
        List<Iap2LinkFrameDecoder.Frame> out = null;
        while (offset < encoded.length) {
            int count = Math.min(Iap2LinkFrameDecoder.MAX_INPUT_CHUNK_BYTES, encoded.length - offset);
            out = d.feed(slice(encoded, offset, offset + count));
            offset += count;
            if (offset < encoded.length) eq(0, out.size());
        }
        eq(1, out.size()); eq(77, out.get(0).sequence); bytes(payload, out.get(0).payload);
        eq(0, d.pendingBytes());
    }


    private static void drainsMaximumFrameAndFollowingControlFrameAtBufferBoundary() {
        Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        byte[] payload = new byte[Iap2LinkFrameCodec.MAX_PAYLOAD_BYTES];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte)(i * 7 + 3);
        byte[] maximum = Iap2LinkFrameCodec.encode(0x40, 88, 87, 10, payload);
        byte[] following = Iap2LinkFrameCodec.encode(0x40, 89, 88, 0, null);
        byte[] stream = concat(maximum, following);
        java.util.ArrayList<Iap2LinkFrameDecoder.Frame> decoded =
                new java.util.ArrayList<Iap2LinkFrameDecoder.Frame>();
        int offset = 0;
        while (offset < stream.length) {
            int count = Math.min(Iap2LinkFrameDecoder.MAX_INPUT_CHUNK_BYTES,
                    stream.length - offset);
            decoded.addAll(d.feed(slice(stream, offset, offset + count)));
            offset += count;
            truth(d.pendingBytes() <= Iap2LinkFrameCodec.MAX_WIRE_FRAME_BYTES);
        }
        eq(2, decoded.size());
        eq(88, decoded.get(0).sequence);
        bytes(payload, decoded.get(0).payload);
        eq(89, decoded.get(1).sequence);
        isNull(decoded.get(1).payload);
        eq(0, d.pendingBytes());
    }

    private static void enforcesOriginalReadChunkBound() {
        final Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        d.feed(new byte[] {(byte)0xff});
        expectIllegalArgument(new Runnable() {
            public void run() { d.feed(new byte[Iap2LinkFrameDecoder.MAX_INPUT_CHUNK_BYTES + 1]); }
        });
        eq(0, d.pendingBytes());
    }

    private static void resetsSessionStateAndDiagnostics() {
        Iap2LinkFrameDecoder d = new Iap2LinkFrameDecoder();
        d.feed(new byte[] {1,2,3,4});
        eq(3L, d.getDiscardedBytes());
        d.reset();
        eq(0, d.pendingBytes()); eq(0L, d.getDiscardedBytes());
        eq(0L, d.getInvalidHeaders()); eq(0L, d.getInvalidPayloadChecksums());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length); System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
    private static byte[] slice(byte[] a, int from, int to) {
        byte[] out = new byte[to - from]; System.arraycopy(a, from, out, 0, out.length); return out;
    }
    private static void bytes(byte[] a, byte[] b) {
        checks++; if (b == null || a.length != b.length) throw new AssertionError("byte length mismatch");
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) throw new AssertionError("byte mismatch at " + i);
    }
    private static void eq(long a, long b) { checks++; if (a != b) throw new AssertionError(a + " != " + b); }
    private static void isNull(Object value) { checks++; if (value != null) throw new AssertionError("expected null"); }
    private static void truth(boolean value) { checks++; if (!value) throw new AssertionError("expected true"); }
    private static void expectIllegalArgument(Runnable r) {
        checks++; try { r.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
