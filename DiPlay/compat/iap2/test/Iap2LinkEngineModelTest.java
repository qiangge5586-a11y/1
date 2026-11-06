package com.shilapi.xcertplay.compat.iap2;

import java.util.List;

public final class Iap2LinkEngineModelTest {
    private static int checks;

    public static void main(String[] args) {
        readinessGatesDataPlane();
        ackReleasesQueuedOutboundPacket();
        receiveAndExtendedAckRemainDistinctEvents();
        bothTimersAdvanceOnSameClock();
        terminalWindowFailureClosesComposition();
        negotiatedPeerParametersBuildBothWindows();
        postSyncFramesDispatchIntoWindows();
        eofClosesBeforeAndAfterReadiness();
        System.out.println("Iap2LinkEngineModelTest: PASS (" + checks + " checks)");
    }

    private static void readinessGatesDataPlane() {
        final Iap2LinkEngineModel m = model();
        m.start(true, 0);
        eq(Iap2LinkEngineModel.State.NEGOTIATING, m.state());
        throwsState(new Runnable() { public void run() {
            m.send(10, new byte[] {1}, 0);
        }});
    }

    private static void ackReleasesQueuedOutboundPacket() {
        Iap2LinkEngineModel m = model();
        m.start(true, 0);
        becomeReady(m);
        eq(Iap2LinkEngineModel.State.READY, m.state());
        eq(0, m.send(10, new byte[] {7}, 10).transmitted.size());
        Iap2OutboundWindowModel.Result ack = m.acknowledge(101, 20);
        eq(1, ack.transmitted.size());
        eq(105, ack.transmitted.get(0).sequence);
    }

    private static void receiveAndExtendedAckRemainDistinctEvents() {
        Iap2LinkEngineModel m = model();
        m.start(true, 0);
        becomeReady(m);
        Iap2ReceiveWindowModel.Result gap = m.receive(packet(14), 100);
        bytes(new byte[] {11, 12, 13}, gap.extendedAcknowledgement);
        Iap2ReceiveWindowModel.Result first = m.receive(packet(11), 101);
        eq(1, first.delivered.size());
        m.receive(packet(12), 102);
        Iap2ReceiveWindowModel.Result fill = m.receive(packet(13), 103);
        eq(2, fill.delivered.size());
        eq(14, fill.delivered.get(1).sequence);
    }

    private static void bothTimersAdvanceOnSameClock() {
        Iap2LinkEngineModel m = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel(),
                new Iap2OutboundWindowModel(99, Integer.valueOf(99), 4, 3,
                        10, 500, 4, 8),
                new Iap2ReceiveWindowModel(10, 10, 4, 3, 500, 0, 64));
        m.start(true, 0);
        becomeReady(m);
        m.send(10, new byte[] {1}, 100);
        m.receive(packet(11), 100);
        Iap2LinkEngineModel.TimerResult before = m.advanceTimers(599);
        eq(0, before.outbound.retransmitted.size());
        truth(!before.inbound.acknowledgementNow);
        Iap2LinkEngineModel.TimerResult due = m.advanceTimers(600);
        eq(1, due.outbound.retransmitted.size());
        truth(due.inbound.acknowledgementNow);
    }

    private static void terminalWindowFailureClosesComposition() {
        Iap2LinkEngineModel m = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel(),
                new Iap2OutboundWindowModel(99, Integer.valueOf(99), 4, 3,
                        10, 500, 1, 8),
                new Iap2ReceiveWindowModel(10, 10, 4, 3, 500, 0, 64));
        m.start(true, 0);
        becomeReady(m);
        m.send(10, new byte[] {1}, 100);
        Iap2LinkEngineModel.TimerResult due = m.advanceTimers(600);
        truth(due.outbound.terminalReason != null);
        eq(Iap2LinkEngineModel.State.CLOSED, m.state());
        truth(m.getTerminalReason().contains("retransmission"));
    }

    private static void negotiatedPeerParametersBuildBothWindows() {
        Iap2LinkNegotiationModel negotiation = new Iap2LinkNegotiationModel();
        Iap2LinkEngineModel m = new Iap2LinkEngineModel(negotiation);
        m.start(true, 0);
        m.takeNegotiationOutput();
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                2, 4096, 1500, 250, 3, 5,
                new int[][] {{10, 0, 2}, {11, 2, 1}});
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, sync), 1);
        m.takeNegotiationOutput();
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
        eq(Iap2LinkEngineModel.State.READY, m.state());
        Iap2LinkNegotiationModel.PeerConfiguration peer = m.getNegotiatedPeerConfiguration();
        eq(2, peer.maxOutgoing);
        eq(5, peer.maxAcknowledgements);
        Iap2OutboundWindowModel.Result sent = m.send(10, new byte[] {9}, 100);
        eq(1, sent.transmitted.size());
        eq(100, sent.transmitted.get(0).sequence);
        Iap2ReceiveWindowModel.Result received = m.receive(packet(78), 100);
        eq(1, received.delivered.size());
        eq(350L, received.acknowledgementDeadlineMillis.longValue());
        Iap2LinkEngineModel.TimerResult beforeAck = m.advanceTimers(349);
        truth(!beforeAck.inbound.acknowledgementNow);
        eq(0, beforeAck.outbound.retransmitted.size());
        Iap2LinkEngineModel.TimerResult ackDue = m.advanceTimers(350);
        truth(ackDue.inbound.acknowledgementNow);
        eq(0, ackDue.outbound.retransmitted.size());
        Iap2LinkEngineModel.TimerResult retryDue = m.advanceTimers(1600);
        eq(1, retryDue.outbound.retransmitted.size());
        eq(1500L, retryDue.outbound.retransmitted.get(0).deadlineMillis - 1600);
    }

    private static void postSyncFramesDispatchIntoWindows() {
        Iap2LinkEngineModel m = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        m.start(true, 0);
        m.takeNegotiationOutput();
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                2, 4096, 1500, 250, 3, 5,
                new int[][] {{10, 0, 2}, {11, 2, 1}});
        byte[] peerSyn = Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, sync);
        byte[] firstData = Iap2LinkFrameCodec.encode(0x40, 78, 99, 10,
                new byte[] {4, 5, 6});
        m.feedNegotiation(concat(peerSyn, firstData), 10);
        eq(Iap2LinkEngineModel.State.READY, m.state());
        List<Iap2LinkEngineModel.DispatchEvent> events = m.drainEvents();
        eq(1, events.size());
        notNull(events.get(0).receivedData);
        eq(1, events.get(0).receivedData.delivered.size());
        eq(78, events.get(0).receivedData.delivered.get(0).sequence);

        Iap2OutboundWindowModel.Result sent = m.send(10, new byte[] {9}, 20);
        eq(1, sent.transmitted.size());
        eq(100, sent.transmitted.get(0).sequence);
        byte[] extendedAck = Iap2LinkFrameCodec.encode(0x20, 79, 99, 11,
                new byte[] {100});
        m.feedNegotiation(extendedAck, 30);
        events = m.drainEvents();
        eq(1, events.size());
        notNull(events.get(0).extendedAcknowledgement);
        eq(1, events.get(0).extendedAcknowledgement.retransmitted.size());
        eq(100, events.get(0).extendedAcknowledgement.retransmitted.get(0).sequence);

        byte[] ackOnly = Iap2LinkFrameCodec.encode(0x40, 80, 100, 0, null);
        m.feedNegotiation(ackOnly, 40);
        events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).receivedData == null);
        truth(events.get(0).cumulativeAcknowledgement.accepted);
        eq(Iap2LinkEngineModel.State.READY, m.state());
    }

    private static void eofClosesBeforeAndAfterReadiness() {
        Iap2LinkEngineModel before = model();
        before.start(true, 0);
        before.endOfStream();
        eq(Iap2LinkEngineModel.State.CLOSED, before.state());
        Iap2LinkEngineModel after = model();
        after.start(true, 0);
        becomeReady(after);
        after.endOfStream();
        eq(Iap2LinkEngineModel.State.CLOSED, after.state());
    }

    private static Iap2LinkEngineModel model() {
        return new Iap2LinkEngineModel(new Iap2LinkNegotiationModel(),
                new Iap2OutboundWindowModel(104, Integer.valueOf(99), 4, 3,
                        10, 500, 4, 8),
                new Iap2ReceiveWindowModel(10, 10, 4, 3, 500, 0, 64));
    }

    private static void becomeReady(Iap2LinkEngineModel m) {
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                4, 65535, 4000, 500, 4, 3,
                new int[][] {{10, 0, 2}, {11, 2, 1}, {12, 1, 2}});
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 20, 0, 0, sync), 1);
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 20, 100, 0, null), 2);
    }

    private static Iap2ReceiveWindowModel.Packet packet(int sequence) {
        return new Iap2ReceiveWindowModel.Packet(sequence, 10,
                new byte[] {(byte) sequence});
    }
    private static void throwsState(Runnable r) {
        checks++;
        try { r.run(); throw new AssertionError("expected readiness rejection"); }
        catch (IllegalStateException expected) { }
    }
    private static void bytes(byte[] a, byte[] b) {
        checks++;
        if (b == null || a.length != b.length) throw new AssertionError("byte length");
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) throw new AssertionError("byte " + i);
    }
    private static void eq(int a, int b) { checks++; if (a != b) throw new AssertionError(a + " != " + b); }
    private static void eq(Object a, Object b) { checks++; if (a == null ? b != null : !a.equals(b)) throw new AssertionError(a + " != " + b); }
    private static void truth(boolean b) { checks++; if (!b) throw new AssertionError("expected true"); }
}
