package com.shilapi.xcertplay.compat.iap2;

import java.util.Arrays;

public final class Iap2ReceiveWindowModelTest {
    private static int checks;

    public static void main(String[] args) {
        inOrderDeliveryUsesDelayedAck();
        outOfOrderPacketsAreDeliveredInSequence();
        duplicateBufferedPacketIsIgnored();
        extendedAckContainsRawMissingSequenceNumbers();
        sequenceWrapPreservesOrdering();
        staleAndOutOfWindowPacketsForceAck();
        outOfOrderCapacityIsTerminal();
        receiveWindowThresholdForcesAck();
        zeroPeerAckLimitDisablesAckTimer();
        exhaustivelyChecksGapAndExtendedAckBoundariesAcrossWrap();
        System.out.println("Iap2ReceiveWindowModelTest: PASS (" + checks + " checks)");
    }

    private static void inOrderDeliveryUsesDelayedAck() {
        Iap2ReceiveWindowModel m = model(10, 10, 4, 3, 500, 0, 64);
        Iap2ReceiveWindowModel.Result r = m.receive(packet(11), 100);
        eq(1, r.delivered.size());
        eq(11, r.delivered.get(0).sequence);
        eq(11, m.getLastReceivedInOrder());
        falsity(r.acknowledgementNow);
        eq(Long.valueOf(600), r.acknowledgementDeadlineMillis);
        falsity(m.advanceTime(599).acknowledgementNow);
        truth(m.advanceTime(600).acknowledgementNow);
        eq(11, m.getLastAcknowledgedReceived());
    }

    private static void outOfOrderPacketsAreDeliveredInSequence() {
        Iap2ReceiveWindowModel m = model(10, 10, 4, 3, 500, 0, 64);
        Iap2ReceiveWindowModel.Result gap = m.receive(packet(12), 100);
        eq(0, gap.delivered.size());
        eq(1, m.getOutOfOrderCount());
        Iap2ReceiveWindowModel.Result filled = m.receive(packet(11), 200);
        eq(2, filled.delivered.size());
        eq(11, filled.delivered.get(0).sequence);
        eq(12, filled.delivered.get(1).sequence);
        eq(12, m.getLastReceivedInOrder());
        eq(0, m.getOutOfOrderCount());
    }

    private static void duplicateBufferedPacketIsIgnored() {
        Iap2ReceiveWindowModel m = model(10, 10, 4, 3, 500, 0, 64);
        m.receive(packet(12), 100);
        Iap2ReceiveWindowModel.Result duplicate = m.receive(packet(12), 110);
        eq(1, m.getOutOfOrderCount());
        falsity(duplicate.acknowledgementNow);
        eq(null, duplicate.extendedAcknowledgement);
    }

    private static void extendedAckContainsRawMissingSequenceNumbers() {
        Iap2ReceiveWindowModel m = model(10, 10, 4, 3, 500, 0, 64);
        Iap2ReceiveWindowModel.Result r = m.receive(packet(14), 100);
        bytes(new byte[] {11, 12, 13}, r.extendedAcknowledgement);
        falsity(r.acknowledgementNow);
        eq(1, m.getOutOfOrderCount());
        eq(null, r.acknowledgementDeadlineMillis);
    }

    private static void sequenceWrapPreservesOrdering() {
        Iap2ReceiveWindowModel m = model(254, 254, 4, 3, 500, 0, 64);
        m.receive(packet(0), 100);
        Iap2ReceiveWindowModel.Result r = m.receive(packet(255), 110);
        eq(2, r.delivered.size());
        eq(255, r.delivered.get(0).sequence);
        eq(0, r.delivered.get(1).sequence);
        eq(0, m.getLastReceivedInOrder());
    }

    private static void staleAndOutOfWindowPacketsForceAck() {
        Iap2ReceiveWindowModel stale = model(10, 10, 4, 3, 500, 0, 64);
        stale.receive(packet(11), 0);
        Long pendingDeadline = stale.getAcknowledgementDeadlineMillis();
        truth(stale.receive(packet(10), 1).acknowledgementNow);
        eq(10, stale.getLastAcknowledgedReceived());
        eq(pendingDeadline, stale.getAcknowledgementDeadlineMillis());
        Iap2ReceiveWindowModel far = model(10, 10, 4, 3, 500, 0, 64);
        truth(far.receive(packet(25), 1).acknowledgementNow);
    }

    private static void outOfOrderCapacityIsTerminal() {
        Iap2ReceiveWindowModel m = model(10, 10, 4, 3, 500, 0, 1);
        m.receive(packet(12), 100);
        Iap2ReceiveWindowModel.Result r = m.receive(packet(13), 110);
        truth(r.terminalReason != null);
        truth(r.terminalReason.contains("out-of-order"));
        eq(1, m.getOutOfOrderCount());
    }

    private static void receiveWindowThresholdForcesAck() {
        Iap2ReceiveWindowModel m = model(10, 10, 4, 3, 500, 0, 64);
        falsity(m.receive(packet(11), 1).acknowledgementNow);
        falsity(m.receive(packet(12), 2).acknowledgementNow);
        falsity(m.receive(packet(13), 3).acknowledgementNow);
        Iap2ReceiveWindowModel.Result forced = m.receive(packet(14), 4);
        truth(forced.acknowledgementNow);
        eq(14, m.getLastAcknowledgedReceived());
        eq(null, m.getAcknowledgementDeadlineMillis());
    }

    private static void zeroPeerAckLimitDisablesAckTimer() {
        Iap2ReceiveWindowModel m = model(10, 10, 4, 0, 0, 0, 64);
        Iap2ReceiveWindowModel.Result r = m.receive(packet(11), 100);
        falsity(r.acknowledgementNow);
        eq(null, r.acknowledgementDeadlineMillis);
        falsity(m.advanceTime(10000).acknowledgementNow);
    }


    private static void exhaustivelyChecksGapAndExtendedAckBoundariesAcrossWrap() {
        int[] windows = new int[] {1, 2, 4, 16, 255};
        for (int w = 0; w < windows.length; w++) {
            int peerWindow = windows[w];
            for (int start = 0; start < 256; start++) {
                for (int distance = 2; distance <= 255; distance++) {
                    int target = (start + distance) & 0xff;
                    Iap2ReceiveWindowModel m = model(start, start, peerWindow,
                            3, 500, 0, 1);
                    Iap2ReceiveWindowModel.Result r = m.receive(packet(target), 100);
                    boolean inRange = distance <= peerWindow + 10;
                    if (!inRange) {
                        truth(r.acknowledgementNow);
                        eq(0, m.getOutOfOrderCount());
                        eq(null, r.extendedAcknowledgement);
                        continue;
                    }
                    falsity(r.acknowledgementNow);
                    eq(1, m.getOutOfOrderCount());
                    eq(0, r.delivered.size());
                    if (distance >= peerWindow) {
                        byte[] expectedMissing = new byte[distance - 1];
                        for (int i = 0; i < expectedMissing.length; i++) {
                            expectedMissing[i] = (byte) ((start + i + 1) & 0xff);
                        }
                        bytes(expectedMissing, r.extendedAcknowledgement);
                        eq(null, r.acknowledgementDeadlineMillis);
                    } else {
                        eq(null, r.extendedAcknowledgement);
                        eq(Long.valueOf(600), r.acknowledgementDeadlineMillis);
                    }
                }
            }
        }
    }

    private static Iap2ReceiveWindowModel model(int received, int acknowledged,
            int maxOutgoing, int maxAcks, int ackTimeout, int delta, int capacity) {
        return new Iap2ReceiveWindowModel(received, acknowledged, maxOutgoing,
                maxAcks, ackTimeout, delta, capacity);
    }
    private static Iap2ReceiveWindowModel.Packet packet(int sequence) {
        return new Iap2ReceiveWindowModel.Packet(sequence, 10,
                new byte[] {(byte) sequence});
    }
    private static void bytes(byte[] a, byte[] b) {
        checks++; if (!Arrays.equals(a, b)) throw new AssertionError("byte vector mismatch");
    }
    private static void eq(int a, int b) {
        checks++; if (a != b) throw new AssertionError(a + " != " + b);
    }
    private static void eq(Object a, Object b) {
        checks++; if (a == null ? b != null : !a.equals(b))
            throw new AssertionError(a + " != " + b);
    }
    private static void truth(boolean value) {
        checks++; if (!value) throw new AssertionError("expected true");
    }
    private static void falsity(boolean value) {
        checks++; if (value) throw new AssertionError("expected false");
    }
}
