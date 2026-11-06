package com.shilapi.xcertplay.compat.iap2;

/**
 * Source-derived retransmission counter policy from DiPlay 0.3.11
 * Iap2LinkEngine. This is a deterministic offline policy helper, not a
 * transport or complete link-engine implementation.
 */
public final class Iap2RetransmissionPolicy {
    public enum Action { NOT_RETAINED, RETRANSMIT, TERMINATE }

    public static final class Decision {
        public final Action action;
        public final int retryCount;

        private Decision(Action action, int retryCount) {
            this.action = action;
            this.retryCount = retryCount;
        }
    }

    private Iap2RetransmissionPolicy() { }

    /** A peer maxRetransmissions of zero disables retention/retransmission. */
    public static boolean shouldRetain(int peerMaxRetransmissions) {
        requireByte(peerMaxRetransmissions);
        return peerMaxRetransmissions != 0;
    }

    /**
     * Mirrors the source order: increment first, then terminate when the
     * incremented counter equals peer maxRetransmissions. Therefore a peer
     * value of four permits retransmission counts 1, 2 and 3; count 4 dies.
     */
    public static Decision onRetry(int currentRetryCount, int peerMaxRetransmissions) {
        requireByte(currentRetryCount);
        requireByte(peerMaxRetransmissions);
        if (peerMaxRetransmissions == 0) {
            return new Decision(Action.NOT_RETAINED, currentRetryCount);
        }
        int next = currentRetryCount + 1;
        if (next > 255) next = 255;
        return new Decision(next == peerMaxRetransmissions ? Action.TERMINATE
                : Action.RETRANSMIT, next);
    }

    /** Extended-ACK requests use the same per-packet retry counter policy. */
    public static Decision onExplicitMissingSequence(int currentRetryCount,
            int peerMaxRetransmissions) {
        return onRetry(currentRetryCount, peerMaxRetransmissions);
    }

    /** Extended-ACK payload is a list of raw u8 sequence numbers, not a bitmap. */
    public static boolean isSequenceRequested(int sequence, byte[] missingSequences) {
        requireByte(sequence);
        if (missingSequences == null) return false;
        for (int i = 0; i < missingSequences.length; i++) {
            if ((missingSequences[i] & 0xFF) == sequence) return true;
        }
        return false;
    }

    private static void requireByte(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("u8");
    }
}
