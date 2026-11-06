package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.List;

/** Bounded incremental decoder for the original iAP2 CSM 0x4040 frame format. */
public final class Iap2CsmFrameDecoder {
    public static final int START = 0x4040;
    public static final int HEADER_BYTES = 6;
    public static final int MAX_FRAME_BYTES = 65535;
    private byte[] pending = new byte[0];
    private long discardedBytes;
    private long invalidLengthCandidates;

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
        List<Frame> frames = new ArrayList<Frame>();
        if (chunk.length == 0) return frames;
        if (pending.length > MAX_FRAME_BYTES) {
            reset();
            throw new IllegalStateException("CSM pending buffer is already invalid");
        }

        // Mirror original Iap2CsmFramer.offer(): drain, append only available
        // capacity in the fixed 65,535-byte receive buffer, then drain again.
        int inputOffset = 0;
        while (inputOffset < chunk.length) {
            drainPending(frames);
            int capacity = MAX_FRAME_BYTES - pending.length;
            if (capacity <= 0) {
                reset();
                throw new IllegalStateException("CSM receive buffer could not make progress");
            }
            int count = Math.min(chunk.length - inputOffset, capacity);
            byte[] joined = new byte[pending.length + count];
            System.arraycopy(pending, 0, joined, 0, pending.length);
            System.arraycopy(chunk, inputOffset, joined, pending.length, count);
            pending = joined;
            inputOffset += count;
        }
        drainPending(frames);
        return frames;
    }

    private void drainPending(List<Frame> frames) {
        int offset = 0;
        while (offset < pending.length) {
            int available = pending.length - offset;
            if (available >= 2 && readU16(pending, offset) != START) {
                offset++;
                discardedBytes++;
                continue;
            }
            if (available < HEADER_BYTES) break;
            int length = readU16(pending, offset + 2);
            if (length < HEADER_BYTES) {
                offset++;
                discardedBytes++;
                invalidLengthCandidates++;
                continue;
            }
            if (available < length) break;
            int messageId = readU16(pending, offset + 4);
            byte[] payload = copy(pending, offset + HEADER_BYTES, offset + length);
            frames.add(new Frame(messageId, payload));
            offset += length;
        }
        if (offset > 0) pending = copy(pending, offset, pending.length);
        if (pending.length > MAX_FRAME_BYTES) {
            reset();
            throw new IllegalStateException("CSM receive buffer exceeded maximum frame size");
        }
    }

    public synchronized int pendingBytes() { return pending.length; }
    /** Number of bytes skipped while searching for a valid CSM frame boundary. */
    public synchronized long getDiscardedBytes() { return discardedBytes; }
    /** Number of 0x4040 candidates rejected because total length was below six bytes. */
    public synchronized long getInvalidLengthCandidates() { return invalidLengthCandidates; }
    /** Reset stream state and diagnostics when the underlying session generation changes. */
    public synchronized void reset() {
        pending = new byte[0];
        discardedBytes = 0;
        invalidLengthCandidates = 0;
    }

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
