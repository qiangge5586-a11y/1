package com.shilapi.xcertplay.airplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TunnelPackageAccumulatorTest {
    private static final class RecordingListener implements IapTunnel.Listener {
        final List<byte[]> bodies = new ArrayList<byte[]>();
        final List<String> debug = new ArrayList<String>();
        public void onDebug(String message) { debug.add(message); }
        public void onIap(byte[] bytes) { bodies.add(bytes); }
    }

    public static void main(String[] args) {
        testFragmentedHeaderAndBody();
        testMultiplePackagesAndUnknownType();
        testLargePackageAcrossManyReads();
        testInvalidOversizeIsBounded();
        System.out.println("TunnelPackageAccumulatorTest: PASS");
    }

    private static void testFragmentedHeaderAndBody() {
        RecordingListener listener = new RecordingListener();
        TunnelPackageAccumulator parser = new TunnelPackageAccumulator(listener);
        byte[] packet = packet(0x636f6d6d, new byte[] {1, 2, 3, 4, 5});
        parser.append(Arrays.copyOfRange(packet, 0, 7));
        parser.append(Arrays.copyOfRange(packet, 7, 32));
        check(listener.bodies.size() == 0, "body must wait for complete payload");
        parser.append(Arrays.copyOfRange(packet, 32, packet.length));
        check(listener.bodies.size() == 1, "fragmented packet dispatched once");
        check(Arrays.equals(listener.bodies.get(0), new byte[] {1, 2, 3, 4, 5}), "body bytes preserved");
        check(listener.debug.size() == 1 && listener.debug.get(0).endsWith("body=5"), "debug callback preserved");
    }

    private static void testMultiplePackagesAndUnknownType() {
        RecordingListener listener = new RecordingListener();
        TunnelPackageAccumulator parser = new TunnelPackageAccumulator(listener);
        byte[] a = packet(0x12345678, new byte[] {9});
        byte[] b = packet(0x636f6d6d, new byte[] {7, 8});
        byte[] c = packet(0x636f6d6d, new byte[0]);
        parser.append(join(join(a, b), c));
        check(listener.bodies.size() == 2, "unknown package skipped, comm packages delivered");
        check(Arrays.equals(listener.bodies.get(0), new byte[] {7, 8}), "first comm order");
        check(listener.bodies.get(1).length == 0, "empty comm body delivered");
    }

    private static void testLargePackageAcrossManyReads() {
        RecordingListener listener = new RecordingListener();
        TunnelPackageAccumulator parser = new TunnelPackageAccumulator(listener);
        byte[] body = new byte[1024 * 1024];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i * 31);
        byte[] packet = packet(0x636f6d6d, body);
        for (int offset = 0; offset < packet.length; offset += 4093) {
            parser.append(Arrays.copyOfRange(packet, offset, Math.min(packet.length, offset + 4093)));
        }
        check(listener.bodies.size() == 1, "large fragmented packet dispatched once");
        check(Arrays.equals(listener.bodies.get(0), body), "large body preserved");
    }

    private static void testInvalidOversizeIsBounded() {
        RecordingListener listener = new RecordingListener();
        TunnelPackageAccumulator parser = new TunnelPackageAccumulator(listener);
        byte[] invalidHeader = new byte[32];
        invalidHeader[0] = 0x7f;
        invalidHeader[1] = (byte) 0xff;
        invalidHeader[2] = (byte) 0xff;
        invalidHeader[3] = (byte) 0xff;
        parser.append(invalidHeader);
        boolean failedClosed = false;
        try {
            parser.append(new byte[4 * 1024 * 1024 + 16384]);
        } catch (IllegalStateException expected) {
            failedClosed = true;
        }
        check(failedClosed, "invalid/incomplete stream cannot grow without bound");
    }

    private static byte[] packet(int type, byte[] body) {
        byte[] packet = new byte[32 + body.length];
        int size = packet.length;
        packet[0] = (byte) (size >>> 24);
        packet[1] = (byte) (size >>> 16);
        packet[2] = (byte) (size >>> 8);
        packet[3] = (byte) size;
        packet[16] = (byte) (type >>> 24);
        packet[17] = (byte) (type >>> 16);
        packet[18] = (byte) (type >>> 8);
        packet[19] = (byte) type;
        System.arraycopy(body, 0, packet, 32, body.length);
        return packet;
    }

    private static byte[] join(byte[] a, byte[] b) {
        byte[] joined = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, joined, a.length, b.length);
        return joined;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
