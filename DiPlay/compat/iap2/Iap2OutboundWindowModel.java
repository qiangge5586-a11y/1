package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic offline model of the source Iap2LinkEngine outbound packet
 * window, cumulative ACK queue drain and retransmission deadline handling.
 * No sockets, Android APIs or transport I/O are performed.
 */
public final class Iap2OutboundWindowModel {
    public static final class Packet {
        public final int sequence;
        public final int sessionId;
        public final byte[] payload;
        public final int retryCount;
        public final long deadlineMillis;

        private Packet(int sequence, int sessionId, byte[] payload,
                int retryCount, long deadlineMillis) {
            this.sequence = sequence;
            this.sessionId = sessionId;
            this.payload = copy(payload);
            this.retryCount = retryCount;
            this.deadlineMillis = deadlineMillis;
        }
    }

    public static final class Result {
        public final List<Packet> transmitted;
        public final List<Packet> retransmitted;
        public final List<Integer> acknowledged;
        public final boolean acknowledgementAccepted;
        public final String terminalReason;

        private Result(List<Packet> sent, List<Packet> retried, List<Integer> acked,
                boolean accepted, String terminal) {
            transmitted = immutablePackets(sent);
            retransmitted = immutablePackets(retried);
            acknowledged = Collections.unmodifiableList(new ArrayList<Integer>(acked));
            acknowledgementAccepted = accepted;
            terminalReason = terminal;
        }
    }

    private final int peerMaxOutgoing;
    private final int peerMaxAcknowledgements;
    private final int ackSlack;
    private final int retransmissionTimeoutMillis;
    private final int peerMaxRetransmissions;
    private final int maxQueuedPackets;
    private final ArrayList<Packet> unacknowledged = new ArrayList<Packet>();
    private final ArrayList<Packet> queued = new ArrayList<Packet>();
    private int sentSequence;
    private Integer lastSentAcknowledged;
    private String terminalReason;

    public Iap2OutboundWindowModel(int initialSentSequence, Integer initialAcknowledgement,
            int peerMaxOutgoing, int peerMaxAcknowledgements, int ackSlack,
            int retransmissionTimeoutMillis, int peerMaxRetransmissions,
            int maxQueuedPackets) {
        checkByte(initialSentSequence, "initialSentSequence");
        checkNullableByte(initialAcknowledgement, "initialAcknowledgement");
        checkByte(peerMaxOutgoing, "peerMaxOutgoing");
        checkByte(peerMaxAcknowledgements, "peerMaxAcknowledgements");
        checkByte(ackSlack, "ackSlack");
        checkU16(retransmissionTimeoutMillis, "retransmissionTimeoutMillis");
        checkByte(peerMaxRetransmissions, "peerMaxRetransmissions");
        if (maxQueuedPackets < 0) throw new IllegalArgumentException("maxQueuedPackets");
        this.sentSequence = initialSentSequence;
        this.lastSentAcknowledged = initialAcknowledgement;
        this.peerMaxOutgoing = peerMaxOutgoing;
        this.peerMaxAcknowledgements = peerMaxAcknowledgements;
        this.ackSlack = ackSlack;
        this.retransmissionTimeoutMillis = retransmissionTimeoutMillis;
        this.peerMaxRetransmissions = peerMaxRetransmissions;
        this.maxQueuedPackets = maxQueuedPackets;
    }

    /** Mirrors sendPacket(): queue only when current distance is strictly > maxOutgoing. */
    public synchronized Result send(int sessionId, byte[] payload, long nowMillis) {
        checkByte(sessionId, "sessionId");
        if (payload == null) throw new NullPointerException("payload");
        if (terminalReason != null) return empty(false);
        if (Iap2AckWindowPolicy.shouldQueueNewPacket(sentSequence,
                lastSentAcknowledged, peerMaxOutgoing)) {
            if (queued.size() >= maxQueuedPackets) {
                terminalReason = "iAP2 outbound packet queue limit exceeded";
                return empty(false);
            }
            queued.add(new Packet(Iap2LinkFrameCodec.nextSequence(sentSequence),
                    sessionId, payload, 0, 0));
            return empty(false);
        }
        ArrayList<Packet> sent = new ArrayList<Packet>();
        sent.add(transmit(sessionId, payload, nowMillis));
        return result(sent, new ArrayList<Packet>(), new ArrayList<Integer>(), false);
    }

    /** Accepts only a forward cumulative ACK inside the source maxAcknowledgements+slack window. */
    public synchronized Result acknowledge(int acknowledgement, long nowMillis) {
        checkByte(acknowledgement, "acknowledgement");
        if (terminalReason != null) return empty(false);
        // Source Iap2LinkEngine.handleAcknowledgement() accepts the received
        // u8 ACK watermark directly; it does not apply a forward-window validator.
        lastSentAcknowledged = Integer.valueOf(acknowledgement);
        ArrayList<Integer> removed = new ArrayList<Integer>();
        // The source walks from the head and stops at the first packet still
        // inside maxAcknowledgements + ACK_SLACK. It does not scan later entries.
        while (!unacknowledged.isEmpty()) {
            Packet first = unacknowledged.get(0);
            if (Iap2AckWindowPolicy.remainsUnacknowledged(first.sequence,
                    acknowledgement, peerMaxAcknowledgements)) break;
            removed.add(Integer.valueOf(first.sequence));
            unacknowledged.remove(0);
        }
        ArrayList<Packet> sent = drainQueued(nowMillis);
        return result(sent, new ArrayList<Packet>(), removed, true);
    }

    /** Selects the earliest due packet and applies the original increment-then-equality retry rule. */
    public synchronized Result advanceTime(long nowMillis) {
        if (terminalReason != null || unacknowledged.isEmpty()) return empty(false);
        Packet earliest = null;
        for (int i = 0; i < unacknowledged.size(); i++) {
            Packet p = unacknowledged.get(i);
            if (p.deadlineMillis <= nowMillis
                    && (earliest == null || p.deadlineMillis < earliest.deadlineMillis)) {
                earliest = p;
            }
        }
        if (earliest == null) return empty(false);
        int nextRetry = earliest.retryCount + 1;
        if (nextRetry > 255) nextRetry = 255;
        Packet updated = new Packet(earliest.sequence, earliest.sessionId, earliest.payload,
                nextRetry, nowMillis + retransmissionTimeoutMillis);
        replace(earliest, updated);
        if (nextRetry == peerMaxRetransmissions) {
            terminalReason = "iAP2 packet retransmission limit reached";
            return result(new ArrayList<Packet>(), new ArrayList<Packet>(),
                    new ArrayList<Integer>(), false);
        }
        ArrayList<Packet> retried = new ArrayList<Packet>();
        retried.add(updated);
        return result(new ArrayList<Packet>(), retried, new ArrayList<Integer>(), false);
    }

    /** Retransmits only outstanding packets whose sequence occurs in the raw Extended ACK list. */
    public synchronized Result requestMissing(byte[] missingSequences, long nowMillis) {
        if (missingSequences == null) throw new NullPointerException("missingSequences");
        if (terminalReason != null) return empty(false);
        ArrayList<Packet> retried = new ArrayList<Packet>();
        for (int i = 0; i < unacknowledged.size(); i++) {
            Packet p = unacknowledged.get(i);
            if (!Iap2RetransmissionPolicy.isSequenceRequested(p.sequence, missingSequences)) {
                continue;
            }
            int nextRetry = p.retryCount + 1;
            if (nextRetry > 255) nextRetry = 255;
            // Extended ACK retransmission preserves the packet's original deadline.
            // The source engine rearms its receive-ACK timer from that same deadline.
            Packet updated = new Packet(p.sequence, p.sessionId, p.payload,
                    nextRetry, p.deadlineMillis);
            unacknowledged.set(i, updated);
            if (nextRetry == peerMaxRetransmissions) {
                terminalReason = "iAP2 packet retransmission limit reached";
                break;
            }
            retried.add(updated);
        }
        return result(new ArrayList<Packet>(), retried, new ArrayList<Integer>(), false);
    }

    private Packet transmit(int sessionId, byte[] payload, long nowMillis) {
        sentSequence = Iap2LinkFrameCodec.nextSequence(sentSequence);
        if (peerMaxRetransmissions == 0) {
            return new Packet(sentSequence, sessionId, payload, 0, 0);
        }
        Packet packet = new Packet(sentSequence, sessionId, payload, 0,
                nowMillis + retransmissionTimeoutMillis);
        unacknowledged.add(packet);
        return packet;
    }

    private ArrayList<Packet> drainQueued(long nowMillis) {
        ArrayList<Packet> sent = new ArrayList<Packet>();
        while (!queued.isEmpty() && Iap2AckWindowPolicy.mayDrainQueuedPacket(
                sentSequence, lastSentAcknowledged, peerMaxOutgoing)) {
            Packet p = queued.remove(0);
            sent.add(transmit(p.sessionId, p.payload, nowMillis));
        }
        return sent;
    }

    private void replace(Packet oldPacket, Packet newPacket) {
        for (int i = 0; i < unacknowledged.size(); i++) {
            if (unacknowledged.get(i) == oldPacket) {
                unacknowledged.set(i, newPacket);
                return;
            }
        }
    }

    private Result empty(boolean accepted) {
        return result(new ArrayList<Packet>(), new ArrayList<Packet>(),
                new ArrayList<Integer>(), accepted);
    }
    private Result result(List<Packet> sent, List<Packet> retried,
            List<Integer> acked, boolean accepted) {
        return new Result(sent, retried, acked, accepted, terminalReason);
    }
    private static List<Packet> immutablePackets(List<Packet> packets) {
        return Collections.unmodifiableList(new ArrayList<Packet>(packets));
    }

    /** Earliest outstanding retransmission deadline, or null when none remain. */
    public synchronized Long getNextDeadlineMillis() {
        if (unacknowledged.isEmpty()) return null;
        long earliest = Long.MAX_VALUE;
        for (int i = 0; i < unacknowledged.size(); i++) {
            if (unacknowledged.get(i).deadlineMillis < earliest) {
                earliest = unacknowledged.get(i).deadlineMillis;
            }
        }
        return Long.valueOf(earliest);
    }

    public synchronized int getSentSequence() { return sentSequence; }
    public synchronized Integer getLastSentAcknowledged() { return lastSentAcknowledged; }
    public synchronized int getUnacknowledgedCount() { return unacknowledged.size(); }
    public synchronized int getQueuedCount() { return queued.size(); }
    public synchronized String getTerminalReason() { return terminalReason; }
    public synchronized List<Packet> getUnacknowledgedSnapshot() {
        return immutablePackets(unacknowledged);
    }

    private static void checkNullableByte(Integer value, String name) {
        if (value != null) checkByte(value.intValue(), name);
    }
    private static void checkByte(int value, String name) {
        if (value < 0 || value > 255) throw new IllegalArgumentException(name);
    }
    private static void checkU16(int value, String name) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException(name);
    }
    private static byte[] copy(byte[] bytes) {
        byte[] out = new byte[bytes.length];
        System.arraycopy(bytes, 0, out, 0, bytes.length);
        return out;
    }
}
