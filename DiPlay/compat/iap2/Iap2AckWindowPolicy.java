package com.shilapi.xcertplay.compat.iap2;

/** Exact comparison predicates transcribed from Iap2LinkEngine ACK/window paths. */
public final class Iap2AckWindowPolicy {
    private Iap2AckWindowPolicy() { }

    /** Mirrors handleAcknowledgement(): retain only packets ahead by <= maxAcknowledgements+10. */
    public static boolean remainsUnacknowledged(int packetSequence, int cumulativeAck,
            int peerMaxAcknowledgements) {
        checkByte(packetSequence);
        checkByte(cumulativeAck);
        checkByte(peerMaxAcknowledgements);
        int distance = sequenceDistance(packetSequence, cumulativeAck);
        return distance > 0 && distance <= peerMaxAcknowledgements + 10;
    }

    /** Mirrors sendPacket(): queue only when current distance is strictly greater than maxOutgoing. */
    public static boolean shouldQueueNewPacket(int sentSequence, Integer lastSentAcknowledged,
            int peerMaxOutgoing) {
        checkByte(sentSequence);
        checkNullableByte(lastSentAcknowledged);
        checkByte(peerMaxOutgoing);
        return sequenceDistance(sentSequence, lastSentAcknowledged) > peerMaxOutgoing;
    }

    /** Mirrors handleAcknowledgement() queue drain: send while distance is strictly below maxOutgoing. */
    public static boolean mayDrainQueuedPacket(int sentSequence, Integer lastSentAcknowledged,
            int peerMaxOutgoing) {
        checkByte(sentSequence);
        checkNullableByte(lastSentAcknowledged);
        checkByte(peerMaxOutgoing);
        return sequenceDistance(sentSequence, lastSentAcknowledged) < peerMaxOutgoing;
    }

    public static int sequenceDistance(int sequence, Integer previous) {
        checkByte(sequence);
        checkNullableByte(previous);
        return previous == null ? 0 : (sequence - previous.intValue()) & 0xff;
    }

    private static void checkNullableByte(Integer value) {
        if (value != null) checkByte(value.intValue());
    }
    private static void checkByte(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("u8");
    }
}
