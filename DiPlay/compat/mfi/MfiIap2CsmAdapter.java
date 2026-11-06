package com.shilapi.xcertplay.compat.mfi;

/**
 * Connects complete inbound CSM frames to the MFi state coordinator and emits
 * complete outbound CSM frames. The caller still owns CSM receive/send pump I/O.
 */
public final class MfiIap2CsmAdapter {
    private final MfiIap2ExchangeCoordinator coordinator;

    public MfiIap2CsmAdapter(MfiIap2ExchangeCoordinator coordinator) {
        if (coordinator == null) throw new IllegalArgumentException("coordinator == null");
        this.coordinator = coordinator;
    }

    /**
     * Processes one complete inbound CSM frame. Returns an encoded outbound frame,
     * or null for peer terminal result AA04/AA05.
     */
    public byte[] onInboundFrame(byte[] encodedFrame) {
        try {
            MfiIap2MessageCodec.Frame frame = MfiIap2MessageCodec.parseFrame(encodedFrame);
            if (frame.messageId == MfiIap2MessageCodec.REQUEST_CERTIFICATE) {
                MfiIap2ExchangeCoordinator.Reply reply = coordinator.requestCertificate();
                return MfiIap2MessageCodec.encodeCertificate(reply.payload());
            }
            if (frame.messageId == MfiIap2MessageCodec.REQUEST_CHALLENGE) {
                byte[] challenge = MfiIap2MessageCodec.decodeChallenge(encodedFrame);
                MfiIap2ExchangeCoordinator.Reply reply = coordinator.requestChallenge(challenge);
                return MfiIap2MessageCodec.encodeSignature(reply.payload());
            }
            if (frame.messageId == MfiIap2ExchangeCoordinator.AUTHENTICATION_FAILED
                    || frame.messageId == MfiIap2ExchangeCoordinator.AUTHENTICATION_SUCCEEDED) {
                coordinator.peerResult(frame.messageId);
                return null;
            }
            throw new IllegalArgumentException("Unexpected iAP2 MFi message 0x"
                    + Integer.toHexString(frame.messageId));
        } catch (RuntimeException failure) {
            coordinator.cancel();
            throw failure;
        }
    }

    public MfiIap2ExchangeCoordinator.State state() { return coordinator.state(); }
    public void cancel() { coordinator.cancel(); }
}
