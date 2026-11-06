package com.shilapi.xcertplay.compat.ncm;

import java.util.ArrayList;
import java.util.List;

/**
 * Small Java 7 NTB16 codec ported from the DiPlay 0.3.11 transport format.
 *
 * This class only transforms byte arrays. It performs no USB I/O and is safe
 * to exercise with offline fixtures on desktop JVMs and Android API 17.
 */
public final class Ntb16Codec {
    public static final int NTH16_SIGNATURE = 0x484D434E;
    public static final int NDP16_SIGNATURE = 0x304D434E;
    private static final int NDP16_SIGNATURE_MASK = 0x00FFFFFF;
    private static final int NDP16_SIGNATURE_BASE = 0x004D434E;
    private static final int NTH_LENGTH = 12;
    private static final int NDP_LENGTH = 16;
    private static final int DATAGRAM_OFFSET = 28;
    private static final int MAX_DATAGRAM_BYTES = 65507;
    private static final int USB_PACKET_SIZE = 512;

    private Ntb16Codec() {
    }

    public static byte[] build(byte[] frame, int sequence) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (frame.length == 0 || frame.length > MAX_DATAGRAM_BYTES) {
            throw new IllegalArgumentException("frame length must be in 1..65507");
        }
        if (sequence < 0 || sequence > 65535) {
            throw new IllegalArgumentException("sequence must fit in u16");
        }

        int blockLength = frame.length + DATAGRAM_OFFSET;
        byte[] block = new byte[blockLength];
        putU32(block, 0, NTH16_SIGNATURE);
        putU16(block, 4, NTH_LENGTH);
        putU16(block, 6, sequence);
        putU16(block, 8, blockLength);
        putU16(block, 10, NTH_LENGTH);

        putU32(block, 12, NDP16_SIGNATURE);
        putU16(block, 16, NDP_LENGTH);
        putU16(block, 18, 0);
        putU16(block, 20, DATAGRAM_OFFSET);
        putU16(block, 22, frame.length);
        putU16(block, 24, 0);
        putU16(block, 26, 0);
        System.arraycopy(frame, 0, block, DATAGRAM_OFFSET, frame.length);

        // Preserve the original DiPlay behavior: append a ZLP marker when
        // the NTB itself ends exactly on a USB max-packet boundary.
        if ((block.length % USB_PACKET_SIZE) == 0) {
            byte[] padded = new byte[block.length + 1];
            System.arraycopy(block, 0, padded, 0, block.length);
            return padded;
        }
        return block;
    }

    public static List<byte[]> parse(byte[] source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        return parse(source, 0, source.length);
    }

    public static List<byte[]> parse(byte[] source, int offset, int length) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (offset < 0 || length < 0 || offset > source.length - length) {
            throw new IllegalArgumentException("NTB16 range is outside the source buffer");
        }

        List<byte[]> datagrams = new ArrayList<byte[]>();
        if (length < NTH_LENGTH || readU32(source, offset) != NTH16_SIGNATURE) {
            return datagrams;
        }

        int headerLength = readU16(source, offset + 4);
        int blockLength = readU16(source, offset + 8);
        int ndpOffset = readU16(source, offset + 10);
        if (headerLength != NTH_LENGTH || blockLength < NTH_LENGTH
                || blockLength > length || ndpOffset < NTH_LENGTH
                || ndpOffset >= blockLength) {
            return datagrams;
        }

        boolean[] visited = new boolean[blockLength];
        int hops = 0;
        int maxHops = Math.max(1, blockLength / 4);
        while (ndpOffset != 0 && hops < maxHops) {
            if (ndpOffset < NTH_LENGTH || ndpOffset > blockLength - 8
                    || visited[ndpOffset]) {
                break;
            }
            visited[ndpOffset] = true;

            int absoluteNdp = offset + ndpOffset;
            int signature = readU32(source, absoluteNdp);
            if ((signature & NDP16_SIGNATURE_MASK) != NDP16_SIGNATURE_BASE) {
                break;
            }
            int ndpLength = readU16(source, absoluteNdp + 4);
            int nextNdp = readU16(source, absoluteNdp + 6);
            if (ndpLength < 12 || ndpLength > blockLength - ndpOffset) {
                break;
            }

            int entry = absoluteNdp + 8;
            int ndpEnd = absoluteNdp + ndpLength;
            while (entry <= ndpEnd - 4) {
                int datagramIndex = readU16(source, entry);
                int datagramLength = readU16(source, entry + 2);
                if (datagramIndex == 0 || datagramLength == 0) {
                    break;
                }
                long datagramEnd = (long) datagramIndex + (long) datagramLength;
                if (datagramIndex >= NTH_LENGTH && datagramEnd <= blockLength) {
                    int start = offset + datagramIndex;
                    byte[] frame = new byte[datagramLength];
                    System.arraycopy(source, start, frame, 0, datagramLength);
                    datagrams.add(frame);
                }
                entry += 4;
            }

            ndpOffset = nextNdp;
            hops++;
        }
        return datagrams;
    }

    private static void putU16(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }

    private static void putU32(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static int readU16(byte[] source, int offset) {
        return (source[offset] & 0xFF) | ((source[offset + 1] & 0xFF) << 8);
    }

    private static int readU32(byte[] source, int offset) {
        return (source[offset] & 0xFF)
                | ((source[offset + 1] & 0xFF) << 8)
                | ((source[offset + 2] & 0xFF) << 16)
                | ((source[offset + 3] & 0xFF) << 24);
    }
}
