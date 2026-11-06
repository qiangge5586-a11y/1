package com.shilapi.xcertplay.compat.mfi;

/**
 * Generation-safe adapter boundary for the two synchronous MFi provider phases.
 *
 * The same operation context is passed to both provider methods. Provider work is
 * deliberately performed outside this object's monitor so cancel() can signal a
 * blocked provider immediately. Providers MUST still honor the shared context.
 */
public final class MfiProviderSession {
    public static final int MIN_CHALLENGE_BYTES = 1;
    public static final int MAX_CHALLENGE_BYTES = 128;

    public interface Provider {
        byte[] readCertificate(MfiOperationContext context);
        byte[] signChallenge(byte[] challenge, MfiOperationContext context);
    }

    public enum State {
        NEW, CERTIFICATE_READING, CERTIFICATE_READY, SIGNING,
        CHALLENGE_SIGNED, FAILED, CLOSED
    }

    private final Provider provider;
    private final MfiOperationContext context;
    private final long generation;
    private volatile State state = State.NEW;

    public MfiProviderSession(Provider provider, MfiOperationContext context,
            long generation) {
        if (provider == null) throw new IllegalArgumentException("provider == null");
        if (context == null) throw new IllegalArgumentException("context == null");
        if (context.generation() != generation)
            throw new IllegalArgumentException("generation does not match operation context");
        this.provider = provider;
        this.context = context;
        this.generation = generation;
    }

    public byte[] readCertificate(long activeGeneration) {
        synchronized (this) {
            requireState(State.NEW);
            state = State.CERTIFICATE_READING;
        }
        try {
            context.checkActive(activeGeneration);
            byte[] certificate = provider.readCertificate(context);
            context.checkActive(activeGeneration);
            if (certificate == null || certificate.length < 1
                    || certificate.length > MfiI2cCertificateReader.MAX_CERTIFICATE_BYTES)
                throw new MfiI2cCertificateReader.TransportDataException(
                        "provider returned an invalid certificate length");
            synchronized (this) {
                requireState(State.CERTIFICATE_READING);
                state = State.CERTIFICATE_READY;
            }
            return certificate.clone();
        } catch (RuntimeException failure) {
            failUnlessClosed();
            throw failure;
        }
    }

    public byte[] signChallenge(byte[] challenge, long activeGeneration) {
        synchronized (this) {
            requireState(State.CERTIFICATE_READY);
            if (challenge == null || challenge.length < MIN_CHALLENGE_BYTES
                    || challenge.length > MAX_CHALLENGE_BYTES) {
                state = State.FAILED;
                throw new IllegalArgumentException("challenge length outside 1..128");
            }
            state = State.SIGNING;
        }
        try {
            context.checkActive(activeGeneration);
            byte[] signature = provider.signChallenge(challenge.clone(), context);
            context.checkActive(activeGeneration);
            if (signature == null || signature.length == 0)
                throw new MfiI2cCertificateReader.TransportDataException(
                        "provider returned an empty challenge signature");
            synchronized (this) {
                requireState(State.SIGNING);
                state = State.CHALLENGE_SIGNED;
            }
            return signature.clone();
        } catch (RuntimeException failure) {
            failUnlessClosed();
            throw failure;
        }
    }

    /**
     * Cancellation is published before taking the short state-transition monitor.
     * A provider call that ignores cancellation may still run until it returns, but
     * cancel() itself never waits for that provider call to release a session lock.
     */
    public void cancel() {
        synchronized (this) {
            if (state == State.CLOSED) return;
            state = State.CLOSED;
        }
        context.cancel();
    }

    public State state() { return state; }

    private synchronized void failUnlessClosed() {
        if (state != State.CLOSED) state = State.FAILED;
    }

    private void requireState(State expected) {
        if (state != expected)
            throw new IllegalStateException("MFi provider session state is " + state);
    }
}
