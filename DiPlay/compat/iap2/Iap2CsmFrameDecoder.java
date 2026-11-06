package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.List;

/** Bounded incremental decoder for the original iAP2 CSM 0x4040 frame format. */
public final class Iap2CsmFrameDecoder {
    public static final int START = 0x4040;
    public static final int HEADER_BYTES = 6;
    public static final int MAX_FRAME_BYTES = 65535;
    /** Original Iap2LinkChannel.runPump() reads at most 8192 bytes per recv call. */
    public static final int MAX_INPUT_CHUNK_BYTES = 8192;
    private byte[] pending = new byte[0];

    public static final class Frame {
        public final int messageId;
        public final byte[] payload;
        Frame(int messageId, byte[] payload) {
            this.messageId = messageId;
            this.payload = payload;
        }
    }

    public synchronized List<Frame> feed(byte[] chunk) {
        if (chunk == null) throw new NullPointerException("chunk");
        if (chunk.length == 0) return new ArrayList<Frame>();
        if (chunk.length > MAX_INPUT_CHUNK_BYTES) {
            reset();
            throw new IllegalArgumentException("CSM input chunk exceeds original 8192-byte read bound");
        }
        if (pending.length > MAX_FRAME_BYTES) {
            reset();
            throw new IllegalStateException("CSM pending buffer is already invalid");
        }
        byte[] joined = concat(pending, chunk);
        List<Frame> frames = new ArrayList<Frame>();
        int offset = 0;
        while (offset < joined.length) {
            int available = joined.length - offset;
            if (available >= 2 && readU16(joined, offset) != START) {
                offset++;
                continue;
            }
            if (available < HEADER_BYTES) break;
            int length = readU16(joined, offset + 2);
            if (length < HEADER_BYTES) {
                offset++;
                continue;
            }
            if (available < length) break;
            int messageId = readU16(joined, offset + 4);
            byte[] payload = copy(joined, offset + HEADER_BYTES, offset + length);
            frames.add(new Frame(messageId, payload));
            offset += length;
        }
        int remaining = joined.length - offset;
        if (remaining > MAX_FRAME_BYTES) {
            reset();
            throw new IllegalStateException("CSM receive buffer exceeded maximum frame size");
        }
        pending = copy(joined, offset, joined.length);
        return frames;
    }

    public synchronized int pendingBytes() { return pending.length; }
    public synchronized void reset() { pending = new byte[0]; }

    private static int readU16(byte[] b, int p) {
        return ((b[p] & 255) << 8) | (b[p + 1] & 255);
    }
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
    private static byte[] copy(byte[] a, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(a, from, out, 0, out.length);
        return out;
    }
}
