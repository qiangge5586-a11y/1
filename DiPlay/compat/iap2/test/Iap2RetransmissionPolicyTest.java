package com.shilapi.xcertplay.compat.iap2;

public final class Iap2RetransmissionPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        zeroRetriesDisablesRetention();
        maxFourMeansThreeRetransmissionsThenFailure();
        explicitExtendedAckUsesSameCounter();
        missingSequencePayloadIsRawUnsignedList();
        System.out.println("Iap2RetransmissionPolicyTest: PASS (" + checks + " checks)");
    }

    private static void zeroRetriesDisablesRetention() {
        truth(!Iap2RetransmissionPolicy.shouldRetain(0));
        Iap2RetransmissionPolicy.Decision d = Iap2RetransmissionPolicy.onRetry(0, 0);
        eq(Iap2RetransmissionPolicy.Action.NOT_RETAINED, d.action);
        eq(0, d.retryCount);
    }

    private static void maxFourMeansThreeRetransmissionsThenFailure() {
        truth(Iap2RetransmissionPolicy.shouldRetain(4));
        Iap2RetransmissionPolicy.Decision d1 = Iap2RetransmissionPolicy.onRetry(0, 4);
        eq(Iap2RetransmissionPolicy.Action.RETRANSMIT, d1.action); eq(1, d1.retryCount);
        Iap2RetransmissionPolicy.Decision d2 = Iap2RetransmissionPolicy.onRetry(d1.retryCount, 4);
        eq(Iap2RetransmissionPolicy.Action.RETRANSMIT, d2.action); eq(2, d2.retryCount);
        Iap2RetransmissionPolicy.Decision d3 = Iap2RetransmissionPolicy.onRetry(d2.retryCount, 4);
        eq(Iap2RetransmissionPolicy.Action.RETRANSMIT, d3.action); eq(3, d3.retryCount);
        Iap2RetransmissionPolicy.Decision d4 = Iap2RetransmissionPolicy.onRetry(d3.retryCount, 4);
        eq(Iap2RetransmissionPolicy.Action.TERMINATE, d4.action); eq(4, d4.retryCount);
    }

    private static void explicitExtendedAckUsesSameCounter() {
        Iap2RetransmissionPolicy.Decision d =
                Iap2RetransmissionPolicy.onExplicitMissingSequence(2, 4);
        eq(Iap2RetransmissionPolicy.Action.RETRANSMIT, d.action);
        eq(3, d.retryCount);
        Iap2RetransmissionPolicy.Decision terminal =
                Iap2RetransmissionPolicy.onExplicitMissingSequence(3, 4);
        eq(Iap2RetransmissionPolicy.Action.TERMINATE, terminal.action);
    }

    private static void missingSequencePayloadIsRawUnsignedList() {
        byte[] missing = new byte[] {(byte)255, 0, 7};
        truth(Iap2RetransmissionPolicy.isSequenceRequested(255, missing));
        truth(Iap2RetransmissionPolicy.isSequenceRequested(0, missing));
        truth(Iap2RetransmissionPolicy.isSequenceRequested(7, missing));
        truth(!Iap2RetransmissionPolicy.isSequenceRequested(8, missing));
        truth(!Iap2RetransmissionPolicy.isSequenceRequested(7, null));
    }

    private static void eq(int a, int b) {
        checks++; if (a != b) throw new AssertionError(a + " != " + b);
    }
    private static void eq(Object a, Object b) {
        checks++; if (a != b) throw new AssertionError(a + " != " + b);
    }
    private static void truth(boolean value) {
        checks++; if (!value) throw new AssertionError("expected true");
    }
}
