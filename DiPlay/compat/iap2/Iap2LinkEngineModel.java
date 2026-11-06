package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offline composition boundary for the audited iAP2 link sub-models.
 *
 * This class composes the offline negotiation, outbound-window and receive-window
 * models, dispatches post-SYN frames into those models, and exposes deterministic
 * events. It does not open a transport or write encoded frames to a live peer.
 */
public final class Iap2LinkEngineModel {
    public enum State { IDLE, NEGOTIATING, READY, CLOSED }

    private static final int SOURCE_ACK_SLACK = 10;
    private static final int WIRELESS_MAX_OUT_OF_ORDER = 64;
    private static final int WIRELESS_MAX_QUEUED_PACKETS = 64;
    private static final int WIRELESS_MAX_OUTGOING_DELTA = 0;

    private final Iap2LinkNegotiationModel negotiation;
    private Iap2OutboundWindowModel outbound;
    private Iap2ReceiveWindowModel inbound;
    private final ArrayList<DispatchEvent> pendingEvents = new ArrayList<DispatchEvent>();
    private final ArrayList<byte[]> pendingWireFrames = new ArrayList<byte[]>();
    private final ArrayList<PreReadyPacket> preReadyPackets = new ArrayList<PreReadyPacket>();
    private String localTerminalReason;
    private int accumulatedAcknowledgements;
    private boolean started;
    private boolean closed;

    /** Creates a composition that builds both windows from the peer SYN after readiness. */
    public Iap2LinkEngineModel(Iap2LinkNegotiationModel negotiation) {
        if (negotiation == null) throw new NullPointerException("negotiation");
        this.negotiation = negotiation;
    }

    /** Injection constructor retained for focused deterministic tests. */
    public Iap2LinkEngineModel(Iap2LinkNegotiationModel negotiation,
            Iap2OutboundWindowModel outbound, Iap2ReceiveWindowModel inbound) {
        if (negotiation == null || outbound == null || inbound == null) {
            throw new NullPointerException("link model component");
        }
        this.negotiation = negotiation;
        this.outbound = outbound;
        this.inbound = inbound;
    }

    public synchronized void start(boolean initiator, long nowMillis) {
        if (closed) throw new IllegalStateException("link is closed");
        if (started) return;
        started = true;
        negotiation.start(initiator, nowMillis);
    }

    public synchronized void feedNegotiation(byte[] bytes, long nowMillis) {
        requireStarted();
        if (closed) return;
        negotiation.feed(bytes, nowMillis);
        dispatchPostReadyFrames(nowMillis);
    }

    public synchronized boolean initiateSynchronizationIfDetecting(long nowMillis) {
        requireStarted();
        if (closed) return false;
        return negotiation.initiateSynchronizationIfDetecting(nowMillis);
    }

    public synchronized State advanceNegotiationTime(long nowMillis) {
        requireStarted();
        if (closed) return State.CLOSED;
        negotiation.advanceTime(nowMillis);
        if (negotiation.getState() == Iap2LinkNegotiationModel.State.DEAD) {
            closed = true;
            return State.CLOSED;
        }
        return state();
    }

    /** Queues an application packet before link readiness, matching the original engine queue gate. */
    public synchronized boolean queueBeforeReady(int sessionId, byte[] payload) {
        requireStarted();
        if (payload == null) throw new NullPointerException("payload");
        if (state() == State.READY) throw new IllegalStateException("link is already ready");
        if (state() == State.CLOSED) return false;
        if (sessionId < 0 || sessionId > 255) throw new IllegalArgumentException("sessionId");
        if (preReadyPackets.size() >= WIRELESS_MAX_QUEUED_PACKETS) {
            localTerminalReason = "iAP2 outbound packet queue limit exceeded";
            return false;
        }
        preReadyPackets.add(new PreReadyPacket(sessionId, payload));
        return true;
    }

    public synchronized Iap2OutboundWindowModel.Result send(
            int sessionId, byte[] payload, long nowMillis) {
        requireReady();
        Iap2LinkNegotiationModel.PeerConfiguration peer = negotiation.getPeerConfiguration();
        if (peer == null || !peer.acceptsPayload(payload)) {
            throw new IllegalArgumentException("payload exceeds peer maxLength - 10");
        }
        Iap2OutboundWindowModel.Result result = outbound.send(sessionId, payload, nowMillis);
        enqueuePacketFrames(result.transmitted);
        return result;
    }

    public synchronized Iap2OutboundWindowModel.Result acknowledge(
            int acknowledgement, long nowMillis) {
        requireReady();
        Iap2OutboundWindowModel.Result result = outbound.acknowledge(acknowledgement, nowMillis);
        enqueuePacketFrames(result.transmitted);
        return result;
    }

    public synchronized Iap2OutboundWindowModel.Result requestMissing(
            byte[] missingSequences, long nowMillis) {
        requireReady();
        Iap2OutboundWindowModel.Result result = outbound.requestMissing(missingSequences, nowMillis);
        enqueuePacketFrames(result.retransmitted);
        return result;
    }

    public synchronized Iap2ReceiveWindowModel.Result receive(
            Iap2ReceiveWindowModel.Packet packet, long nowMillis) {
        requireReady();
        Iap2ReceiveWindowModel.Result result = inbound.receive(packet, nowMillis);
        enqueueInboundResponse(result);
        return result;
    }

    /** Advances both independent timers at one deterministic clock instant. */
    public synchronized TimerResult advanceTimers(long nowMillis) {
        requireReady();
        Iap2OutboundWindowModel.Result outboundResult = outbound.advanceTime(nowMillis);
        Iap2ReceiveWindowModel.Result inboundResult = inbound.advanceTime(nowMillis);
        enqueuePacketFrames(outboundResult.retransmitted);
        enqueueInboundResponse(inboundResult);
        return new TimerResult(outboundResult, inboundResult);
    }

    public synchronized void endOfStream() {
        if (closed) return;
        closed = true;
        if (started) negotiation.feedEof();
    }

    public synchronized State state() {
        if (closed || localTerminalReason != null
                || negotiation.getState() == Iap2LinkNegotiationModel.State.DEAD
                || (outbound != null && outbound.getTerminalReason() != null)
                || (inbound != null && inbound.getTerminalReason() != null)) {
            return State.CLOSED;
        }
        if (negotiation.isReady()) return State.READY;
        if (started) return State.NEGOTIATING;
        return State.IDLE;
    }

    public synchronized byte[] takeNegotiationOutput() {
        return negotiation.takeOutput();
    }

    public synchronized String getTerminalReason() {
        if (localTerminalReason != null) return localTerminalReason;
        if (outbound != null && outbound.getTerminalReason() != null) return outbound.getTerminalReason();
        if (inbound != null && inbound.getTerminalReason() != null) return inbound.getTerminalReason();
        return negotiation.getTerminalReason();
    }

    public synchronized Iap2LinkNegotiationModel.PeerConfiguration
            getNegotiatedPeerConfiguration() {
        return negotiation.getPeerConfiguration();
    }

    private void dispatchPostReadyFrames(long nowMillis) {
        if (!negotiation.isReady()) return;
        ensureWindowModels();
        List<Iap2LinkNegotiationModel.ReadyFrame> frames = negotiation.drainPostReadyFrames();
        boolean preReadyFlushed = false;
        for (int i = 0; i < frames.size(); i++) {
            if (pendingEvents.size() >= 256) {
                localTerminalReason = "iAP2 pending dispatch event limit exceeded";
                return;
            }
            Iap2LinkNegotiationModel.ReadyFrame readyFrame = frames.get(i);
            Iap2LinkFrameDecoder.Frame frame = readyFrame.frame;
            Iap2OutboundWindowModel.Result cumulativeAck = null;
            Iap2OutboundWindowModel.Result extendedAck = null;
            Iap2ReceiveWindowModel.Result received = null;
            Iap2ReceiveWindowModel.Result acknowledgementBatch = null;
            boolean carriesAck = (frame.control
                    & Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT) != 0;
            if (carriesAck) {
                accumulatedAcknowledgements++;
                // Negotiation handled the readiness gate, but the composed
                // outbound window is separate and must apply this ACK once.
                cumulativeAck = outbound.acknowledge(frame.acknowledgement, nowMillis);
                enqueuePacketFrames(cumulativeAck.transmitted);
            }
            if (!preReadyFlushed) {
                // Original handleAcknowledgement() applies the ACK and drains
                // writable packets before processFrame() checks ACK batching.
                flushPreReadyPackets(nowMillis);
                preReadyFlushed = true;
                if (localTerminalReason != null) return;
            }
            if ((frame.control & Iap2LinkFrameCodec.CONTROL_EXTENDED_ACKNOWLEDGEMENT) != 0
                    && frame.payload != null) {
                extendedAck = outbound.requestMissing(frame.payload, nowMillis);
                enqueuePacketFrames(extendedAck.retransmitted);
            }
            // Original processFrame(): (control & ~0x40) == 0 and payload != null.
            // This permits both data-only (0x00) and data+ACK (0x40) frames.
            if ((frame.control & ~Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT) == 0
                    && frame.payload != null) {
                received = inbound.receive(new Iap2ReceiveWindowModel.Packet(
                        frame.sequence, frame.sessionId, frame.payload), nowMillis);
                enqueueInboundResponse(received);
            }
            Iap2LinkNegotiationModel.PeerConfiguration peer =
                    negotiation.getPeerConfiguration();
            if (peer != null && peer.maxAcknowledgements > 0
                    && accumulatedAcknowledgements >= peer.maxAcknowledgements) {
                accumulatedAcknowledgements = 0;
                acknowledgementBatch = inbound.forceAcknowledgement();
                enqueueInboundResponse(acknowledgementBatch);
            }
            pendingEvents.add(new DispatchEvent(frame, cumulativeAck, extendedAck,
                    received, acknowledgementBatch));
        }
    }

    /** Drains encoded link frames generated by the offline model; no transport write occurs. */
    public synchronized List<byte[]> drainWireFrames() {
        ArrayList<byte[]> frames = new ArrayList<byte[]>(pendingWireFrames.size());
        for (int i = 0; i < pendingWireFrames.size(); i++) {
            byte[] source = pendingWireFrames.get(i);
            byte[] copy = new byte[source.length];
            System.arraycopy(source, 0, copy, 0, source.length);
            frames.add(copy);
        }
        pendingWireFrames.clear();
        return Collections.unmodifiableList(frames);
    }

    private void enqueuePacketFrames(List<Iap2OutboundWindowModel.Packet> packets) {
        for (int i = 0; i < packets.size(); i++) {
            Iap2OutboundWindowModel.Packet packet = packets.get(i);
            enqueueWireFrame(Iap2LinkFrameCodec.encode(
                    Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT, packet.sequence,
                    inbound.getLastReceivedInOrder(), packet.sessionId, packet.payload));
        }
    }

    private void enqueueInboundResponse(Iap2ReceiveWindowModel.Result result) {
        if (result.extendedAcknowledgement != null) {
            enqueueWireFrame(Iap2LinkFrameCodec.encode(
                    Iap2LinkFrameCodec.CONTROL_EXTENDED_ACKNOWLEDGEMENT,
                    outbound.getSentSequence(), inbound.getLastReceivedInOrder(),
                    Iap2LinkFrameCodec.EA_SESSION_ID, result.extendedAcknowledgement));
        } else if (result.acknowledgementNow) {
            enqueueWireFrame(Iap2LinkFrameCodec.encode(
                    Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT,
                    outbound.getSentSequence(), inbound.getLastReceivedInOrder(), 0, null));
        }
    }

    private void enqueueWireFrame(byte[] frame) {
        // Original writePacket() resets the ACK batch counter on every emitted frame.
        accumulatedAcknowledgements = 0;
        if (pendingWireFrames.size() >= 256) {
            localTerminalReason = "iAP2 pending wire-frame queue limit exceeded";
            return;
        }
        pendingWireFrames.add(frame);
    }

    /** Drains parsed post-SYN link-frame outcomes in wire order. */
    public synchronized List<DispatchEvent> drainEvents() {
        ArrayList<DispatchEvent> events = new ArrayList<DispatchEvent>(pendingEvents);
        pendingEvents.clear();
        return Collections.unmodifiableList(events);
    }

    public static final class DispatchEvent {
        public final Iap2LinkFrameDecoder.Frame frame;
        public final Iap2OutboundWindowModel.Result cumulativeAcknowledgement;
        public final Iap2OutboundWindowModel.Result extendedAcknowledgement;
        public final Iap2ReceiveWindowModel.Result receivedData;
        public final Iap2ReceiveWindowModel.Result acknowledgementBatch;
        private DispatchEvent(Iap2LinkFrameDecoder.Frame frame,
                Iap2OutboundWindowModel.Result cumulativeAcknowledgement,
                Iap2OutboundWindowModel.Result extendedAcknowledgement,
                Iap2ReceiveWindowModel.Result receivedData,
                Iap2ReceiveWindowModel.Result acknowledgementBatch) {
            this.frame = frame;
            this.cumulativeAcknowledgement = cumulativeAcknowledgement;
            this.extendedAcknowledgement = extendedAcknowledgement;
            this.receivedData = receivedData;
            this.acknowledgementBatch = acknowledgementBatch;
        }
    }

    private void flushPreReadyPackets(long nowMillis) {
        if (preReadyPackets.isEmpty()) return;
        Iap2LinkNegotiationModel.PeerConfiguration peer = negotiation.getPeerConfiguration();
        for (int i = 0; i < preReadyPackets.size(); i++) {
            if (!peer.acceptsPayload(preReadyPackets.get(i).payload)) {
                localTerminalReason = "peer iAP2 maxLength cannot carry pending control packets";
                preReadyPackets.clear();
                return;
            }
        }
        for (int i = 0; i < preReadyPackets.size(); i++) {
            PreReadyPacket packet = preReadyPackets.get(i);
            Iap2OutboundWindowModel.Result result =
                    outbound.send(packet.sessionId, packet.payload, nowMillis);
            enqueuePacketFrames(result.transmitted);
            if (result.terminalReason != null) {
                localTerminalReason = result.terminalReason;
                preReadyPackets.clear();
                return;
            }
        }
        preReadyPackets.clear();
    }

    private static final class PreReadyPacket {
        final int sessionId;
        final byte[] payload;
        PreReadyPacket(int sessionId, byte[] payload) {
            this.sessionId = sessionId;
            this.payload = new byte[payload.length];
            System.arraycopy(payload, 0, this.payload, 0, payload.length);
        }
    }

    private void ensureWindowModels() {
        if (outbound != null && inbound != null) return;
        Iap2LinkNegotiationModel.PeerConfiguration peer = negotiation.getPeerConfiguration();
        if (peer == null) throw new IllegalStateException("peer SYN configuration is unavailable");
        int receiveSequence = peer.synchronizationSequence;
        outbound = new Iap2OutboundWindowModel(Iap2LinkFrameCodec.INITIAL_SEQUENCE,
                Integer.valueOf(Iap2LinkFrameCodec.INITIAL_SEQUENCE), peer.maxOutgoing,
                peer.maxAcknowledgements, SOURCE_ACK_SLACK, peer.retransmissionTimeoutMillis,
                peer.maxRetransmissions, WIRELESS_MAX_QUEUED_PACKETS);
        inbound = new Iap2ReceiveWindowModel(receiveSequence, receiveSequence,
                peer.maxOutgoing, peer.maxAcknowledgements,
                peer.acknowledgementTimeoutMillis, WIRELESS_MAX_OUTGOING_DELTA,
                WIRELESS_MAX_OUT_OF_ORDER);
    }

    private void requireStarted() {
        if (!started) throw new IllegalStateException("link has not been started");
    }

    private void requireReady() {
        if (state() != State.READY) {
            throw new IllegalStateException("iAP2 link is not ready: " + state());
        }
        ensureWindowModels();
    }

    public static final class TimerResult {
        public final Iap2OutboundWindowModel.Result outbound;
        public final Iap2ReceiveWindowModel.Result inbound;
        private TimerResult(Iap2OutboundWindowModel.Result outbound,
                Iap2ReceiveWindowModel.Result inbound) {
            this.outbound = outbound;
            this.inbound = inbound;
        }
    }
}
