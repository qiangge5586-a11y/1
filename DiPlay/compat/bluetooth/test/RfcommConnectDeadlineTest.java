package com.shilapi.xcertplay.compat.bluetooth;

/** Pure Java tests for the overall RFCOMM fallback deadline. */
public final class RfcommConnectDeadlineTest {
    private static int checks;
    public static void main(String[] args) {
        long deadline = RfcommConnectDeadline.deadlineAfter(1000000L, 10000L);
        eq(10001000000L, deadline);
        eq(10000000000L, RfcommConnectDeadline.remainingNanos(1000000L, deadline));
        eq(6001L, RfcommConnectDeadline.remainingMillisCeil(4000000000L, deadline));
        eq(1L, RfcommConnectDeadline.remainingMillisCeil(10000999999L, deadline));
        eq(0L, RfcommConnectDeadline.remainingMillisCeil(deadline, deadline));
        eq(0L, RfcommConnectDeadline.remainingNanos(deadline + 5L, deadline));

        // System.nanoTime() is an arbitrary signed counter and may wrap.
        long nearWrap = Long.MAX_VALUE - 4L;
        long wrappedDeadline = RfcommConnectDeadline.deadlineAfter(nearWrap, 10L);
        eq(Long.MIN_VALUE + 5L, wrappedDeadline);
        eq(10000000L, RfcommConnectDeadline.remainingNanos(nearWrap, wrappedDeadline));
        eq(5000000L, RfcommConnectDeadline.remainingNanos(Long.MIN_VALUE, wrappedDeadline));

        fails(0L);
        fails(300001L);
        System.out.println("RfcommConnectDeadlineTest: PASS (" + checks + " checks)");
    }
    private static void fails(long timeout) {
        checks++;
        try { RfcommConnectDeadline.deadlineAfter(0L, timeout); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected invalid timeout: " + timeout);
    }
    private static void eq(long expected, long actual) {
        checks++;
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
}
