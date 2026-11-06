package com.shilapi.xcertplay.compat.iap2;

/**
 * Source-audited iAP2 link packet primitives from DiPlay 0.3.11
 * Iap2LinkEngine / Iap2LinkEngine$Companion Smali.
 *
 * This is an offline codec only. It performs no transport I/O.
 */
public final class Iap2LinkFrameCodec {
    public static final int HEADER_BYTES = 9;
    public static final int CHECKSUM_BYTES = 1;
    public static final int MAX_WIRE_FRAME_BYTES = 65535;
    public static final int MAX_PAYLOAD_BYTES = 65525;
    public static final int CONTROL_SYNCHRONIZE = 0x80;
    public static final int CONTROL_ACKNOWLEDGEMENT = 0x40;
    public static final int CONTROL_EXTENDED_ACKNOWLEDGEMENT = 0x20;
    public static final int CONTROL_RESET = 0x10;
    private static final int START_HIGH = 0xFF;
    private static final int START_LOW = 0x5A;

    private Iap2LinkFrameCodec() { }

    public static final class Header {
        public final int length;
        public final int control;
        public final int sequence;
        public final int acknowledgement;
        public final int sessionId;

        private Header(int length, int control, int sequence, int acknowledgement, int sessionId) {
            this.length = length;
            this.control = control;
            this.sequence = sequence;
            this.acknowledgement = acknowledgement;
            this.sessionId = sessionId;
        }
    }

    /** Encodes one link frame; null payload produces the original 9-byte control frame. */
    public static byte[] encode(int control, int sequence, int acknowledgement,
                                int sessionId, byte[] payload) {
        int payloadLength = payload == null ? 0 : payload.length;
        int length = payload == null ? HEADER_BYTES : payloadLength + HEADER_BYTES + CHECKSUM_BYTES;
        if (length > MAX_WIRE_FRAME_BYTES) throw new IllegalArgumentException("frame too large");
        if ((control & ~0xFF) != 0 || (sequence & ~0xFF) != 0
                || (acknowledgement & ~0xFF) != 0 || (sessionId & ~0xFF) != 0) {
            throw new IllegalArgumentException("header field outside u8");
        }
        byte[] out = new byte[length];
        out[0] = (byte) START_HIGH;
        out[1] = (byte) START_LOW;
        out[2] = (byte) (length >>> 8);
        out[3] = (byte) length;
        out[4] = (byte) control;
        out[5] = (byte) sequence;
        out[6] = (byte) acknowledgement;
        out[7] = (byte) sessionId;
        out[8] = (byte) checksum(out, 8);
        if (payload != null) {
            System.arraycopy(payload, 0, out, HEADER_BYTES, payloadLength);
            out[length - 1] = (byte) checksum(payload, payload.length);
        }
        return out;
    }

    /** Mirrors original parseHeader(): exactly 9 bytes, marker and header checksum required. */
    public static Header parseHeader(byte[] bytes) {
        if (bytes == null || bytes.length != HEADER_BYTES || !checksumValid(bytes)) return null;
        if (u8(bytes[0]) != START_HIGH || u8(bytes[1]) != START_LOW) return null;
        int length = readU16(bytes, 2);
        if (length < HEADER_BYTES || length > MAX_WIRE_FRAME_BYTES) return null;
        return new Header(length, u8(bytes[4]), u8(bytes[5]), u8(bytes[6]), u8(bytes[7]));
    }

    /** Payload checksum is a trailing additive two's-complement byte; sum including it is 0 mod 256. */
    public static boolean payloadChecksumValid(byte[] payloadAndChecksum) {
        return payloadAndChecksum != null && payloadAndChecksum.length > 0
                && checksumValid(payloadAndChecksum);
    }

    public static int nextSequence(int sequence) {
        return (sequence + 1) & 0xFF;
    }

    /** Circular 8-bit distance: (sequence - previous) modulo 256. */
    public static int sequenceDistance(int sequence, Integer previous) {
        return previous == null ? 0 : (sequence - previous.intValue()) & 0xFF;
    }

    public static int checksum(byte[] bytes, int count) {
        if (bytes == null) throw new NullPointerException("bytes");
        if (count < 0 || count > bytes.length) throw new IllegalArgumentException("count");
        int sum = 0;
        for (int i = 0; i < count; i++) sum += u8(bytes[i]);
        return (-sum) & 0xFF;
    }

    private static boolean checksumValid(byte[] bytes) {
        int sum = 0;
        for (int i = 0; i < bytes.length; i++) sum = (sum + u8(bytes[i])) & 0xFF;
        return sum == 0;
    }

    private static int readU16(byte[] bytes, int offset) {
        return (u8(bytes[offset]) << 8) | u8(bytes[offset + 1]);
    }

    private static int u8(byte value) {
        return value & 0xFF;
    }
}
