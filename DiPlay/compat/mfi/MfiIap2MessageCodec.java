package com.shilapi.xcertplay.compat.mfi;

import com.shilapi.xcertplay.compat.iap2.Iap2CsmFrameCodec;
import com.shilapi.xcertplay.compat.iap2.Iap2ParameterCodec;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Source-audited iAP2 MFi CSM message builders/parsers.
 * This encodes CSM frames and parameters only; it does not drive a live link.
 */
public final class MfiIap2MessageCodec {
    public static final int REQUEST_CERTIFICATE = MfiIap2ExchangeCoordinator.REQUEST_CERTIFICATE;
    public static final int CERTIFICATE_RESPONSE = MfiIap2ExchangeCoordinator.CERTIFICATE_RESPONSE;
    public static final int REQUEST_CHALLENGE = MfiIap2ExchangeCoordinator.REQUEST_CHALLENGE;
    public static final int SIGNATURE_RESPONSE = MfiIap2ExchangeCoordinator.SIGNATURE_RESPONSE;

    private MfiIap2MessageCodec() { }

    /** Original MFI builder: AuthenticationCertificate, parameter 0 = certificate bytes. */
    public static byte[] encodeCertificate(byte[] certificate) {
        return encodeSingleBytesParameter(CERTIFICATE_RESPONSE, certificate);
    }

    /** Original signature builder: AuthenticationResponse, parameter 0 = signature bytes. */
    public static byte[] encodeSignature(byte[] signature) {
        return encodeSingleBytesParameter(SIGNATURE_RESPONSE, signature);
    }

    /**
     * Original BAA body builder validates the supplied body as a complete parameter list,
     * then places those bytes directly in AuthenticationCertificate (no extra wrapper).
     */
    public static byte[] encodeBaaCertificateBody(byte[] parameterBody) {
        Iap2ParameterCodec.parse(parameterBody);
        return Iap2CsmFrameCodec.encode(CERTIFICATE_RESPONSE, parameterBody.clone());
    }

    /** Original BAA package: parameter 0 leaf, parameter 1 u8(1), parameter 2 intermediate. */
    public static byte[] encodeBaaCertificatePackage(byte[] leaf, byte[] intermediate) {
        if (leaf == null) throw new NullPointerException("leaf");
        if (intermediate == null) throw new NullPointerException("intermediate");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        append(body, Iap2ParameterCodec.encode(0, leaf));
        append(body, Iap2ParameterCodec.encodeU8(1, 1));
        append(body, Iap2ParameterCodec.encode(2, intermediate));
        return Iap2CsmFrameCodec.encode(CERTIFICATE_RESPONSE, body.toByteArray());
    }

    /** Extracts parameter zero from an incoming AA02 CSM frame, matching the original reader. */
    public static byte[] decodeChallenge(byte[] encodedFrame) {
        Frame frame = parseFrame(encodedFrame);
        if (frame.messageId != REQUEST_CHALLENGE)
            throw new IllegalArgumentException("Expected RequestAuthenticationChallengeResponse, got 0x"
                    + Integer.toHexString(frame.messageId));
        List<Iap2ParameterCodec.Parameter> parameters = Iap2ParameterCodec.parse(frame.payload);
        return Iap2ParameterCodec.first(parameters, 0).value();
    }

    public static Frame parseFrame(byte[] encodedFrame) {
        if (encodedFrame == null) throw new NullPointerException("encodedFrame");
        if (encodedFrame.length < Iap2CsmFrameCodec.HEADER_BYTES
                || encodedFrame.length > Iap2CsmFrameCodec.MAX_FRAME_BYTES)
            throw new IllegalArgumentException("CSM frame size must be 6..65535");
        int start = readU16(encodedFrame, 0);
        if (start != Iap2CsmFrameCodec.START) throw new IllegalArgumentException("CSM frame must start with 0x4040");
        int length = readU16(encodedFrame, 2);
        if (length != encodedFrame.length) throw new IllegalArgumentException("CSM frame length must equal its complete size");
        int messageId = readU16(encodedFrame, 4);
        byte[] payload = new byte[length - Iap2CsmFrameCodec.HEADER_BYTES];
        System.arraycopy(encodedFrame, Iap2CsmFrameCodec.HEADER_BYTES, payload, 0, payload.length);
        return new Frame(messageId, payload);
    }

    private static byte[] encodeSingleBytesParameter(int messageId, byte[] value) {
        if (value == null) throw new NullPointerException("value");
        return Iap2CsmFrameCodec.encode(messageId, Iap2ParameterCodec.encode(0, value));
    }
    private static void append(ByteArrayOutputStream out, byte[] value) {
        out.write(value, 0, value.length);
    }
    private static int readU16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    public static final class Frame {
        public final int messageId;
        private final byte[] payload;
        private Frame(int messageId, byte[] payload) {
            this.messageId = messageId;
            this.payload = payload.clone();
        }
        public byte[] payload() { return payload.clone(); }
    }
}
