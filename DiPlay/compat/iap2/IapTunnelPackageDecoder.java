package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded parser for plaintext AirPlay IAP tunnel packages after AEAD open.
 * Package length is unsigned big-endian; message type is at byte offset 16.
 * Unknown package types are preserved as parsed records, not treated as IAP.
 */
public final class IapTunnelPackageDecoder {
    public static final int HEADER_BYTES = 32;
    public static final int MESSAGE_TYPE_OFFSET = 16;
    public static final int MESSAGE_TYPE_COMM = 0x636f6d6d;
    public static final int DEFAULT_MAX_PACKAGE_BYTES = 4 * 1024 * 1024;
    private final int maxPackageBytes;
    private byte[] pending = new byte[0];

    public static final class Package {
        public final int messageType;
        public final byte[] body;
        Package(int messageType, byte[] body) {
            this.messageType = messageType;
            this.body = body;
        }
        public boolean isComm() { return messageType == MESSAGE_TYPE_COMM; }
    }

    public IapTunnelPackageDecoder() { this(DEFAULT_MAX_PACKAGE_BYTES); }
    public IapTunnelPackageDecoder(int maxPackageBytes) {
        if (maxPackageBytes < HEADER_BYTES) throw new IllegalArgumentException("maxPackageBytes < 32");
        this.maxPackageBytes = maxPackageBytes;
    }

    /** Incomplete trailing package is retained for the next call. Invalid size fails closed. */
    public synchronized List<Package> feed(byte[] plaintext) {
        if (plaintext == null) throw new NullPointerException("plaintext");
        byte[] joined = concat(pending, plaintext);
        List<Package> result = new ArrayList<Package>();
        int offset = 0;
        while (joined.length - offset >= HEADER_BYTES) {
            long size = readU32Be(joined, offset);
            if (size < HEADER_BYTES || size > maxPackageBytes) {
                reset();
                throw new IllegalArgumentException("invalid IAP package size: " + size);
            }
            if (joined.length - offset < size) break;
            int type = readU32Be(joined, offset + MESSAGE_TYPE_OFFSET);
            int bodyLength = (int) size - HEADER_BYTES;
            byte[] body = new byte[bodyLength];
            System.arraycopy(joined, offset + HEADER_BYTES, body, 0, bodyLength);
            result.add(new Package(type, body));
            offset += (int) size;
        }
        int remaining = joined.length - offset;
        if (remaining > maxPackageBytes) {
            reset();
            throw new IllegalArgumentException("incomplete IAP package exceeds configured maximum");
        }
        pending = copy(joined, offset, joined.length);
        return result;
    }

    public synchronized int pendingBytes() { return pending.length; }
    public synchronized void reset() { pending = new byte[0]; }

    private static long readU32Be(byte[] b, int p) {
        return ((long)(b[p] & 255) << 24) | ((long)(b[p + 1] & 255) << 16)
                | ((long)(b[p + 2] & 255) << 8) | (long)(b[p + 3] & 255);
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
