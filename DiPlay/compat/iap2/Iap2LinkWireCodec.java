package com.shilapi.xcertplay.compat.iap2;

/**
 * Offline iAP2 link-wire codec reconstructed from DiPlay 0.3.11
 * Iap2LinkEngine Smali/JADX evidence. This class performs no I/O.
 */
public final class Iap2LinkWireCodec {
    public static final int HEADER_BYTES = 9;
    public static final int MAX_WIRE_FRAME_BYTES = 65535;
    public static final int MAX_PAYLOAD_BYTES = 65525;
    public static final int CONTROL_SYNCHRONIZE = 0x80;
    public static final int CONTROL_ACKNOWLEDGEMENT = 0x40;
    public static final int CONTROL_EXTENDED_ACKNOWLEDGEMENT = 0x20;
    public static final int CONTROL_RESET = 0x10;
    public static final int CONTROL_SESSION_ID = 10;
    public static final int EA_SESSION_ID = 11;
    public static final int FILE_TRANSFER_SESSION_ID = 12;
    public static final int INITIAL_SEQUENCE = 99;
    public static final byte[] MARKER = new byte[] {
        (byte) 0xff, 0x55, 0x02, 0x00, (byte) 0xee, 0x10
    };

    private Iap2LinkWireCodec() { }

    public static final class Header {
        public final int length;
        public final int control;
        public final int sequence;
        public final int acknowledgement;
        public final int sessionId;

        private Header(int length, int control, int sequence,
                int acknowledgement, int sessionId) {
            this.length = length;
            this.control = control;
            this.sequence = sequence;
            this.acknowledgement = acknowledgement;
            this.sessionId = sessionId;
        }
    }

    public static int checksum(byte[] bytes, int count) {
        if (bytes == null || count < 0 || count > bytes.length) {
            throw new IllegalArgumentException("invalid checksum range");
        }
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum = (sum + (bytes[i] & 0xff)) & 0xff;
        }
        return (-sum) & 0xff;
    }

    public static boolean checksumValid(byte[] bytes) {
        if (bytes == null) return false;
        int sum = 0;
        for (int i = 0; i < bytes.length; i++) {
            sum = (sum + (bytes[i] & 0xff)) & 0xff;
        }
        return sum == 0;
    }

    public static byte[] encodeFrame(byte[] payload, int sequence,
            int control, int acknowledgement, int sessionId) {
        if ((sequence & ~0xff) != 0 || (control & ~0xff) != 0
                || (acknowledgement & ~0xff) != 0
                || (sessionId & ~0xff) != 0) {
            throw new IllegalArgumentException("link header fields must fit one byte");
        }
        if (payload != null && payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("iAP2 payload exceeds 65525 bytes");
        }
        int length = payload == null ? HEADER_BYTES : payload.length + 10;
        byte[] out = new byte[length];
        out[0] = (byte) 0xff;
        out[1] = 0x5a;
        out[2] = (byte) (length >>> 8);
        out[3] = (byte) length;
        out[4] = (byte) control;
        out[5] = (byte) sequence;
        out[6] = (byte) acknowledgement;
        out[7] = (byte) sessionId;
        out[8] = (byte) checksum(out, 8);
        if (payload != null) {
            System.arraycopy(payload, 0, out, HEADER_BYTES, payload.length);
            out[out.length - 1] = (byte) checksum(payload, payload.length);
        }
        return out;
    }

    public static Header parseHeader(byte[] bytes) {
        if (bytes == null || bytes.length != HEADER_BYTES
                || !checksumValid(bytes)
                || (bytes[0] & 0xff) != 0xff
                || (bytes[1] & 0xff) != 0x5a) {
            return null;
        }
        int length = ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        if (length < HEADER_BYTES || length > MAX_WIRE_FRAME_BYTES) return null;
        return new Header(length, bytes[4] & 0xff, bytes[5] & 0xff,
                bytes[6] & 0xff, bytes[7] & 0xff);
    }

    public static byte[] encodeSynchronizationPayload(int maxOutgoing,
            int maxLength, int retransmissionTimeoutMillis,
            int acknowledgementTimeoutMillis, int maxRetransmissions,
            int maxAcknowledgements, int[][] sessions) {
        requireByte(maxOutgoing);
        requireU16(maxLength);
        requireU16(retransmissionTimeoutMillis);
        requireU16(acknowledgementTimeoutMillis);
        requireByte(maxRetransmissions);
        requireByte(maxAcknowledgements);
        if (sessions == null) throw new IllegalArgumentException("sessions is null");
        byte[] out = new byte[10 + sessions.length * 3];
        out[0] = 1;
        out[1] = (byte) maxOutgoing;
        putU16(out, 2, maxLength);
        putU16(out, 4, retransmissionTimeoutMillis);
        putU16(out, 6, acknowledgementTimeoutMillis);
        out[8] = (byte) maxRetransmissions;
        out[9] = (byte) maxAcknowledgements;
        for (int i = 0; i < sessions.length; i++) {
            if (sessions[i] == null || sessions[i].length != 3) {
                throw new IllegalArgumentException("each session descriptor has 3 fields");
            }
            for (int j = 0; j < 3; j++) {
                requireByte(sessions[i][j]);
                out[10 + i * 3 + j] = (byte) sessions[i][j];
            }
        }
        return out;
    }

    public static int[] decodeSynchronizationPayload(byte[] bytes) {
        if (bytes == null || bytes.length < 10 || (bytes[0] & 0xff) != 1) {
            return null;
        }
        int[] fields = new int[6 + ((bytes.length - 10) / 3) * 3];
        fields[0] = bytes[1] & 0xff;
        fields[1] = readU16(bytes, 2);
        fields[2] = readU16(bytes, 4);
        fields[3] = readU16(bytes, 6);
        fields[4] = bytes[8] & 0xff;
        fields[5] = bytes[9] & 0xff;
        int index = 6;
        for (int offset = 10; offset + 3 <= bytes.length; offset += 3) {
            fields[index++] = bytes[offset] & 0xff;
            fields[index++] = bytes[offset + 1] & 0xff;
            fields[index++] = bytes[offset + 2] & 0xff;
        }
        return fields;
    }

    public static int sequenceDistance(int sequence, int previous) {
        requireByte(sequence);
        requireByte(previous);
        return (sequence - previous) & 0xff;
    }

    public static int nextSequence(int sequence) {
        requireByte(sequence);
        return (sequence + 1) & 0xff;
    }

    private static int readU16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static void putU16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void requireByte(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("value must fit one byte");
    }

    private static void requireU16(int value) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException("value must fit two bytes");
    }
}
