package com.shilapi.xcertplay.compat.bluetooth;

/**
 * Fixed-capacity byte ring for transport adapters.
 *
 * This class is deliberately not synchronized: callers own the lock that protects
 * it. Writes are all-or-nothing so a producer can apply backpressure without
 * silently dropping a partial packet.
 */
public final class BoundedByteRingBuffer {
    private final byte[] bytes;
    private int head;
    private int size;

    public BoundedByteRingBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity <= 0");
        bytes = new byte[capacity];
    }

    public int capacity() { return bytes.length; }
    public int size() { return size; }
    public int remainingCapacity() { return bytes.length - size; }

    public boolean offer(byte[] source, int offset, int length) {
        checkRange(source, offset, length);
        if (length > remainingCapacity()) return false;
        int tail = (head + size) % bytes.length;
        int first = Math.min(length, bytes.length - tail);
        System.arraycopy(source, offset, bytes, tail, first);
        if (first < length) System.arraycopy(source, offset + first, bytes, 0, length - first);
        size += length;
        return true;
    }

    public int read(byte[] target, int offset, int length) {
        checkRange(target, offset, length);
        if (length == 0 || size == 0) return 0;
        int count = Math.min(length, size);
        int first = Math.min(count, bytes.length - head);
        System.arraycopy(bytes, head, target, offset, first);
        if (first < count) System.arraycopy(bytes, 0, target, offset + first, count - first);
        head = (head + count) % bytes.length;
        size -= count;
        return count;
    }

    public void clear() {
        head = 0;
        size = 0;
    }

    private static void checkRange(byte[] buffer, int offset, int length) {
        if (buffer == null) throw new NullPointerException("buffer");
        if (offset < 0 || length < 0 || offset > buffer.length - length) {
            throw new IndexOutOfBoundsException("invalid buffer range");
        }
    }
}
