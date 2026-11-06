package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.List;

public final class Iap2CsmReceiveCoordinatorTest {
    private static int checks;

    public static void main(String[] args) {
        splitFrameAcrossOrderedPackets();
        ignoreNonControlSessions();
        preserveFrameOrderForCoalescedPayloads();
        closeOnReceiveQueueOverflow();
        System.out.println("Iap2CsmReceiveCoordinatorTest: " + checks + " checks passed");
    }

    private static void splitFrameAcrossOrderedPackets() {
        Iap2CsmReceiveCoordinator receiver = new Iap2CsmReceiveCoordinator();
        byte[] encoded = Iap2CsmFrameCodec.encode(0x1234, new byte[] {1, 2, 3, 4});
        List<Iap2ReceiveWindowModel.Packet> packets = new ArrayList<Iap2ReceiveWindowModel.Packet>();
        packets.add(packet(1, Iap2LinkFrameCodec.CONTROL_SESSION_ID, slice(encoded, 0, 4)));
        packets.add(packet(2, Iap2LinkFrameCodec.CONTROL_SESSION_ID,
                slice(encoded, 4, encoded.length)));
        equal(0, receiver.acceptDeliveredPackets(packets.subList(0, 1)));
        equal(encoded.length - 4, receiver.decoderPendingBytes());
        equal(1, receiver.acceptDeliveredPackets(packets.subList(1, 2)));
        Iap2CsmReceiveQueueModel.Frame frame = receiver.poll();
        equal(0x1234, frame.messageId);
        bytes(new byte[] {1, 2, 3, 4}, frame.payload);
        equal(0, receiver.queuedFrames());
    }

    private static void ignoreNonControlSessions() {
        Iap2CsmReceiveCoordinator receiver = new Iap2CsmReceiveCoordinator();
        byte[] encoded = Iap2CsmFrameCodec.encode(7, new byte[] {9});
        List<Iap2ReceiveWindowModel.Packet> packets = new ArrayList<Iap2ReceiveWindowModel.Packet>();
        packets.add(packet(1, Iap2LinkFrameCodec.EA_SESSION_ID, encoded));
        equal(0, receiver.acceptDeliveredPackets(packets));
        equal(0, receiver.decoderPendingBytes());
        equal(0, receiver.queuedFrames());
    }

    private static void preserveFrameOrderForCoalescedPayloads() {
        Iap2CsmReceiveCoordinator receiver = new Iap2CsmReceiveCoordinator();
        byte[] a = Iap2CsmFrameCodec.encode(11, new byte[] {1});
        byte[] b = Iap2CsmFrameCodec.encode(12, new byte[] {2});
        byte[] both = new byte[a.length + b.length];
        System.arraycopy(a, 0, both, 0, a.length);
        System.arraycopy(b, 0, both, a.length, b.length);
        List<Iap2ReceiveWindowModel.Packet> packets = new ArrayList<Iap2ReceiveWindowModel.Packet>();
        packets.add(packet(1, Iap2LinkFrameCodec.CONTROL_SESSION_ID, both));
        equal(2, receiver.acceptDeliveredPackets(packets));
        equal(11, receiver.poll().messageId);
        equal(12, receiver.poll().messageId);
    }

    private static void closeOnReceiveQueueOverflow() {
        Iap2CsmReceiveCoordinator receiver = new Iap2CsmReceiveCoordinator();
        List<Iap2ReceiveWindowModel.Packet> packets = new ArrayList<Iap2ReceiveWindowModel.Packet>();
        for (int i = 0; i < Iap2CsmReceiveQueueModel.MAX_PENDING_FRAMES + 1; i++) {
            packets.add(packet(i & 255, Iap2LinkFrameCodec.CONTROL_SESSION_ID,
                    Iap2CsmFrameCodec.encode(i, new byte[0])));
        }
        equal(Iap2CsmReceiveQueueModel.MAX_PENDING_FRAMES,
                receiver.acceptDeliveredPackets(packets));
        truth(receiver.isClosed());
        truth(receiver.terminalReason() != null);
        equal(Iap2CsmReceiveQueueModel.MAX_PENDING_FRAMES, receiver.queuedFrames());
        equal(0, receiver.acceptDeliveredPackets(packets));
    }

    private static Iap2ReceiveWindowModel.Packet packet(int seq, int session, byte[] payload) {
        return new Iap2ReceiveWindowModel.Packet(seq, session, payload);
    }
    private static byte[] slice(byte[] b, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(b, from, out, 0, out.length);
        return out;
    }
    private static void equal(int expected, int actual) {
        checks++;
        if (expected != actual) throw new AssertionError("expected " + expected + " but was " + actual);
    }
    private static void bytes(byte[] expected, byte[] actual) {
        checks++;
        if (actual == null || expected.length != actual.length) throw new AssertionError("byte length mismatch");
        for (int i = 0; i < expected.length; i++)
            if (expected[i] != actual[i]) throw new AssertionError("byte mismatch at " + i);
    }
    private static void truth(boolean value) {
        checks++;
        if (!value) throw new AssertionError("expected true");
    }
}
