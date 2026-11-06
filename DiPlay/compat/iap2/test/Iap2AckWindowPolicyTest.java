package com.shilapi.xcertplay.compat.iap2;

public final class Iap2AckWindowPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        cumulativeAckRemovalUsesPeerAckWindowPlusTen();
        sequenceWrapIsModulo256();
        sendQueueUsesStrictGreaterThanBoundary();
        queuedDrainUsesStrictLessThanBoundary();
        nullAcknowledgementStartsAtZeroDistance();
        System.out.println("Iap2AckWindowPolicyTest: PASS (" + checks + " checks)");
    }

    private static void cumulativeAckRemovalUsesPeerAckWindowPlusTen() {
        truth(Iap2AckWindowPolicy.remainsUnacknowledged(100, 99, 3));
        truth(Iap2AckWindowPolicy.remainsUnacknowledged(112, 99, 3));
        falsity(Iap2AckWindowPolicy.remainsUnacknowledged(113, 99, 3));
        falsity(Iap2AckWindowPolicy.remainsUnacknowledged(99, 99, 3));
        falsity(Iap2AckWindowPolicy.remainsUnacknowledged(98, 99, 3));
    }

    private static void sequenceWrapIsModulo256() {
        truth(Iap2AckWindowPolicy.remainsUnacknowledged(0, 255, 3));
        eq(1, Iap2AckWindowPolicy.sequenceDistance(0, Integer.valueOf(255)));
        falsity(Iap2AckWindowPolicy.remainsUnacknowledged(254, 255, 3));
    }

    private static void sendQueueUsesStrictGreaterThanBoundary() {
        falsity(Iap2AckWindowPolicy.shouldQueueNewPacket(103, Integer.valueOf(99), 4));
        truth(Iap2AckWindowPolicy.shouldQueueNewPacket(104, Integer.valueOf(99), 4));
        falsity(Iap2AckWindowPolicy.shouldQueueNewPacket(0, Integer.valueOf(255), 4));
    }

    private static void queuedDrainUsesStrictLessThanBoundary() {
        truth(Iap2AckWindowPolicy.mayDrainQueuedPacket(102, Integer.valueOf(99), 4));
        falsity(Iap2AckWindowPolicy.mayDrainQueuedPacket(103, Integer.valueOf(99), 4));
        falsity(Iap2AckWindowPolicy.mayDrainQueuedPacket(104, Integer.valueOf(99), 4));
    }

    private static void nullAcknowledgementStartsAtZeroDistance() {
        eq(0, Iap2AckWindowPolicy.sequenceDistance(99, null));
        falsity(Iap2AckWindowPolicy.shouldQueueNewPacket(99, null, 4));
        truth(Iap2AckWindowPolicy.mayDrainQueuedPacket(99, null, 4));
    }

    private static void eq(int expected, int actual) {
        checks++; if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    private static void truth(boolean value) {
        checks++; if (!value) throw new AssertionError("expected true");
    }
    private static void falsity(boolean value) {
        checks++; if (value) throw new AssertionError("expected false");
    }
}
