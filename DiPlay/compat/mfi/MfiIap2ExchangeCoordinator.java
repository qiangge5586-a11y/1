package com.shilapi.xcertplay.compat.mfi;

/**
 * Coordinates source-confirmed iAP2 MFi message IDs. Provider work is deliberately
 * outside the coordinator monitor so reconnect/cancel can invalidate blocked work.
 * Wire parameter serialization remains the responsibility of the source-parity codec.
 */
public final class MfiIap2ExchangeCoordinator {
    public static final int REQUEST_CERTIFICATE = 0xAA00;
    public static final int CERTIFICATE_RESPONSE = 0xAA01;
    public static final int REQUEST_CHALLENGE = 0xAA02;
    public static final int SIGNATURE_RESPONSE = 0xAA03;
    public static final int AUTHENTICATION_FAILED = 0xAA04;
    public static final int AUTHENTICATION_SUCCEEDED = 0xAA05;

    public enum State { WAITING_FOR_CERTIFICATE, READING_CERTIFICATE, CERTIFICATE_SENT,
        SIGNING_CHALLENGE, SIGNATURE_SENT, SUCCEEDED, FAILED, CLOSED }

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

    /** Incoming AA00: read certificate and return the payload for AA01. */
    public Reply requestCertificate() {
        synchronized (this) {
            require(State.WAITING_FOR_CERTIFICATE);
            state = State.READING_CERTIFICATE;
        }
        try {
            byte[] certificate = provider.readCertificate(generation);
            synchronized (this) {
                require(State.READING_CERTIFICATE);
                state = State.CERTIFICATE_SENT;
            }
            return new Reply(CERTIFICATE_RESPONSE, certificate);
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        }
    }

    /** Incoming AA02: sign decoded parameter zero and return the payload for AA03. */
    public Reply requestChallenge(byte[] challenge) {
        synchronized (this) {
            require(State.CERTIFICATE_SENT);
            state = State.SIGNING_CHALLENGE;
        }
        try {
            byte[] signature = provider.signChallenge(challenge, generation);
            synchronized (this) {
                require(State.SIGNING_CHALLENGE);
                state = State.SIGNATURE_SENT;
            }
            return new Reply(SIGNATURE_RESPONSE, signature);
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        }
    }

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

    public void cancel() {
        synchronized (this) {
            if (state == State.SUCCEEDED || state == State.FAILED || state == State.CLOSED) return;
            state = State.CLOSED;
        }
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
        boolean cancel;
        synchronized (this) {
            cancel = state != State.SUCCEEDED && state != State.CLOSED && state != State.FAILED;
            if (cancel) state = State.FAILED;
        }
        if (cancel) provider.cancel();
    }
}
