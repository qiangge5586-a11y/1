package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded structural decoder for AirPlay IAP data-stream encrypted frames.
 *
 * It only separates the 2-byte little-endian length prefix and sealed payload
 * (ciphertext plus 16-byte authentication tag). AEAD verification/decryption
 * and nonce management are deliberately left to a crypto adapter.
 */
public final class IapTunnelFrameDecoder {
    public static final int HEADER_BYTES = 2;
    public static final int TAG_BYTES = 16;
    public static final int DEFAULT_MAX_CIPHERTEXT_BYTES = 0xffff;
    private final int maxCiphertextBytes;
    private byte[] pending = new byte[0];

    public IapTunnelFrameDecoder() {
        this(DEFAULT_MAX_CIPHERTEXT_BYTES);
    }

    public IapTunnelFrameDecoder(int maxCiphertextBytes) {
        if (maxCiphertextBytes < 1 || maxCiphertextBytes > 0xffff) {
            throw new IllegalArgumentException("maxCiphertextBytes must be 1..65535");
        }
        this.maxCiphertextBytes = maxCiphertextBytes;
    }

    /** Returns complete sealed frames; each item includes the 16-byte tag, not the 2-byte prefix. */
    public synchronized List<byte[]> feed(byte[] input) {
        if (input == null) throw new NullPointerException("input");
        byte[] joined = concat(pending, input);
        List<byte[]> frames = new ArrayList<byte[]>();
        int offset = 0;
        while (joined.length - offset >= HEADER_BYTES) {
            int length = (joined[offset] & 0xff) | ((joined[offset + 1] & 0xff) << 8);
            if (length > maxCiphertextBytes) {
                reset();
                throw new IllegalArgumentException("encrypted frame length exceeds configured maximum: " + length);
            }
            long totalLong = (long) HEADER_BYTES + length + TAG_BYTES;
            if (totalLong > Integer.MAX_VALUE) {
                reset();
                throw new IllegalArgumentException("encrypted frame length overflow");
            }
            int total = (int) totalLong;
            if (joined.length - offset < total) break;
            frames.add(copy(joined, offset + HEADER_BYTES, offset + total));
            offset += total;
        }
        int remaining = joined.length - offset;
        if (remaining > HEADER_BYTES + maxCiphertextBytes + TAG_BYTES) {
            reset();
            throw new IllegalArgumentException("pending encrypted frame exceeds maximum");
        }
        pending = copy(joined, offset, joined.length);
        return frames;
    }

    public synchronized int pendingBytes() { return pending.length; }
    public synchronized void reset() { pending = new byte[0]; }

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
