package com.shilapi.xcertplay.compat.mfi;

/**
 * Generation-safe adapter boundary for the two synchronous MFi provider phases.
 *
 * The same operation context is passed to certificate retrieval and challenge signing.
 * Provider implementations MUST check it before/after every blocking I/O or crypto phase
 * and use remainingMillis() for bounded waits. This wrapper detects late completion but
 * cannot forcibly preempt a provider that ignores the context.
 */
public final class MfiProviderSession {
    public static final int MIN_CHALLENGE_BYTES = 1;
    public static final int MAX_CHALLENGE_BYTES = 128;

    public interface Provider {
        byte[] readCertificate(MfiOperationContext context);
        byte[] signChallenge(byte[] challenge, MfiOperationContext context);
    }

    public enum State {
        NEW, CERTIFICATE_READY, CHALLENGE_SIGNED, FAILED, CLOSED
    }

    private final Provider provider;
    private final MfiOperationContext context;
    private final long generation;
    private State state = State.NEW;

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

    public synchronized byte[] readCertificate(long activeGeneration) {
        requireState(State.NEW);
        try {
            context.checkActive(activeGeneration);
            byte[] certificate = provider.readCertificate(context);
            context.checkActive(activeGeneration);
            if (certificate == null || certificate.length < 1
                    || certificate.length > MfiI2cCertificateReader.MAX_CERTIFICATE_BYTES)
                throw new MfiI2cCertificateReader.TransportDataException(
                        "provider returned an invalid certificate length");
            state = State.CERTIFICATE_READY;
            return certificate.clone();
        } catch (RuntimeException failure) {
            state = State.FAILED;
            throw failure;
        }
    }

    public synchronized byte[] signChallenge(byte[] challenge, long activeGeneration) {
        requireState(State.CERTIFICATE_READY);
        if (challenge == null || challenge.length < MIN_CHALLENGE_BYTES
                || challenge.length > MAX_CHALLENGE_BYTES) {
            state = State.FAILED;
            throw new IllegalArgumentException("challenge length outside 1..128");
        }
        try {
            context.checkActive(activeGeneration);
            byte[] signature = provider.signChallenge(challenge.clone(), context);
            context.checkActive(activeGeneration);
            if (signature == null || signature.length == 0)
                throw new MfiI2cCertificateReader.TransportDataException(
                        "provider returned an empty challenge signature");
            state = State.CHALLENGE_SIGNED;
            return signature.clone();
        } catch (RuntimeException failure) {
            state = State.FAILED;
            throw failure;
        }
    }

    public synchronized void cancel() {
        context.cancel();
        if (state != State.CHALLENGE_SIGNED) state = State.CLOSED;
    }

    public synchronized State state() { return state; }

    private void requireState(State expected) {
        if (state != expected)
            throw new IllegalStateException("MFi provider session state is " + state);
    }
}
