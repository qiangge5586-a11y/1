package com.shilapi.xcertplay.compat.mfi;

import java.util.ArrayList;
import java.util.List;

public final class MfiI2cCertificateReaderTest {
    private static int checks;

    private static final class FakeClock implements MfiOperationContext.NanoClock {
        long now;
        public long nanoTime() { return now; }
    }

    private static final class FakeTransport implements MfiI2cCertificateReader.RegisterTransport {
        final int certificateLength;
        final List<Integer> registers = new ArrayList<Integer>();
        final List<Integer> counts = new ArrayList<Integer>();
        MfiOperationContext.Cancellation cancelOnRead;
        FakeClock clock;
        long advanceNanos;
        boolean shortNextRead;

        FakeTransport(int certificateLength) { this.certificateLength = certificateLength; }

        public byte[] readRegister(int register, int count, MfiOperationContext context) {
            context.checkActive(1L);
            registers.add(Integer.valueOf(register));
            counts.add(Integer.valueOf(count));
            if (clock != null) clock.now += advanceNanos;
            if (cancelOnRead != null) cancelOnRead.cancel();
            context.checkActive(1L);
            if (shortNextRead) return new byte[Math.max(0, count - 1)];
            if (register == MfiI2cCertificateReader.CERTIFICATE_LENGTH_REGISTER) {
                return new byte[] {(byte) (certificateLength >>> 8), (byte) certificateLength};
            }
            byte[] result = new byte[count];
            int base = (register - MfiI2cCertificateReader.CERTIFICATE_DATA_REGISTER)
                    * MfiI2cCertificateReader.WINDOW_BYTES;
            for (int i = 0; i < count; i++) result[i] = (byte) (base + i);
            return result;
        }
    }

    public static void main(String[] args) {
        readsSourceDefinedWindows();
        rejectsInvalidLengthsAndShortReads();
        oneDeadlineCoversEveryWindow();
        cancellationStopsBeforeNextWindow();
        failedReadReleasesSharedLock();
        System.out.println("MfiI2cCertificateReaderTest: PASS (" + checks + " checks)");
    }

    private static void readsSourceDefinedWindows() {
        assertLength(1, 1);
        assertLength(128, 1);
        assertLength(129, 2);
        assertLength(65525, 512);
    }

    private static void assertLength(int length, int expectedWindows) {
        FakeTransport transport = new FakeTransport(length);
        MfiI2cCertificateReader reader = new MfiI2cCertificateReader(transport, 1L);
        byte[] result = reader.readCertificate(65525, operation(new FakeClock(), 300000L));
        eq(length, result.length);
        eq(expectedWindows + 1, transport.registers.size());
        eq(Integer.valueOf(0x30), transport.registers.get(0));
        eq(Integer.valueOf(2), transport.counts.get(0));
        for (int i = 0; i < expectedWindows; i++) {
            eq(Integer.valueOf(0x31 + i), transport.registers.get(i + 1));
            int expected = Math.min(128, length - i * 128);
            eq(Integer.valueOf(expected), transport.counts.get(i + 1));
        }
        if (length > 0) {
            eq((byte) 0, result[0]);
            eq((byte) ((length - 1) & 0xff), result[length - 1]);
        }
    }

    private static void rejectsInvalidLengthsAndShortReads() {
        expectIllegal(new Runnable() { public void run() {
            new MfiI2cCertificateReader(new FakeTransport(1), 1L)
                    .readCertificate(0, operation(new FakeClock(), 1000L));
        }});
        expectDataFailure(new Runnable() { public void run() {
            new MfiI2cCertificateReader(new FakeTransport(0), 1L)
                    .readCertificate(65525, operation(new FakeClock(), 1000L));
        }});
        FakeTransport shortTransport = new FakeTransport(129);
        shortTransport.shortNextRead = true;
        expectDataFailure(new Runnable() { public void run() {
            new MfiI2cCertificateReader(shortTransport, 1L)
                    .readCertificate(65525, operation(new FakeClock(), 1000L));
        }});
    }

    private static void oneDeadlineCoversEveryWindow() {
        final FakeClock clock = new FakeClock();
        final FakeTransport transport = new FakeTransport(129);
        transport.clock = clock;
        transport.advanceNanos = 6000000L;
        expectTimeout(new Runnable() { public void run() {
            new MfiI2cCertificateReader(transport, 1L)
                    .readCertificate(65525, operation(clock, 10L));
        }});
        eq(2, transport.registers.size());
    }

    private static void cancellationStopsBeforeNextWindow() {
        final MfiOperationContext.Cancellation cancellation =
                new MfiOperationContext.Cancellation();
        final FakeTransport transport = new FakeTransport(129);
        transport.cancelOnRead = cancellation;
        expectCancelled(new Runnable() { public void run() {
            new MfiI2cCertificateReader(transport, 1L)
                    .readCertificate(65525, new MfiOperationContext(
                            new FakeClock(), 0L, 1000L, 1L, cancellation));
        }});
        eq(1, transport.registers.size());
    }

    private static void failedReadReleasesSharedLock() {
        FakeTransport broken = new FakeTransport(1);
        broken.shortNextRead = true;
        expectDataFailure(new Runnable() { public void run() {
            new MfiI2cCertificateReader(broken, 1L)
                    .readCertificate(65525, operation(new FakeClock(), 1000L));
        }});
        FakeTransport healthy = new FakeTransport(1);
        byte[] result = new MfiI2cCertificateReader(healthy, 1L)
                .readCertificate(65525, operation(new FakeClock(), 1000L));
        eq(1, result.length);
    }

    private static MfiOperationContext operation(FakeClock clock, long timeoutMillis) {
        return new MfiOperationContext(clock, clock.now, timeoutMillis, 1L,
                new MfiOperationContext.Cancellation());
    }

    private static void eq(long expected, long actual) {
        checks++;
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    private static void eq(Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(expected + " != " + actual);
    }
    private static void expectIllegal(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }
    private static void expectDataFailure(Runnable action) {
        checks++;
        try { action.run(); } catch (MfiI2cCertificateReader.TransportDataException expected) { return; }
        throw new AssertionError("expected transport data failure");
    }
    private static void expectTimeout(Runnable action) {
        checks++;
        try { action.run(); } catch (MfiOperationContext.OperationTimeoutException expected) { return; }
        throw new AssertionError("expected timeout");
    }
    private static void expectCancelled(Runnable action) {
        checks++;
        try { action.run(); } catch (MfiOperationContext.OperationCancelledException expected) { return; }
        throw new AssertionError("expected cancellation");
    }
}
