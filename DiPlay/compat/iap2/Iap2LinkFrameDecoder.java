package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded incremental iAP2 link-frame decoder matching the original
 * Iap2LinkEngine.parseAvailable()/ReceiveBuffer behavior.
 * It consumes bytes after the six-byte startup marker has been handled.
 */
public final class Iap2LinkFrameDecoder {
    public static final int MAX_INPUT_CHUNK_BYTES = 8192;
    private byte[] pending = new byte[0];
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
        if (chunk.length == 0) return new ArrayList<Frame>();
        byte[] joined = concat(pending, chunk);
        List<Frame> frames = new ArrayList<Frame>();
        int offset = 0;
        while (offset < joined.length) {
            int available = joined.length - offset;
            while (available >= 2
                    && ((joined[offset] & 0xff) != 0xff || (joined[offset + 1] & 0xff) != 0x5a)) {
                offset++;
                discardedBytes++;
                available--;
            }
            if (available < Iap2LinkFrameCodec.HEADER_BYTES) break;

            byte[] rawHeader = copy(joined, offset, offset + Iap2LinkFrameCodec.HEADER_BYTES);
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

            int payloadAndChecksumLength = header.length - Iap2LinkFrameCodec.HEADER_BYTES;
            if (available < header.length) break;
            byte[] payloadAndChecksum = copy(joined,
                    offset + Iap2LinkFrameCodec.HEADER_BYTES, offset + header.length);
            if (!Iap2LinkFrameCodec.payloadChecksumValid(payloadAndChecksum)) {
                invalidPayloadChecksums++;
            } else {
                byte[] payload = copy(payloadAndChecksum, 0, payloadAndChecksum.length - 1);
                frames.add(new Frame(header, payload));
            }
            offset += Iap2LinkFrameCodec.HEADER_BYTES + payloadAndChecksumLength;
        }

        int remaining = joined.length - offset;
        if (remaining > Iap2LinkFrameCodec.MAX_WIRE_FRAME_BYTES) {
            reset();
            throw new IllegalStateException("iAP2 link receive buffer exceeded 65535 bytes");
        }
        pending = copy(joined, offset, joined.length);
        return frames;
    }

    public synchronized int pendingBytes() { return pending.length; }
    public synchronized long getDiscardedBytes() { return discardedBytes; }
    public synchronized long getInvalidHeaders() { return invalidHeaders; }
    public synchronized long getInvalidPayloadChecksums() { return invalidPayloadChecksums; }

    public synchronized void reset() {
        pending = new byte[0];
        discardedBytes = 0;
        invalidHeaders = 0;
        invalidPayloadChecksums = 0;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] copy(byte[] source, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(source, from, out, 0, out.length);
        return out;
    }
}
