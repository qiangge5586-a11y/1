package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Source-parity CSM parameter list codec: u16 length, u16 id, opaque value (big-endian). */
public final class Iap2ParameterCodec {
    public static final int HEADER_BYTES = 4;
    public static final int MAX_PARAMETER_VALUE_BYTES = 65531;
    public static final int MAX_PARAMETER_LIST_BYTES = 65529;

    public static final class Parameter {
        public final int id;
        private final byte[] value;
        public Parameter(int id, byte[] value) {
            if (id < 0 || id > 65535) throw new IllegalArgumentException("parameter id must fit u16");
            if (value == null) throw new NullPointerException("value");
            if (value.length > MAX_PARAMETER_VALUE_BYTES)
                throw new IllegalArgumentException("CSM parameter payload exceeds 65531 bytes");
            this.id = id;
            this.value = value.clone();
        }
        public byte[] value() { return value.clone(); }
    }

    private Iap2ParameterCodec() { }

    public static byte[] encode(int id, byte[] value) {
        Parameter checked = new Parameter(id, value);
        byte[] data = checked.value();
        byte[] result = new byte[HEADER_BYTES + data.length];
        writeU16(result, 0, result.length);
        writeU16(result, 2, id);
        System.arraycopy(data, 0, result, HEADER_BYTES, data.length);
        return result;
    }

    public static byte[] encodeU8(int id, int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("u8 value must be in 0..255");
        return encode(id, new byte[] { (byte) value });
    }

    public static byte[] encodeList(Parameter... parameters) {
        if (parameters == null) throw new NullPointerException("parameters");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] == null) throw new NullPointerException("parameter");
            byte[] next = encode(parameters[i].id, parameters[i].value());
            if (next.length > MAX_PARAMETER_LIST_BYTES - out.size())
                throw new IllegalArgumentException("Encoded CSM parameters exceed the 65529-byte message body limit");
            out.write(next, 0, next.length);
        }
        return out.toByteArray();
    }

    public static List<Parameter> parse(byte[] encoded) {
        if (encoded == null) throw new NullPointerException("encoded");
        List<Parameter> result = new ArrayList<Parameter>();
        int offset = 0;
        while (offset < encoded.length) {
            int remaining = encoded.length - offset;
            if (remaining < HEADER_BYTES) throw new IllegalArgumentException("Truncated CSM parameter header");
            int length = readU16(encoded, offset);
            if (length < HEADER_BYTES || length > remaining)
                throw new IllegalArgumentException("Invalid CSM parameter length " + length);
            int id = readU16(encoded, offset + 2);
            byte[] value = new byte[length - HEADER_BYTES];
            System.arraycopy(encoded, offset + HEADER_BYTES, value, 0, value.length);
            result.add(new Parameter(id, value));
            offset += length;
        }
        return Collections.unmodifiableList(result);
    }

    public static Parameter first(List<Parameter> parameters, int id) {
        if (parameters == null) throw new NullPointerException("parameters");
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            if (parameter.id == id) return parameter;
        }
        throw new IllegalArgumentException("Missing required CSM parameter " + id);
    }

    private static int readU16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }
    private static void writeU16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }
}
