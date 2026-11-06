package com.shilapi.xcertplay.compat.iap2;

import java.util.List;

public final class Iap2LinkNegotiationModelTest {
    private static int checks;

    public static void main(String[] args) {
        wirelessResponderNegotiatesAfterPeerMarker();
        initiatorStartsSyncImmediately();
        markerResendsWhileDetecting();
        synchronizationRetriesAndTerminatesAtSourceBoundary();
        acknowledgementNumberDoesNotGateNegotiationReady();
        synOnlyDoesNotCompleteReadiness();
        acknowledgementBeforePeerSyncIsTerminal();
        tooSmallPeerLimitIsTerminalOnAcknowledgement();
        exposesImmutablePeerSynchronizationSnapshot();
        rejectsMalformedSynchronizationPayloads();
        preservesPeerSynchronizationBoundaryValues();
        rejectsWrongStartupMarker();
        eofTerminatesSession();
        System.out.println("Iap2LinkNegotiationModelTest: PASS (" + checks + " checks)");
    }

    private static void wirelessResponderNegotiatesAfterPeerMarker() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(false, 0);
        eq(Iap2LinkNegotiationModel.State.DETECTING, model.getState());
        bytes(Iap2LinkFrameCodec.IAP2_MARKER, model.takeOutput());

        model.feed(slice(Iap2LinkFrameCodec.IAP2_MARKER, 0, 2), 100);
        eq(Iap2LinkNegotiationModel.State.DETECTING, model.getState());
        eq(0, model.takeOutput().length);

        byte[] peerSync = peerSyncFrame(77, 65535);
        model.feed(concat(slice(Iap2LinkFrameCodec.IAP2_MARKER, 2, 6), peerSync), 120);
        eq(Iap2LinkNegotiationModel.State.NEGOTIATING, model.getState());
        eq(65535, model.getPeerMaxLength());

        Iap2LinkFrameDecoder decoder = new Iap2LinkFrameDecoder();
        List<Iap2LinkFrameDecoder.Frame> response = decoder.feed(model.takeOutput());
        eq(2, response.size());
        eq(Iap2LinkFrameCodec.CONTROL_SYNCHRONIZE, response.get(0).control);
        int[] local = Iap2LinkFrameCodec.decodeSynchronizationPayload(response.get(0).payload);
        notNull(local);
        eq(4, local[0]); eq(65535, local[1]); eq(4000, local[2]); eq(500, local[3]);
        eq(4, local[4]); eq(3, local[5]);
        eq(2, local[8]); // control session version in descriptor (id 10, kind 0, version 2)
        eq(Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT, response.get(1).control);
        eq(77, response.get(1).acknowledgement);

        model.feed(Iap2LinkFrameCodec.encode(Iap2LinkFrameCodec.CONTROL_ACKNOWLEDGEMENT,
                77, 98, 0, null), 140);
        eq(Iap2LinkNegotiationModel.State.NORMAL, model.getState());
        truth(model.isReady());
        eq(0, model.getPendingFrameBytes());
    }

    private static void initiatorStartsSyncImmediately() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(true, 10);
        eq(Iap2LinkNegotiationModel.State.NEGOTIATING, model.getState());
        byte[] output = model.takeOutput();
        truth(startsWith(output, Iap2LinkFrameCodec.IAP2_MARKER));
        Iap2LinkFrameDecoder decoder = new Iap2LinkFrameDecoder();
        List<Iap2LinkFrameDecoder.Frame> frames = decoder.feed(slice(output, 6, output.length));
        eq(1, frames.size());
        eq(0x80, frames.get(0).control);
        eq(99, frames.get(0).sequence);
        eq(0, frames.get(0).acknowledgement);
    }

    private static void markerResendsWhileDetecting() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(false, 0);
        model.takeOutput();
        model.advanceTime(999);
        eq(0, model.takeOutput().length);
        model.advanceTime(1000);
        bytes(Iap2LinkFrameCodec.IAP2_MARKER, model.takeOutput());
        model.advanceTime(2000);
        bytes(Iap2LinkFrameCodec.IAP2_MARKER, model.takeOutput());
    }

    private static void synchronizationRetriesAndTerminatesAtSourceBoundary() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(true, 0);
        model.takeOutput(); // startup marker + initial SYN
        for (int i = 1; i <= 20; i++) {
            model.advanceTime(i * 500L);
            eq(i, model.getSyncAttempts());
            truth(model.getState() == Iap2LinkNegotiationModel.State.NEGOTIATING);
            truth(model.takeOutput().length > 0);
        }
        model.advanceTime(10500);
        eq(21, model.getSyncAttempts());
        eq(Iap2LinkNegotiationModel.State.DEAD, model.getState());
        truth(model.getTerminalReason().contains("after 21 attempts"));
    }

    private static void acknowledgementNumberDoesNotGateNegotiationReady() {
        int[] acknowledgements = new int[] { 98, 100 };
        for (int i = 0; i < acknowledgements.length; i++) {
            Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
            model.start(true, 0);
            model.takeOutput();
            model.feed(peerSyncFrame(77, 65535, acknowledgements[i]), 1);
            model.takeOutput();
            eq(Iap2LinkNegotiationModel.State.NORMAL, model.getState());
            model.feed(Iap2LinkFrameCodec.encode(0x40, 77, acknowledgements[i], 0, null), 2);
            eq(Iap2LinkNegotiationModel.State.NORMAL, model.getState());
            truth(model.isReady());
        }
    }

    private static void synOnlyDoesNotCompleteReadiness() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(true, 0);
        model.takeOutput();
        model.feed(peerSyncFrame(77, 65535), 1);
        eq(Iap2LinkNegotiationModel.State.NEGOTIATING, model.getState());
        truth(model.getPeerConfiguration() != null);
        model.feed(Iap2LinkFrameCodec.encode(0x40, 77, 0, 0, null), 2);
        eq(Iap2LinkNegotiationModel.State.NORMAL, model.getState());
    }

    private static void acknowledgementBeforePeerSyncIsTerminal() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(true, 0);
        model.takeOutput();
        model.feed(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 1);
        eq(Iap2LinkNegotiationModel.State.DEAD, model.getState());
        truth(model.getTerminalReason().contains("maxLength"));
    }

    private static void tooSmallPeerLimitIsTerminalOnAcknowledgement() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(true, 0);
        model.takeOutput();
        model.feed(peerSyncFrame(77, 10), 1);
        model.takeOutput();
        eq(Iap2LinkNegotiationModel.State.DEAD, model.getState());
        model.feed(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
        eq(Iap2LinkNegotiationModel.State.DEAD, model.getState());
        falsity(model.isReady());
    }

    private static void exposesImmutablePeerSynchronizationSnapshot() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(true, 0);
        model.takeOutput();
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                2, 4096, 1500, 250, 3, 5,
                new int[][] {{10, 0, 2}, {11, 2, 1}});
        model.feed(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, sync), 1);
        Iap2LinkNegotiationModel.PeerConfiguration peer = model.getPeerConfiguration();
        notNull(peer);
        eq(2, peer.maxOutgoing);
        eq(4096, peer.maxLength);
        eq(1500, peer.retransmissionTimeoutMillis);
        eq(250, peer.acknowledgementTimeoutMillis);
        eq(3, peer.maxRetransmissions);
        eq(5, peer.maxAcknowledgements);
        eq(77, peer.synchronizationSequence);
        eq(2, peer.getSessionDescriptorCount());
        int[] control = peer.getSessionDescriptor(0);
        eq(10, control[0]); eq(0, control[1]); eq(2, control[2]);
        control[0] = 99;
        eq(10, peer.getSessionDescriptor(0)[0]);
    }

    private static void rejectsMalformedSynchronizationPayloads() {
        byte[][] invalid = new byte[][] {
            new byte[0],
            new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9},
            new byte[] {2, 0, 0, 11, 0, 1, 0, 1, 1, 1}
        };
        for (int i = 0; i < invalid.length; i++) {
            Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
            model.start(true, 0);
            model.takeOutput();
            model.feed(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, invalid[i]), 1);
            // A malformed SYN alone is ignored by processFrame(); the
            // readiness failure occurs only when an ACK bit is also received.
            eq(Iap2LinkNegotiationModel.State.NEGOTIATING, model.getState());
            truth(model.getPeerConfiguration() == null);
            model.feed(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
            eq(Iap2LinkNegotiationModel.State.DEAD, model.getState());
        }
    }

    private static void preservesPeerSynchronizationBoundaryValues() {
        int[][] values = new int[][] {
            {0, 11, 0, 0, 0, 0},
            {255, 65535, 65535, 65535, 255, 255}
        };
        for (int i = 0; i < values.length; i++) {
            Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
            model.start(true, 0);
            model.takeOutput();
            byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                    values[i][0], values[i][1], values[i][2], values[i][3],
                    values[i][4], values[i][5], new int[0][3]);
            model.feed(Iap2LinkFrameCodec.encode(0x80, 200 + i, 0, 0, sync), 1);
            Iap2LinkNegotiationModel.PeerConfiguration peer = model.getPeerConfiguration();
            notNull(peer);
            eq(values[i][0], peer.maxOutgoing);
            eq(values[i][1], peer.maxLength);
            eq(values[i][2], peer.retransmissionTimeoutMillis);
            eq(values[i][3], peer.acknowledgementTimeoutMillis);
            eq(values[i][4], peer.maxRetransmissions);
            eq(values[i][5], peer.maxAcknowledgements);
            eq(0, peer.getSessionDescriptorCount());
            eq(Iap2LinkNegotiationModel.State.NORMAL, model.getState());
        }

        Iap2LinkNegotiationModel partialDescriptor = new Iap2LinkNegotiationModel();
        partialDescriptor.start(true, 0);
        partialDescriptor.takeOutput();
        byte[] complete = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                4, 4096, 1000, 500, 4, 3, new int[0][3]);
        byte[] withTrailingPartial = new byte[complete.length + 2];
        System.arraycopy(complete, 0, withTrailingPartial, 0, complete.length);
        withTrailingPartial[complete.length] = 10;
        withTrailingPartial[complete.length + 1] = 0;
        partialDescriptor.feed(Iap2LinkFrameCodec.encode(0x80, 88, 0, 0,
                withTrailingPartial), 1);
        eq(0, partialDescriptor.getPeerConfiguration().getSessionDescriptorCount());
    }

    private static void rejectsWrongStartupMarker() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(false, 0);
        model.takeOutput();
        model.feed(new byte[] { (byte)0xff, 0x55, 0x02, 0x01, (byte)0xee, 0x10 }, 1);
        eq(Iap2LinkNegotiationModel.State.DEAD, model.getState());
        truth(model.getTerminalReason().contains("marker"));
    }

    private static void eofTerminatesSession() {
        Iap2LinkNegotiationModel model = new Iap2LinkNegotiationModel();
        model.start(true, 0);
        model.feedEof();
        eq(Iap2LinkNegotiationModel.State.DEAD, model.getState());
        truth(model.getTerminalReason().contains("EOF"));
    }

    private static byte[] peerSyncFrame(int sequence, int maxLength) {
        return peerSyncFrame(sequence, maxLength, 0);
    }

    private static byte[] peerSyncFrame(int sequence, int maxLength, int acknowledgement) {
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                4, maxLength, 4000, 500, 4, 3,
                new int[][] {{10, 0, 2}, {11, 2, 1}, {12, 1, 2}});
        return Iap2LinkFrameCodec.encode(0x80, sequence, acknowledgement, 0, sync);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
    private static byte[] slice(byte[] a, int from, int to) {
        byte[] out = new byte[to - from]; System.arraycopy(a, from, out, 0, out.length); return out;
    }
    private static boolean startsWith(byte[] a, byte[] prefix) {
        if (a.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (a[i] != prefix[i]) return false;
        return true;
    }
    private static void bytes(byte[] a, byte[] b) {
        checks++; if (b == null || a.length != b.length) throw new AssertionError("byte length mismatch");
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) throw new AssertionError("byte mismatch " + i);
    }
    private static void eq(long a, long b) { checks++; if (a != b) throw new AssertionError(a + " != " + b); }
    private static void eq(Object a, Object b) { checks++; if (!a.equals(b)) throw new AssertionError(a + " != " + b); }
    private static void truth(boolean b) { checks++; if (!b) throw new AssertionError("expected true"); }
    private static void notNull(Object o) { checks++; if (o == null) throw new AssertionError("expected non-null"); }
    private static void falsity(boolean b) { checks++; if (b) throw new AssertionError("expected false"); }
}
