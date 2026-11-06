package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

/** Contract tests for the API-17 stream adapter; no Android runtime or hardware required. */
public final class Iap2DuplexByteStreamTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        testReadBytesAndEof();
        testTimeoutMapping();
        testTimeoutControllerReceivesPumpDeadline();
        testWriteRangeAndCompleteWrite();
        testCloseIsIdempotentAndClosesBothSides();
        System.out.println("Iap2DuplexByteStreamTest: " + checks + " checks passed");
    }

    private static void testReadBytesAndEof() throws Exception {
        Iap2StreamDuplexAdapter stream = new Iap2StreamDuplexAdapter(
                new ByteArrayInputStream(new byte[] { 4, 5, 6 }), new ByteArrayOutputStream());
        byte[] buffer = new byte[8];
        equal(3, stream.read(buffer, 0, buffer.length, 100), "read count");
        equal(4, buffer[0] & 255, "first byte");
        equal(6, buffer[2] & 255, "last byte");
        equal(-1, stream.read(buffer, 0, buffer.length, 100), "EOF");
        stream.close();
    }

    private static void testTimeoutMapping() throws Exception {
        InputStream timeout = new InputStream() {
            public int read() throws IOException { throw new SocketTimeoutException("timeout"); }
            public int read(byte[] b, int off, int len) throws IOException {
                throw new SocketTimeoutException("timeout");
            }
        };
        Iap2StreamDuplexAdapter stream = new Iap2StreamDuplexAdapter(timeout, new ByteArrayOutputStream());
        equal(0, stream.read(new byte[4], 0, 4, 25), "timeout maps to zero");
        stream.close();
    }


    private static void testTimeoutControllerReceivesPumpDeadline() throws Exception {
        final int[] configured = new int[] { -1 };
        InputStream timeout = new InputStream() {
            public int read() throws IOException { throw new SocketTimeoutException("timeout"); }
            public int read(byte[] b, int off, int len) throws IOException {
                throw new SocketTimeoutException("timeout");
            }
        };
        Iap2StreamDuplexAdapter stream = new Iap2StreamDuplexAdapter(timeout,
                new ByteArrayOutputStream(), new Iap2StreamDuplexAdapter.TimeoutController() {
                    public void setReadTimeoutMillis(int value) { configured[0] = value; }
                });
        equal(0, stream.read(new byte[4], 0, 4, 37), "controlled timeout maps to zero");
        equal(37, configured[0], "pump timeout applied to carrier");
        stream.close();
    }

    private static void testWriteRangeAndCompleteWrite() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Iap2StreamDuplexAdapter stream = new Iap2StreamDuplexAdapter(
                new ByteArrayInputStream(new byte[0]), output);
        byte[] source = new byte[] { 9, 8, 7, 6 };
        stream.write(source, 1, 2);
        byte[] written = output.toByteArray();
        equal(2, written.length, "write length");
        equal(8, written[0] & 255, "write offset");
        equal(7, written[1] & 255, "write end");
        stream.close();
    }

    private static void testCloseIsIdempotentAndClosesBothSides() throws Exception {
        final boolean[] closed = new boolean[2];
        InputStream input = new ByteArrayInputStream(new byte[0]) {
            public void close() throws IOException { closed[0] = true; }
        };
        OutputStream output = new ByteArrayOutputStream() {
            public void close() throws IOException { closed[1] = true; }
        };
        Iap2StreamDuplexAdapter stream = new Iap2StreamDuplexAdapter(input, output);
        stream.close();
        stream.close();
        truth(closed[0], "input closed");
        truth(closed[1], "output closed");
        boolean failed = false;
        try { stream.write(new byte[] { 1 }, 0, 1); } catch (IOException expected) { failed = true; }
        truth(failed, "write after close rejected");
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
