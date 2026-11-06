package com.shilapi.xcertplay.compat.ncm;

import java.util.ArrayList;
import java.util.List;

/**
 * Incremental, offline NTB16 stream framing decoder.
 *
 * Input chunks may split an NTB at any byte boundary or contain several NTBs.
 * This class has no USB, Android, socket, or root dependency.
 */
public final class Ntb16StreamDecoder {
    private static final int NTH_LENGTH = 12;
    private static final int MIN_BLOCK_LENGTH = 28;
    private static final int DEFAULT_MAX_BUFFER = 1024 * 1024;

    private byte[] buffered = new byte[32768];
    private int bufferedSize;
    private final int maxBufferedBytes;
    private boolean optionalShortPacketPad;
    private int resyncCount;
    private int skippedBytes;

    public Ntb16StreamDecoder() {
        this(DEFAULT_MAX_BUFFER);
    }

    public Ntb16StreamDecoder(int maxBufferedBytes) {
        if (maxBufferedBytes < 64 || maxBufferedBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("maxBufferedBytes must be in 64..16777216");
        }
        this.maxBufferedBytes = maxBufferedBytes;
    }

    public synchronized List<byte[]> append(byte[] source, int offset, int length) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (offset < 0 || length < 0 || offset > source.length - length) {
            throw new IllegalArgumentException("input range is outside source");
        }
        if (length > maxBufferedBytes - bufferedSize) {
            throw new IllegalStateException("NTB stream buffer limit exceeded");
        }
        ensureCapacity(bufferedSize + length);
        System.arraycopy(source, offset, buffered, bufferedSize, length);
        bufferedSize += length;
        return drain();
    }

    public synchronized List<byte[]> append(byte[] source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        return append(source, 0, source.length);
    }

    public synchronized int getBufferedByteCount() {
        return bufferedSize;
    }

    public synchronized int getResyncCount() {
        return resyncCount;
    }

    public synchronized int getSkippedByteCount() {
        return skippedBytes;
    }

    public synchronized void reset() {
        bufferedSize = 0;
        optionalShortPacketPad = false;
        resyncCount = 0;
        skippedBytes = 0;
    }

    private List<byte[]> drain() {
        List<byte[]> frames = new ArrayList<byte[]>();
        while (true) {
            if (optionalShortPacketPad && bufferedSize > 0) {
                optionalShortPacketPad = false;
                int first = buffered[0] & 0xFF;
                if (first == 0) {
                    discard(1);
                } else if (first != 0x4E) {
                    if (!resynchronize()) {
                        return frames;
                    }
                }
            }

            if (bufferedSize < NTH_LENGTH) {
                return frames;
            }
            if (readU32(0) != Ntb16Codec.NTH16_SIGNATURE) {
                if (!resynchronize()) {
                    return frames;
                }
                continue;
            }

            int headerLength = readU16(4);
            int blockLength = readU16(8);
            int ndpOffset = readU16(10);
            if (headerLength != NTH_LENGTH || blockLength < MIN_BLOCK_LENGTH
                    || ndpOffset < NTH_LENGTH || (ndpOffset & 3) != 0
                    || ndpOffset > blockLength - 8) {
                if (!resynchronize()) {
                    return frames;
                }
                continue;
            }
            if (bufferedSize < blockLength) {
                return frames;
            }

            frames.addAll(Ntb16Codec.parse(buffered, 0, blockLength));
            discard(blockLength);
            optionalShortPacketPad = (blockLength % 512) == 0;
        }
    }

    private boolean resynchronize() {
        for (int candidate = 1; candidate <= bufferedSize - NTH_LENGTH; candidate++) {
            if (plausibleHeaderAt(candidate)) {
                discard(candidate);
                resyncCount++;
                skippedBytes += candidate;
                return true;
            }
        }
        int discard = Math.max(bufferedSize - (NTH_LENGTH - 1), 0);
        if (discard > 0) {
            discard(discard);
            resyncCount++;
            skippedBytes += discard;
        }
        return false;
    }

    private boolean plausibleHeaderAt(int offset) {
        if (readU32(offset) != Ntb16Codec.NTH16_SIGNATURE) {
            return false;
        }
        int headerLength = readU16(offset + 4);
        int blockLength = readU16(offset + 8);
        int ndpOffset = readU16(offset + 10);
        return headerLength == NTH_LENGTH && blockLength >= MIN_BLOCK_LENGTH
                && ndpOffset >= NTH_LENGTH && (ndpOffset & 3) == 0
                && ndpOffset <= blockLength - 8;
    }

    private void ensureCapacity(int required) {
        if (required <= buffered.length) {
            return;
        }
        int capacity = buffered.length;
        while (capacity < required) {
            capacity = Math.min(maxBufferedBytes, capacity * 2);
            if (capacity < required && capacity == maxBufferedBytes) {
                throw new IllegalStateException("NTB stream buffer limit exceeded");
            }
        }
        byte[] grown = new byte[capacity];
        System.arraycopy(buffered, 0, grown, 0, bufferedSize);
        buffered = grown;
    }

    private void discard(int count) {
        if (count <= 0) {
            return;
        }
        System.arraycopy(buffered, count, buffered, 0, bufferedSize - count);
        bufferedSize -= count;
    }

    private int readU16(int offset) {
        return (buffered[offset] & 0xFF) | ((buffered[offset + 1] & 0xFF) << 8);
    }

    private int readU32(int offset) {
        return (buffered[offset] & 0xFF)
                | ((buffered[offset + 1] & 0xFF) << 8)
                | ((buffered[offset + 2] & 0xFF) << 16)
                | ((buffered[offset + 3] & 0xFF) << 24);
    }
}
