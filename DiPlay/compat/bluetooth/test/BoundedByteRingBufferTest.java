package com.shilapi.xcertplay.compat.bluetooth;

/** Pure Java boundary tests for the RFCOMM receive ring. */
public final class BoundedByteRingBufferTest {
    private static int checks;

    public static void main(String[] args) {
        testCapacityAndAllOrNothingOffer();
        testWrapAroundPreservesOrder();
        testPartialReadsAndOffsets();
        testClearAndZeroLength();
        testInvalidRanges();
        System.out.println("BoundedByteRingBufferTest: " + checks + " checks passed");
    }

    private static void testCapacityAndAllOrNothingOffer() {
        BoundedByteRingBuffer ring = new BoundedByteRingBuffer(4);
        truth(ring.offer(new byte[] { 1, 2, 3 }, 0, 3), "offer fits");
        truth(!ring.offer(new byte[] { 4, 5 }, 0, 2), "overflow rejected atomically");
        equal(3, ring.size(), "overflow does not change size");
        equal(1, ring.remainingCapacity(), "remaining capacity");
    }

    private static void testWrapAroundPreservesOrder() {
        BoundedByteRingBuffer ring = new BoundedByteRingBuffer(5);
        truth(ring.offer(new byte[] { 1, 2, 3, 4 }, 0, 4), "initial offer");
        byte[] first = new byte[3];
        equal(3, ring.read(first, 0, 3), "first read");
        truth(ring.offer(new byte[] { 5, 6, 7, 8 }, 0, 4), "wrapped offer");
        byte[] rest = new byte[5];
        equal(5, ring.read(rest, 0, 5), "wrapped read");
        bytes(new byte[] { 4, 5, 6, 7, 8 }, rest, "wrap order");
    }

    private static void testPartialReadsAndOffsets() {
        BoundedByteRingBuffer ring = new BoundedByteRingBuffer(8);
        truth(ring.offer(new byte[] { 9, 10, 11, 12 }, 0, 4), "offer");
        byte[] out = new byte[6];
        equal(2, ring.read(out, 2, 2), "partial read");
        equal(9, out[2] & 255, "offset first");
        equal(10, out[3] & 255, "offset second");
        equal(2, ring.size(), "remaining bytes");
        equal(2, ring.read(out, 0, 6), "drain");
        equal(11, out[0] & 255, "drain first");
        equal(12, out[1] & 255, "drain second");
    }

    private static void testClearAndZeroLength() {
        BoundedByteRingBuffer ring = new BoundedByteRingBuffer(2);
        truth(ring.offer(new byte[] { 1 }, 0, 1), "offer before clear");
        equal(0, ring.read(new byte[0], 0, 0), "zero read");
        ring.clear();
        equal(0, ring.size(), "clear size");
        equal(2, ring.remainingCapacity(), "clear capacity");
    }

    private static void testInvalidRanges() {
        BoundedByteRingBuffer ring = new BoundedByteRingBuffer(2);
        boolean failed = false;
        try { ring.offer(new byte[2], 1, 2); } catch (IndexOutOfBoundsException expected) { failed = true; }
        truth(failed, "invalid source range rejected");
        failed = false;
        try { ring.read(new byte[2], -1, 1); } catch (IndexOutOfBoundsException expected) { failed = true; }
        truth(failed, "invalid target range rejected");
    }

    private static void bytes(byte[] expected, byte[] actual, String label) {
        equal(expected.length, actual.length, label + " length");
        for (int i = 0; i < expected.length; i++) equal(expected[i], actual[i], label + "[" + i + "]");
    }

    private static void equal(int expected, int actual, String label) {
        checks++;
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + " got " + actual);
    }

    private static void truth(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
