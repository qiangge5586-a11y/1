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
        terminalClosureDiscardsPendingFramesAndEvents();
        negotiatedPeerParametersBuildBothWindows();
        preReadyQueueUsesPeerPayloadLimit();
        readinessAckUpdatesOutboundWindow();
        postSyncFramesDispatchIntoWindows();
        acknowledgementBatchingAndWriteReset();
        preReadyQueueFlushAndPeerPayloadBounds();
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
        eq(0, m.drainWireFrames().size());
        eq(0, m.drainEvents().size());
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

    private static void preReadyQueueUsesPeerPayloadLimit() {
        Iap2LinkEngineModel accepted = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        accepted.start(true, 0);
        accepted.takeNegotiationOutput();
        truth(accepted.queueBeforeReady(10, new byte[] {1}));
        byte[] minSync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                4, 11, 1000, 250, 3, 3, new int[][] {{10, 0, 2}});
        accepted.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, minSync), 1);
        eq(Iap2LinkEngineModel.State.NEGOTIATING, accepted.state());
        accepted.takeNegotiationOutput();
        accepted.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
        eq(Iap2LinkEngineModel.State.READY, accepted.state());
        List<byte[]> wire = accepted.drainWireFrames();
        eq(1, wire.size());
        List<Iap2LinkFrameDecoder.Frame> frames =
                new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, frames.size());
        bytes(new byte[] {1}, frames.get(0).payload);
        // The original NORMAL sendPacket() path does not repeat the pre-ready payload check.
        Iap2OutboundWindowModel.Result postReadyOversize =
                accepted.send(10, new byte[] {2, 3}, 3);
        eq(1, postReadyOversize.transmitted.size());

        Iap2LinkEngineModel rejected = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        rejected.start(true, 0);
        rejected.takeNegotiationOutput();
        truth(rejected.queueBeforeReady(10, new byte[] {1, 2}));
        rejected.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, minSync), 1);
        rejected.takeNegotiationOutput();
        rejected.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
        eq(Iap2LinkEngineModel.State.CLOSED, rejected.state());
        truth(rejected.getTerminalReason().contains("pending control packets"));
        eq(0, rejected.drainWireFrames().size());
    }

    private static void readinessAckUpdatesOutboundWindow() {
        Iap2LinkEngineModel m = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        m.start(true, 0);
        m.takeNegotiationOutput();
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                2, 4096, 1500, 250, 3, 3,
                new int[][] {{10, 0, 2}, {11, 2, 1}});
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, sync), 1);
        m.takeNegotiationOutput();
        // This ACK is within the source maxAcknowledgements + ACK-slack window.
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 77, 111, 0, null), 2);
        eq(Iap2LinkEngineModel.State.READY, m.state());
        List<Iap2LinkEngineModel.DispatchEvent> events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).cumulativeAcknowledgement != null);
        truth(events.get(0).cumulativeAcknowledgement.acknowledgementAccepted);
        // The ACK watermark advances to 111 before the first post-ready send,
        // so sequence 100 remains queued outside the current peer send window.
        Iap2OutboundWindowModel.Result queued = m.send(10, new byte[] {1}, 3);
        eq(0, queued.transmitted.size());
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
        // The SYN-only frame is not a data event; the ACK-bearing frame
        // that completes readiness is retained and then dispatched.
        truth(events.get(0).receivedData != null);
        eq(1, events.get(0).receivedData.delivered.size());
        eq(78, events.get(0).receivedData.delivered.get(0).sequence);
        eq(0, m.drainWireFrames().size());

        Iap2OutboundWindowModel.Result sent = m.send(10, new byte[] {9}, 20);
        eq(1, sent.transmitted.size());
        eq(100, sent.transmitted.get(0).sequence);
        List<byte[]> wire = m.drainWireFrames();
        eq(1, wire.size());
        Iap2LinkFrameDecoder wireDecoder = new Iap2LinkFrameDecoder();
        List<Iap2LinkFrameDecoder.Frame> wireFrames = wireDecoder.feed(wire.get(0));
        eq(1, wireFrames.size());
        eq(0x40, wireFrames.get(0).control);
        eq(100, wireFrames.get(0).sequence);
        eq(78, wireFrames.get(0).acknowledgement);
        eq(10, wireFrames.get(0).sessionId);
        bytes(new byte[] {9}, wireFrames.get(0).payload);
        byte[] extendedAck = Iap2LinkFrameCodec.encode(0x20, 79, 99, 11,
                new byte[] {100});
        m.feedNegotiation(extendedAck, 30);
        events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).extendedAcknowledgement != null);
        truth(events.get(0).cumulativeAcknowledgement == null);
        eq(1, events.get(0).extendedAcknowledgement.retransmitted.size());
        eq(100, events.get(0).extendedAcknowledgement.retransmitted.get(0).sequence);
        wire = m.drainWireFrames();
        eq(1, wire.size());
        wireFrames = new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, wireFrames.size());
        eq(100, wireFrames.get(0).sequence);
        eq(78, wireFrames.get(0).acknowledgement);
        bytes(new byte[] {9}, wireFrames.get(0).payload);

        byte[] ackOnly = Iap2LinkFrameCodec.encode(0x40, 80, 100, 0, null);
        m.feedNegotiation(ackOnly, 40);
        events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).receivedData == null);
        truth(events.get(0).cumulativeAcknowledgement.acknowledgementAccepted);
        eq(0, m.drainWireFrames().size());
        // Data-only frames use control 0x00; they still enter handleData().
        byte[] secondData = Iap2LinkFrameCodec.encode(0x00, 79, 100, 10, new byte[] {7});
        m.feedNegotiation(secondData, 50);
        events = m.drainEvents();
        truth(events.get(0).receivedData != null);
        eq(79, events.get(0).receivedData.delivered.get(0).sequence);
        truth(events.get(0).receivedData.acknowledgementNow);
        wire = m.drainWireFrames();
        eq(1, wire.size());
        wireFrames = new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, wireFrames.size());
        eq(0x40, wireFrames.get(0).control);
        eq(79, wireFrames.get(0).acknowledgement);
        truth(wireFrames.get(0).payload == null);

        // A subsequent ACK+data frame is delivered, then ACKed at its deadline.
        byte[] thirdData = Iap2LinkFrameCodec.encode(0x40, 80, 100, 10, new byte[] {8});
        m.feedNegotiation(thirdData, 60);
        events = m.drainEvents();
        truth(events.get(0).receivedData != null);
        truth(!events.get(0).receivedData.acknowledgementNow);
        eq(0, m.drainWireFrames().size());
        Iap2LinkEngineModel.TimerResult delayedAck = m.advanceTimers(310);
        truth(delayedAck.inbound.acknowledgementNow);
        wire = m.drainWireFrames();
        eq(1, wire.size());
        wireFrames = new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, wireFrames.size());
        eq(0x40, wireFrames.get(0).control);
        eq(80, wireFrames.get(0).acknowledgement);
        truth(wireFrames.get(0).payload == null);

        // The negotiated batch threshold is five; two more ACKs do not trigger it.
        byte[] fourthAck = Iap2LinkFrameCodec.encode(0x40, 81, 100, 0, null);
        m.feedNegotiation(fourthAck, 320);
        events = m.drainEvents();
        truth(events.get(0).acknowledgementBatch == null);
        eq(0, m.drainWireFrames().size());
        byte[] fifthAck = Iap2LinkFrameCodec.encode(0x40, 82, 100, 0, null);
        m.feedNegotiation(fifthAck, 330);
        events = m.drainEvents();
        truth(events.get(0).acknowledgementBatch == null);
        eq(0, m.drainWireFrames().size());
        eq(Iap2LinkEngineModel.State.READY, m.state());
    }

    private static void acknowledgementBatchingAndWriteReset() {
        Iap2LinkEngineModel m = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        m.start(true, 0);
        m.takeNegotiationOutput();
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                4, 4096, 1500, 250, 3, 2,
                new int[][] {{10, 0, 2}, {11, 2, 1}});
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, sync), 1);
        m.takeNegotiationOutput();
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
        eq(Iap2LinkEngineModel.State.READY, m.state());
        List<Iap2LinkEngineModel.DispatchEvent> events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).acknowledgementBatch == null);
        eq(0, m.drainWireFrames().size());

        // Any local writePacket() output resets the accumulated peer-ACK count.
        m.send(10, new byte[] {1}, 3);
        List<byte[]> wire = m.drainWireFrames();
        eq(1, wire.size());
        List<Iap2LinkFrameDecoder.Frame> decoded =
                new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, decoded.size());
        eq(100, decoded.get(0).sequence);

        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 78, 100, 0, null), 4);
        events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).acknowledgementBatch == null);
        eq(0, m.drainWireFrames().size());

        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 79, 100, 0, null), 5);
        events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).acknowledgementBatch != null);
        truth(events.get(0).acknowledgementBatch.acknowledgementNow);
        wire = m.drainWireFrames();
        eq(1, wire.size());
        decoded = new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, decoded.size());
        eq(0x40, decoded.get(0).control);
        eq(77, decoded.get(0).acknowledgement);

        // The ACK emitted by the batch threshold resets the counter again.
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 80, 100, 0, null), 6);
        events = m.drainEvents();
        eq(1, events.size());
        truth(events.get(0).acknowledgementBatch == null);
        eq(0, m.drainWireFrames().size());
    }

    private static void preReadyQueueFlushAndPeerPayloadBounds() {
        Iap2LinkEngineModel queued = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        queued.start(true, 0);
        queued.takeNegotiationOutput();
        byte[] original = new byte[] {1, 2, 3};
        truth(queued.queueBeforeReady(10, original));
        original[0] = 99;
        feedPeerSyncAndAck(queued, 13, 1);
        eq(Iap2LinkEngineModel.State.READY, queued.state());
        List<Iap2LinkEngineModel.DispatchEvent> events = queued.drainEvents();
        eq(1, events.size());
        // The readiness ACK increments the batch count, then queued data
        // writes reset it before processFrame() evaluates the batch threshold.
        truth(events.get(0).acknowledgementBatch == null);
        List<byte[]> wire = queued.drainWireFrames();
        eq(1, wire.size());
        List<Iap2LinkFrameDecoder.Frame> frames =
                new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, frames.size());
        eq(0x40, frames.get(0).control);
        eq(100, frames.get(0).sequence);
        eq(10, frames.get(0).sessionId);
        bytes(new byte[] {1, 2, 3}, frames.get(0).payload);

        Iap2LinkEngineModel oversizedQueued =
                new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        oversizedQueued.start(true, 0);
        oversizedQueued.takeNegotiationOutput();
        truth(oversizedQueued.queueBeforeReady(10, new byte[] {1, 2, 3, 4}));
        feedPeerSyncAndAck(oversizedQueued, 13, 1);
        eq(Iap2LinkEngineModel.State.CLOSED, oversizedQueued.state());
        truth(oversizedQueued.getTerminalReason().contains("maxLength"));
        eq(0, oversizedQueued.drainWireFrames().size());

        Iap2LinkEngineModel oversizedReady =
                new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        oversizedReady.start(true, 0);
        oversizedReady.takeNegotiationOutput();
        feedPeerSyncAndAck(oversizedReady, 13, 5);
        // Smali sendPacket() in NORMAL does not repeat the pre-ready queue size check.
        Iap2OutboundWindowModel.Result postReady =
                oversizedReady.send(10, new byte[] {1, 2, 3, 4}, 3);
        eq(1, postReady.transmitted.size());

        Iap2LinkEngineModel capped = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        capped.start(true, 0);
        for (int i = 0; i < 64; i++) truth(capped.queueBeforeReady(10, new byte[] {(byte) i}));
        truth(!capped.queueBeforeReady(10, new byte[] {65}));
        eq(Iap2LinkEngineModel.State.CLOSED, capped.state());
    }

    private static void feedPeerSyncAndAck(Iap2LinkEngineModel model, int maxLength,
            int maxAcknowledgements) {
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                4, maxLength, 1500, 250, 3, maxAcknowledgements,
                new int[][] {{10, 0, 2}, {11, 2, 1}});
        model.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, sync), 1);
        model.takeNegotiationOutput();
        model.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
    }

    private static void inboundGapEmitsExtendedAckWithSessionZero() {
        Iap2LinkEngineModel m = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        m.start(true, 0);
        m.takeNegotiationOutput();
        feedPeerSyncAndAck(m, 4096, 5);
        m.drainEvents();
        m.drainWireFrames();
        byte[] gap = Iap2LinkFrameCodec.encode(0x00, 80, 99, 10, new byte[] {8});
        m.feedNegotiation(gap, 3);
        List<Iap2LinkEngineModel.DispatchEvent> events = m.drainEvents();
        eq(1, events.size());
        bytes(new byte[] {78, 79}, events.get(0).receivedData.extendedAcknowledgement);
        List<byte[]> wire = m.drainWireFrames();
        eq(1, wire.size());
        List<Iap2LinkFrameDecoder.Frame> frames =
                new Iap2LinkFrameDecoder().feed(wire.get(0));
        eq(1, frames.size());
        eq(0x20, frames.get(0).control);
        eq(0, frames.get(0).sessionId);
        eq(77, frames.get(0).acknowledgement);
        bytes(new byte[] {78, 79}, frames.get(0).payload);
    }


    private static void terminalClosureDiscardsPendingFramesAndEvents() {
        final Iap2LinkEngineModel m = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        m.start(true, 0);
        becomeReady(m);
        m.drainEvents();
        m.send(10, new byte[] {1, 2, 3}, 10);
        eq(1, m.drainWireFrames().size());
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x00, 78, 99, 10,
                new byte[] {4, 5}), 20);
        eq(1, m.drainEvents().size());

        // Queue new output and an undispatched event, then terminate the link.
        m.send(10, new byte[] {6}, 30);
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x00, 79, 99, 10,
                new byte[] {7}), 40);
        m.endOfStream();
        eq(Iap2LinkEngineModel.State.CLOSED, m.state());
        eq(0, m.drainWireFrames().size());
        eq(0, m.drainEvents().size());
        throwsState(new Runnable() { public void run() {
            m.send(10, new byte[] {8}, 50);
        }});
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
        eq(0, after.takeNegotiationOutput().length);
        eq(0, after.drainWireFrames().size());
        eq(0, after.drainEvents().size());
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
        eq(Iap2LinkEngineModel.State.NEGOTIATING, m.state());
        m.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 20, 99, 0, null), 2);
    }

    private static Iap2ReceiveWindowModel.Packet packet(int sequence) {
        return new Iap2ReceiveWindowModel.Packet(sequence, 10,
                new byte[] {(byte) sequence});
    }
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
    private static void throwsIllegalArgument(Runnable r) {
        checks++;
        try { r.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
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
