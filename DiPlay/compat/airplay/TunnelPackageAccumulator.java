package com.shilapi.xcertplay.airplay;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Incremental parser for decrypted AirPlay type-130 iAP packages.
 *
 * The original parser copied the incomplete package tail after every TCP
 * read. This accumulator retains that tail in a reusable buffer, parses
 * complete packages in place, and allocates only the owned iAP body passed
 * to the original listener.
 *
 * Java 7 / Android API 17 compatible.
 */
public final class TunnelPackageAccumulator {
    private static final int HEADER_BYTES = 32;
    private static final int MESSAGE_TYPE_OFFSET = 16;
    private static final int MESSAGE_TYPE_COMM = 0x636f6d6d;
    private static final int MAX_PACKAGE_BYTES = 4 * 1024 * 1024;
    private static final int READ_CHUNK_BYTES = 16 * 1024;
    private static final int MAX_PENDING_BYTES = MAX_PACKAGE_BYTES + READ_CHUNK_BYTES;

    private final Object listener;
    private final Method onDebug;
    private final Method onIap;
    private byte[] buffer = new byte[READ_CHUNK_BYTES];
    private int start;
    private int end;

    public TunnelPackageAccumulator(Object listener) {
        if (listener == null) throw new NullPointerException("listener");
        this.listener = listener;
        try {
            // IapTunnel.Listener is supplied by the original APK's classes3 DEX
            // and is intentionally absent from the standalone overlay classpath.
            // Resolve its two public callbacks once, not per TCP read.
            this.onDebug = listener.getClass().getMethod("onDebug", String.class);
            this.onIap = listener.getClass().getMethod("onIap", byte[].class);
        } catch (NoSuchMethodException error) {
            throw new IllegalArgumentException("listener does not expose IapTunnel callbacks", error);
        }
    }

    /** Append decrypted bytes and synchronously dispatch each complete package. */
    public void append(byte[] bytes) {
        if (bytes == null) throw new NullPointerException("bytes");
        if (bytes.length == 0) return;
        ensureCapacity(bytes.length);
        System.arraycopy(bytes, 0, buffer, end, bytes.length);
        end += bytes.length;
        parseAvailable();
    }

    private void parseAvailable() {
        while (end - start >= HEADER_BYTES) {
            int size = readInt32Be(buffer, start);
            // Preserve the original parser's signed-int length checks.
            if (size < HEADER_BYTES || size > MAX_PACKAGE_BYTES) break;
            if (end - start < size) break;
            int messageType = readInt32Be(buffer, start + MESSAGE_TYPE_OFFSET);
            if (messageType == MESSAGE_TYPE_COMM) {
                int bodyLength = size - HEADER_BYTES;
                invoke(onDebug, "AirPlay iAP tunnel package type=comm body=" + bodyLength);
                byte[] body = Arrays.copyOfRange(buffer, start + HEADER_BYTES, start + size);
                invoke(onIap, body);
            }
            start += size;
        }
        if (start == end) {
            start = 0;
            end = 0;
        } else if (start > 0 && (start >= buffer.length / 2 || end == buffer.length)) {
            compact();
        }
    }

    private void ensureCapacity(int incoming) {
        int pending = end - start;
        long requiredLong = (long) pending + (long) incoming;
        if (requiredLong > MAX_PENDING_BYTES) {
            throw new IllegalStateException("AirPlay iAP tunnel pending package exceeds bounded buffer");
        }
        int required = (int) requiredLong;
        if (buffer.length - end >= incoming) return;
        if (start > 0) {
            compact();
            if (buffer.length - end >= incoming) return;
        }
        int capacity = buffer.length;
        while (capacity < required) {
            int next = capacity << 1;
            if (next <= capacity || next > MAX_PENDING_BYTES) {
                capacity = MAX_PENDING_BYTES;
                break;
            }
            capacity = next;
        }
        if (capacity < required) {
            throw new IllegalStateException("AirPlay iAP tunnel pending package exceeds bounded buffer");
        }
        buffer = Arrays.copyOf(buffer, capacity);
    }

    private void compact() {
        int pending = end - start;
        if (pending > 0) System.arraycopy(buffer, start, buffer, 0, pending);
        start = 0;
        end = pending;
    }

    private void invoke(Method method, Object argument) {
        try {
            method.invoke(listener, argument);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("IapTunnel listener callback failed", cause);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("IapTunnel listener callback is inaccessible", error);
        }
    }

    private static int readInt32Be(byte[] source, int offset) {
        return ((source[offset] & 0xff) << 24)
                | ((source[offset + 1] & 0xff) << 16)
                | ((source[offset + 2] & 0xff) << 8)
                | (source[offset + 3] & 0xff);
    }
}
