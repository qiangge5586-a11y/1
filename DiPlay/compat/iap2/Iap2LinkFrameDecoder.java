package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded incremental iAP2 link-frame decoder matching the original
 * Iap2LinkEngine parseAvailable()/ReceiveBuffer behavior.
 * It consumes bytes after the six-byte startup marker has been handled.
 */
public final class Iap2LinkFrameDecoder {
    public static final int MAX_INPUT_CHUNK_BYTES = 8192;
    private final byte[] pending = new byte[Iap2LinkFrameCodec.MAX_WIRE_FRAME_BYTES];
    private int pendingSize;
    private long discardedBytes;
    private long invalidHeaders;
    private long invalidPayloadChecksums;

    public static final class Frame {
        public final int control;
        public final int sequence;
        public final int acknowledgement;
        public final int sessionId;
        /** Null for the original 9-byte header-only control frame. */
        public final byte[] payload;

        Frame(Iap2LinkFrameCodec.Header h, byte[] payload) {
            this.control = h.control;
            this.sequence = h.sequence;
            this.acknowledgement = h.acknowledgement;
            this.sessionId = h.sessionId;
            this.payload = payload;
        }
    }

    public synchronized List<Frame> feed(byte[] chunk) {
        if (chunk == null) throw new NullPointerException("chunk");
        if (chunk.length > MAX_INPUT_CHUNK_BYTES) {
            reset();
            throw new IllegalArgumentException("link input chunk exceeds original 8192-byte read bound");
        }
        List<Frame> frames = new ArrayList<Frame>();
        int inputOffset = 0;
        do {
            drainPending(frames);
            if (inputOffset >= chunk.length) break;

            int capacity = pending.length - pendingSize;
            if (capacity == 0) {
                // A full receive buffer must always contain a complete maximum-size
                // frame or a malformed candidate that drainPending() can consume.
                reset();
                throw new IllegalStateException("iAP2 link receive buffer made no parsing progress");
            }
            int count = Math.min(capacity, chunk.length - inputOffset);
            System.arraycopy(chunk, inputOffset, pending, pendingSize, count);
            pendingSize += count;
            inputOffset += count;
        } while (inputOffset < chunk.length);

        drainPending(frames);
        return frames;
    }

    /** Parses complete frames in-place and compacts the bounded receive buffer. */
    private void drainPending(List<Frame> frames) {
        int offset = 0;
        while (offset < pendingSize) {
            int available = pendingSize - offset;
            while (available >= 2
                    && ((pending[offset] & 0xff) != 0xff
                    || (pending[offset + 1] & 0xff) != 0x5a)) {
                offset++;
                discardedBytes++;
                available--;
            }
            if (available < Iap2LinkFrameCodec.HEADER_BYTES) break;

            byte[] rawHeader = copy(pending, offset, offset + Iap2LinkFrameCodec.HEADER_BYTES);
            Iap2LinkFrameCodec.Header header = Iap2LinkFrameCodec.parseHeader(rawHeader);
            if (header == null || header.length < Iap2LinkFrameCodec.HEADER_BYTES) {
                // Original parser consumes the complete 9-byte candidate header on failure.
                offset += Iap2LinkFrameCodec.HEADER_BYTES;
                invalidHeaders++;
                continue;
            }
            if (header.length == Iap2LinkFrameCodec.HEADER_BYTES) {
                frames.add(new Frame(header, null));
                offset += Iap2LinkFrameCodec.HEADER_BYTES;
                continue;
            }

            if (available < header.length) break;
            byte[] payloadAndChecksum = copy(pending,
                    offset + Iap2LinkFrameCodec.HEADER_BYTES, offset + header.length);
            if (!Iap2LinkFrameCodec.payloadChecksumValid(payloadAndChecksum)) {
                invalidPayloadChecksums++;
            } else {
                byte[] payload = copy(payloadAndChecksum, 0, payloadAndChecksum.length - 1);
                frames.add(new Frame(header, payload));
            }
            offset += header.length;
        }

        if (offset > 0) {
            int remaining = pendingSize - offset;
            if (remaining > 0) System.arraycopy(pending, offset, pending, 0, remaining);
            pendingSize = remaining;
        }
    }

    public synchronized int pendingBytes() { return pendingSize; }
    public synchronized long getDiscardedBytes() { return discardedBytes; }
    public synchronized long getInvalidHeaders() { return invalidHeaders; }
    public synchronized long getInvalidPayloadChecksums() { return invalidPayloadChecksums; }

    public synchronized void reset() {
        pendingSize = 0;
        discardedBytes = 0;
        invalidHeaders = 0;
        invalidPayloadChecksums = 0;
    }

    private static byte[] copy(byte[] source, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(source, from, out, 0, out.length);
        return out;
    }
}
