package com.shilapi.xcertplay.compat.iap2;

/** Source-audited encoder for the six-byte iAP2 CSM 0x4040 frame header. */
public final class Iap2CsmFrameCodec {
    public static final int START = 0x4040;
    public static final int HEADER_BYTES = 6;
    public static final int MAX_FRAME_BYTES = 65535;
    public static final int MAX_PAYLOAD_BYTES = MAX_FRAME_BYTES - HEADER_BYTES;

    private Iap2CsmFrameCodec() { }

    public static byte[] encode(int messageId, byte[] payload) {
        if (messageId < 0 || messageId > 65535) {
            throw new IllegalArgumentException("messageId must fit in u16");
        }
        if (payload == null) throw new NullPointerException("payload");
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("CSM payload exceeds 65529 bytes");
        }
        int length = HEADER_BYTES + payload.length;
        byte[] frame = new byte[length];
        writeU16(frame, 0, START);
        writeU16(frame, 2, length);
        writeU16(frame, 4, messageId);
        System.arraycopy(payload, 0, frame, HEADER_BYTES, payload.length);
        return frame;
    }

    private static void writeU16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }
}
