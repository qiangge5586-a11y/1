package com.shilapi.xcertplay.compat.iap2;

import java.util.Arrays;

public final class Iap2OutboundWindowModelTest {
    private static int checks;

    public static void main(String[] args) {
        sendsAndRetainsWithDeadline();
        zeroRetryLimitDoesNotRetain();
        outgoingWindowQueuesAndAckDrains();
        cumulativeAckRemovesOnlyCoveredPackets();
        cumulativeAckKeepsLaterDeadlinesArmed();
        sourceAcceptsReceivedAckWatermarkWithoutForwardValidation();
        sourceAckStopsAtFirstPacketInsideRetentionWindow();
        timeoutRetransmitsSamePacketAndAdvancesRetry();
        earliestDuePacketLeavesOtherDeadlineArmed();
        sourceTimerUsesLastSentPacketDeadline();
        extendedAckUsesLastMatchedPacketDeadline();
        retryLimitTerminatesAtExactCount();
        extendedAckRetransmitsOnlyRequestedSequences();
        sequenceWrapWorksAcrossAckAndSend();
        queueCapacityFailureIsTerminal();
        System.out.println("Iap2OutboundWindowModelTest: PASS (" + checks + " checks)");
    }

    private static void sendsAndRetainsWithDeadline() {
        Iap2OutboundWindowModel m = model(99, 99, 4, 3, 10, 500, 4, 8);
        Iap2OutboundWindowModel.Result r = m.send(10, new byte[] {1, 2}, 100);
        eq(1, r.transmitted.size());
        Iap2OutboundWindowModel.Packet p = r.transmitted.get(0);
        eq(100, p.sequence);
        eq(10, p.sessionId);
        bytes(new byte[] {1, 2}, p.payload);
        eq(0, p.retryCount);
        eq(600L, p.deadlineMillis);
        eq(1, m.getUnacknowledgedCount());
        eq(Long.valueOf(600), m.getNextDeadlineMillis());
    }

    private static void zeroRetryLimitDoesNotRetain() {
        Iap2OutboundWindowModel m = model(99, 99, 4, 3, 10, 500, 0, 8);
        Iap2OutboundWindowModel.Packet p = m.send(11, new byte[] {7}, 100).transmitted.get(0);
        eq(100, p.sequence);
        eq(0, m.getUnacknowledgedCount());
        eq(null, m.getNextDeadlineMillis());
        eq(null, m.advanceTime(10000).terminalReason);
    }

    private static void outgoingWindowQueuesAndAckDrains() {
        Iap2OutboundWindowModel m = model(104, 99, 4, 3, 10, 500, 4, 8);
        Iap2OutboundWindowModel.Result queued = m.send(10, new byte[] {1}, 10);
        eq(0, queued.transmitted.size());
        eq(1, m.getQueuedCount());
        Iap2OutboundWindowModel.Result ack = m.acknowledge(101, 20);
        truth(ack.acknowledgementAccepted);
        eq(1, ack.transmitted.size());
        eq(105, ack.transmitted.get(0).sequence);
        eq(0, m.getQueuedCount());
        eq(105, m.getSentSequence());
    }

    private static void cumulativeAckRemovesOnlyCoveredPackets() {
        Iap2OutboundWindowModel m = model(99, 99, 10, 3, 10, 500, 4, 8);
        m.send(10, new byte[] {1}, 0);
        m.send(10, new byte[] {2}, 1);
        Iap2OutboundWindowModel.Result r = m.acknowledge(100, 2);
        truth(r.acknowledgementAccepted);
        eq(1, r.acknowledged.size());
        eq(100, r.acknowledged.get(0).intValue());
        eq(1, m.getUnacknowledgedCount());
        eq(101, m.getUnacknowledgedSnapshot().get(0).sequence);
    }

    private static void cumulativeAckKeepsLaterDeadlinesArmed() {
        Iap2OutboundWindowModel m = model(99, 99, 10, 3, 10, 500, 4, 8);
        m.send(10, new byte[] {1}, 100);
        m.send(10, new byte[] {2}, 101);
        m.send(10, new byte[] {3}, 102);
        Iap2OutboundWindowModel.Result ack = m.acknowledge(100, 150);
        eq(1, ack.acknowledged.size());
        eq(100, ack.acknowledged.get(0).intValue());
        eq(Long.valueOf(601), m.getNextDeadlineMillis());
        Iap2OutboundWindowModel.Result due = m.advanceTime(600);
        eq(0, due.retransmitted.size());
        due = m.advanceTime(601);
        eq(1, due.retransmitted.size());
        eq(101, due.retransmitted.get(0).sequence);
        eq(1, due.retransmitted.get(0).retryCount);
        eq(Long.valueOf(602), m.getNextDeadlineMillis());
    }

    private static void sourceAcceptsReceivedAckWatermarkWithoutForwardValidation() {
        Iap2OutboundWindowModel m = model(99, 99, 4, 3, 10, 500, 4, 8);
        m.send(10, new byte[] {1}, 0);
        Iap2OutboundWindowModel.Result r = m.acknowledge(120, 1);
        truth(r.acknowledgementAccepted);
        eq(120, m.getLastSentAcknowledged().intValue());
        eq(0, m.getUnacknowledgedCount());
    }

    private static void sourceAckStopsAtFirstPacketInsideRetentionWindow() {
        Iap2OutboundWindowModel m = model(99, 99, 10, 0, 10, 500, 4, 8);
        m.send(10, new byte[] {1}, 0);
        m.send(10, new byte[] {2}, 1);
        m.send(10, new byte[] {3}, 2);
        Iap2OutboundWindowModel.Result r = m.acknowledge(90, 3);
        truth(r.acknowledgementAccepted);
        // Sequence 100 is at the edge of the source's 10-packet slack window;
        // the original loop stops there and leaves all later packets untouched.
        eq(0, r.acknowledged.size());
        eq(3, m.getUnacknowledgedCount());
    }

    private static void timeoutRetransmitsSamePacketAndAdvancesRetry() {
        Iap2OutboundWindowModel m = model(99, 99, 4, 3, 10, 500, 4, 8);
        Iap2OutboundWindowModel.Packet first = m.send(10, new byte[] {3, 4}, 100)
                .transmitted.get(0);
        falsity(m.advanceTime(599).retransmitted.size() != 0);
        Iap2OutboundWindowModel.Result due = m.advanceTime(600);
        eq(1, due.retransmitted.size());
        Iap2OutboundWindowModel.Packet retry = due.retransmitted.get(0);
        eq(first.sequence, retry.sequence);
        eq(first.sessionId, retry.sessionId);
        bytes(first.payload, retry.payload);
        eq(1, retry.retryCount);
        eq(1100L, retry.deadlineMillis);
        eq(Long.valueOf(1100), m.getNextDeadlineMillis());
    }

    private static void earliestDuePacketLeavesOtherDeadlineArmed() {
        Iap2OutboundWindowModel m = model(99, 99, 8, 3, 10, 500, 4, 8);
        m.send(10, new byte[] {1}, 100);
        m.send(11, new byte[] {2}, 101);
        eq(Long.valueOf(601), m.getNextDeadlineMillis());
        eq(0, m.advanceTime(600).retransmitted.size());
        Iap2OutboundWindowModel.Result first = m.advanceTime(601);
        eq(1, first.retransmitted.size());
        eq(100, first.retransmitted.get(0).sequence);
        eq(Long.valueOf(601), m.getNextDeadlineMillis());
        Iap2OutboundWindowModel.Result second = m.advanceTime(601);
        eq(1, second.retransmitted.size());
        eq(101, second.retransmitted.get(0).sequence);
        eq(Long.valueOf(1101), m.getNextDeadlineMillis());
    }

    private static void sourceTimerUsesLastSentPacketDeadline() {
        Iap2OutboundWindowModel m = model(99, 99, 8, 3, 10, 500, 4, 8);
        m.send(10, new byte[] {1}, 100);
        m.send(10, new byte[] {2}, 101);
        eq(Long.valueOf(601), m.getNextDeadlineMillis());
        Iap2OutboundWindowModel.Result early = m.advanceTime(600);
        eq(0, early.retransmitted.size());
        Iap2OutboundWindowModel.Result due = m.advanceTime(601);
        eq(1, due.retransmitted.size());
        eq(100, due.retransmitted.get(0).sequence);
        eq(Long.valueOf(601), m.getNextDeadlineMillis());
    }

    private static void extendedAckUsesLastMatchedPacketDeadline() {
        Iap2OutboundWindowModel m = model(99, 99, 8, 3, 10, 100, 4, 8);
        m.send(10, new byte[] {1}, 0);
        m.send(11, new byte[] {2}, 1);
        Iap2OutboundWindowModel.Result r = m.requestMissing(
                new byte[] {101, 100}, 20);
        eq(2, r.retransmitted.size());
        // The original iterates outstanding packets in order; sequence 101
        // is the last match and therefore owns the receive-ACK timer.
        eq(Long.valueOf(101), m.getNextDeadlineMillis());
    }

    private static void retryLimitTerminatesAtExactCount() {
        Iap2OutboundWindowModel m = model(99, 99, 4, 3, 10, 10, 2, 8);
        m.send(10, new byte[] {1}, 0);
        eq(1, m.advanceTime(10).retransmitted.get(0).retryCount);
        Iap2OutboundWindowModel.Result terminal = m.advanceTime(20);
        eq(0, terminal.retransmitted.size());
        truth(terminal.terminalReason != null);
        eq(2, m.getUnacknowledgedSnapshot().get(0).retryCount);
    }

    private static void extendedAckRetransmitsOnlyRequestedSequences() {
        Iap2OutboundWindowModel m = model(99, 99, 10, 3, 10, 100, 4, 8);
        m.send(10, new byte[] {1}, 0);
        m.send(11, new byte[] {2}, 1);
        Iap2OutboundWindowModel.Result r = m.requestMissing(new byte[] {101}, 20);
        eq(1, r.retransmitted.size());
        eq(101, r.retransmitted.get(0).sequence);
        eq(11, r.retransmitted.get(0).sessionId);
        eq(1, r.retransmitted.get(0).retryCount);
        eq(101L, r.retransmitted.get(0).deadlineMillis);
        eq(Long.valueOf(101), m.getNextDeadlineMillis());
    }

    private static void sequenceWrapWorksAcrossAckAndSend() {
        Iap2OutboundWindowModel m = model(254, 254, 4, 3, 10, 100, 4, 8);
        eq(255, m.send(10, new byte[] {1}, 0).transmitted.get(0).sequence);
        eq(0, m.send(10, new byte[] {2}, 1).transmitted.get(0).sequence);
        Iap2OutboundWindowModel.Result r = m.acknowledge(255, 2);
        truth(r.acknowledgementAccepted);
        eq(1, m.getUnacknowledgedCount());
        eq(0, m.getUnacknowledgedSnapshot().get(0).sequence);
    }

    private static void queueCapacityFailureIsTerminal() {
        Iap2OutboundWindowModel m = model(104, 99, 4, 3, 10, 500, 4, 0);
        Iap2OutboundWindowModel.Result r = m.send(10, new byte[] {1}, 0);
        truth(r.terminalReason != null);
        truth(m.getTerminalReason().contains("queue limit"));
    }

    private static Iap2OutboundWindowModel model(int sent, Integer ack, int maxOut,
            int maxAcks, int slack, int timeout, int retries, int queueLimit) {
        return new Iap2OutboundWindowModel(sent, ack, maxOut, maxAcks,
                slack, timeout, retries, queueLimit);
    }
    private static void bytes(byte[] a, byte[] b) {
        checks++; if (!Arrays.equals(a, b)) throw new AssertionError("byte mismatch");
    }
    private static void eq(int a, int b) {
        checks++; if (a != b) throw new AssertionError(a + " != " + b);
    }
    private static void eq(long a, long b) {
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
