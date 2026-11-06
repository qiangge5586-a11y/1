package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Source-derived offline receive-window model for Iap2LinkEngine.handleData(). */
public final class Iap2ReceiveWindowModel {
    public static final class Packet {
        public final int sequence;
        public final int sessionId;
        public final byte[] payload;
        public Packet(int sequence, int sessionId, byte[] payload) {
            checkByte(sequence, "sequence");
            checkByte(sessionId, "sessionId");
            if (payload == null) throw new NullPointerException("payload");
            this.sequence = sequence;
            this.sessionId = sessionId;
            this.payload = copy(payload);
        }
    }

    public static final class Result {
        public final boolean acknowledgementNow;
        public final byte[] extendedAcknowledgement;
        public final List<Packet> delivered;
        public final Long acknowledgementDeadlineMillis;
        public final String terminalReason;
        private Result(boolean ack, byte[] extended, List<Packet> delivered,
                Long deadline, String terminal) {
            acknowledgementNow = ack;
            extendedAcknowledgement = extended == null ? null : copy(extended);
            this.delivered = Collections.unmodifiableList(new ArrayList<Packet>(delivered));
            acknowledgementDeadlineMillis = deadline;
            terminalReason = terminal;
        }
    }

    private final int peerMaxOutgoing, peerMaxAcknowledgements;
    private final int peerAcknowledgementTimeoutMillis, maxOutgoingDelta;
    private final int maximumOutOfOrderPackets;
    private final ArrayList<Packet> outOfOrder = new ArrayList<Packet>();
    private int lastReceivedInOrder, lastAcknowledgedReceived;
    private Long acknowledgementDeadlineMillis;
    private String terminalReason;

    public Iap2ReceiveWindowModel(int lastReceivedInOrder, int lastAcknowledgedReceived,
            int peerMaxOutgoing, int peerMaxAcknowledgements,
            int peerAcknowledgementTimeoutMillis, int maxOutgoingDelta,
            int maximumOutOfOrderPackets) {
        checkByte(lastReceivedInOrder, "lastReceivedInOrder");
        checkByte(lastAcknowledgedReceived, "lastAcknowledgedReceived");
        checkByte(peerMaxOutgoing, "peerMaxOutgoing");
        checkByte(peerMaxAcknowledgements, "peerMaxAcknowledgements");
        checkU16(peerAcknowledgementTimeoutMillis, "peerAcknowledgementTimeoutMillis");
        checkByte(maxOutgoingDelta, "maxOutgoingDelta");
        if (maximumOutOfOrderPackets < 0) throw new IllegalArgumentException("maximumOutOfOrderPackets");
        this.lastReceivedInOrder = lastReceivedInOrder;
        this.lastAcknowledgedReceived = lastAcknowledgedReceived;
        this.peerMaxOutgoing = peerMaxOutgoing;
        this.peerMaxAcknowledgements = peerMaxAcknowledgements;
        this.peerAcknowledgementTimeoutMillis = peerAcknowledgementTimeoutMillis;
        this.maxOutgoingDelta = maxOutgoingDelta;
        this.maximumOutOfOrderPackets = maximumOutOfOrderPackets;
    }

    /** Mirrors source handleData() and returns deterministic events/actions. */
    public synchronized Result receive(Packet packet, long nowMillis) {
        if (packet == null) throw new NullPointerException("packet");
        if (terminalReason != null) return result(false, null, new ArrayList<Packet>());
        int distance = distance(packet.sequence, lastReceivedInOrder);
        if (distance == 0 || distance > peerMaxOutgoing + 10) {
            // Source sends an ACK here but does not advance lastAcknowledgedReceived
            // or cancel an already scheduled delayed-ACK deadline.
            return result(true, null, new ArrayList<Packet>());
        }
        if (contains(packet.sequence)) return result(false, null, new ArrayList<Packet>());
        if (outOfOrder.size() >= maximumOutOfOrderPackets) {
            terminalReason = "iAP2 out-of-order packet limit exceeded";
            return result(false, null, new ArrayList<Packet>());
        }
        outOfOrder.add(packet);

        if (distance > 1 && distance >= peerMaxOutgoing) {
            ArrayList<Byte> missing = new ArrayList<Byte>();
            int sequence = lastReceivedInOrder;
            while (distance(packet.sequence, sequence) > 1) {
                sequence = next(sequence);
                missing.add(Byte.valueOf((byte) sequence));
            }
            acknowledgementDeadlineMillis = null;
            byte[] payload = new byte[missing.size()];
            for (int i = 0; i < missing.size(); i++) payload[i] = missing.get(i).byteValue();
            return result(false, payload, new ArrayList<Packet>());
        }

        sortOutOfOrder();
        ArrayList<Packet> delivered = new ArrayList<Packet>();
        while (!outOfOrder.isEmpty()
                && distance(outOfOrder.get(0).sequence, lastReceivedInOrder) == 1) {
            Packet inOrder = outOfOrder.remove(0);
            lastReceivedInOrder = inOrder.sequence;
            delivered.add(inOrder);
        }
        if (peerMaxAcknowledgements == 0) return result(false, null, delivered);
        int windowBeforeForcedAck = Math.max(peerMaxOutgoing - maxOutgoingDelta, 1);
        if (distance(lastReceivedInOrder, lastAcknowledgedReceived) >= windowBeforeForcedAck) {
            lastAcknowledgedReceived = lastReceivedInOrder;
            acknowledgementDeadlineMillis = null;
            return result(true, null, delivered);
        }
        acknowledgementDeadlineMillis = Long.valueOf(nowMillis + peerAcknowledgementTimeoutMillis);
        return result(false, null, delivered);
    }

    /** Applies the original processFrame() ACK-batching threshold immediately. */
    public synchronized Result forceAcknowledgement() {
        if (terminalReason != null) return result(false, null, new ArrayList<Packet>());
        return acknowledgeNow();
    }

    public synchronized Result advanceTime(long nowMillis) {
        if (terminalReason != null || acknowledgementDeadlineMillis == null
                || acknowledgementDeadlineMillis.longValue() > nowMillis) {
            return result(false, null, new ArrayList<Packet>());
        }
        return acknowledgeNow();
    }

    private Result acknowledgeNow() {
        acknowledgementDeadlineMillis = null;
        lastAcknowledgedReceived = lastReceivedInOrder;
        return result(true, null, new ArrayList<Packet>());
    }
    private Result result(boolean ack, byte[] extended, List<Packet> delivered) {
        return new Result(ack, extended, delivered, acknowledgementDeadlineMillis, terminalReason);
    }
    private boolean contains(int sequence) {
        for (int i = 0; i < outOfOrder.size(); i++)
            if (outOfOrder.get(i).sequence == sequence) return true;
        return false;
    }
    private void sortOutOfOrder() {
        Collections.sort(outOfOrder, new Comparator<Packet>() {
            public int compare(Packet a, Packet b) {
                int da = distance(a.sequence, lastReceivedInOrder);
                int db = distance(b.sequence, lastReceivedInOrder);
                return da < db ? -1 : (da == db ? 0 : 1);
            }
        });
    }
    public synchronized int getLastReceivedInOrder() { return lastReceivedInOrder; }
    public synchronized int getLastAcknowledgedReceived() { return lastAcknowledgedReceived; }
    public synchronized int getOutOfOrderCount() { return outOfOrder.size(); }
    public synchronized Long getAcknowledgementDeadlineMillis() { return acknowledgementDeadlineMillis; }
    public synchronized String getTerminalReason() { return terminalReason; }

    private static int distance(int sequence, int previous) { return (sequence - previous) & 0xff; }
    private static int next(int sequence) { return (sequence + 1) & 0xff; }
    private static void checkByte(int value, String name) {
        if (value < 0 || value > 255) throw new IllegalArgumentException(name);
    }
    private static void checkU16(int value, String name) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException(name);
    }
    private static byte[] copy(byte[] source) {
        byte[] out = new byte[source.length];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }
}
