package com.shilapi.xcertplay.compat.iap2;

public final class Iap2LinkEngineModelTest {
    private static int checks;

    public static void main(String[] args) {
        readinessGatesDataPlane();
        ackReleasesQueuedOutboundPacket();
        receiveAndExtendedAckRemainDistinctEvents();
        bothTimersAdvanceOnSameClock();
        eofClosesBeforeAndAfterReadiness();
        System.out.println("Iap2LinkEngineModelTest: PASS (" + checks + " checks)");
    }

    private static void readinessGatesDataPlane() {
        Iap2LinkEngineModel m = model();
        m.start(true, 0);
        eq(Iap2LinkEngineModel.State.NEGOTIATING, m.state());
        throwsState(new Runnable() { public void run() {
            modelForGate.send(10, new byte[] {1}, 0);
        }});
    }

    private static Iap2LinkEngineModel modelForGate;

    private static void ackReleasesQueuedOutboundPacket() {
        Iap2LinkEngineModel m = model();
        modelForGate = m;
        m.start(true, 0);
        becomeReady(m);
        eq(Iap2LinkEngineModel.State.READY, m.state());
        eq(0, m.send(10, new byte[] {7}, 10).transmitted.size());
        eq(1, m.acknowledge(101, 20).transmitted.size());
        eq(105, m.acknowledge(101, 21).transmitted.size() == 0
                ? 105 : m.acknowledge(101, 22).transmitted.get(0).sequence);
    }

    private static void receiveAndExtendedAckRemainDistinctEvents() {
        Iap2LinkEngineModel m = model();
        m.start(true, 0);
        becomeReady(m);
        Iap2ReceiveWindowModel.Result gap = m.receive(packet(14), 100);
        bytes(new byte[] {11, 12, 13}, gap.extendedAcknowledgement);
        Iap2ReceiveWindowModel.Result fill = m.receive(packet(11), 101);
        eq(2, fill.delivered.size());
        eq(12, fill.delivered.get(1).sequence);
    }

    private static void bothTimersAdvanceOnSameClock() {
        Iap2LinkEngineModel m = model();
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
