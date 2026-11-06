package com.shilapi.xcertplay.compat.mfi;

/**
 * Source-grounded iAP2 MFi exchange coordinator.
 *
 * This class coordinates the AA00..AA05 message sequence only. The transport,
 * parameter encoding/decoding and actual provider remain supplied by the caller;
 * no wire parameter layout is guessed here.
 */
public final class MfiIap2ExchangeCoordinator {
    public static final int REQUEST_CERTIFICATE = 0xAA00;
    public static final int CERTIFICATE_RESPONSE = 0xAA01;
    public static final int REQUEST_CHALLENGE = 0xAA02;
    public static final int SIGNATURE_RESPONSE = 0xAA03;
    public static final int AUTHENTICATION_FAILED = 0xAA04;
    public static final int AUTHENTICATION_SUCCEEDED = 0xAA05;

    public enum State { WAITING_FOR_CERTIFICATE, CERTIFICATE_SENT,
        SIGNATURE_SENT, SUCCEEDED, FAILED, CLOSED }

    public static final class Reply {
        public final int messageId;
        private final byte[] payload;
        private Reply(int messageId, byte[] payload) {
            this.messageId = messageId;
            this.payload = payload.clone();
        }
        public byte[] payload() { return payload.clone(); }
    }

    public static final class ProtocolException extends IllegalStateException {
        public ProtocolException(String message) { super(message); }
    }

    private final MfiProviderSession provider;
    private final long generation;
    private volatile State state = State.WAITING_FOR_CERTIFICATE;

    public MfiIap2ExchangeCoordinator(MfiProviderSession provider, long generation) {
        if (provider == null) throw new IllegalArgumentException("provider == null");
        this.provider = provider;
        this.generation = generation;
    }

    /** Handle AA00 and produce AA01. */
    public synchronized Reply requestCertificate() {
        require(State.WAITING_FOR_CERTIFICATE);
        try {
            byte[] certificate = provider.readCertificate(generation);
            state = State.CERTIFICATE_SENT;
            return new Reply(CERTIFICATE_RESPONSE, certificate);
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        }
    }

    /** Handle AA02 and produce AA03. Challenge parameter is the decoded raw value. */
    public synchronized Reply requestChallenge(byte[] challenge) {
        require(State.CERTIFICATE_SENT);
        try {
            byte[] signature = provider.signChallenge(challenge, generation);
            state = State.SIGNATURE_SENT;
            return new Reply(SIGNATURE_RESPONSE, signature);
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        }
    }

    /** Handle peer terminal result AA04/AA05. */
    public synchronized void peerResult(int messageId) {
        if (messageId == AUTHENTICATION_FAILED) {
            if (state == State.SUCCEEDED || state == State.CLOSED)
                throw protocolFailure("authentication failure after terminal state");
            fail();
            return;
        }
        if (messageId == AUTHENTICATION_SUCCEEDED) {
            require(State.SIGNATURE_SENT);
            state = State.SUCCEEDED;
            return;
        }
        throw protocolFailure("message is not an MFi terminal result");
    }

    public synchronized void cancel() {
        if (state == State.SUCCEEDED || state == State.FAILED || state == State.CLOSED) return;
        state = State.CLOSED;
        provider.cancel();
    }

    public State state() { return state; }

    private void require(State expected) {
        if (state != expected)
            throw protocolFailure("unexpected MFi message for state " + state);
    }

    private ProtocolException protocolFailure(String message) {
        fail();
        return new ProtocolException(message);
    }

    private void fail() {
        if (state != State.SUCCEEDED && state != State.CLOSED) {
            state = State.FAILED;
            provider.cancel();
        }
    }
}
