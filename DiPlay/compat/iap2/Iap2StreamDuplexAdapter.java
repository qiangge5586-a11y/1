package com.shilapi.xcertplay.compat.iap2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

/**
 * Blocking-stream adapter. A fixed-timeout carrier may use the two-argument
 * constructor; a carrier supporting per-read timeouts should supply TimeoutController.
 * SocketTimeoutException maps to zero/no data. close() closes both streams and can
 * unblock a pending read without waiting for the read monitor.
 */
public final class Iap2StreamDuplexAdapter implements Iap2DuplexByteStream {
    public interface TimeoutController {
        void setReadTimeoutMillis(int timeoutMillis) throws IOException;
    }

    private final InputStream input;
    private final OutputStream output;
    private final TimeoutController timeoutController;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private volatile boolean closed;

    /** Uses the carrier's preconfigured fixed timeout. */
    public Iap2StreamDuplexAdapter(InputStream input, OutputStream output) {
        this(input, output, null);
    }

    /** Applies timeoutMillis to the carrier immediately before every blocking read. */
    public Iap2StreamDuplexAdapter(InputStream input, OutputStream output,
            TimeoutController timeoutController) {
        if (input == null) throw new NullPointerException("input");
        if (output == null) throw new NullPointerException("output");
        this.input = input;
        this.output = output;
        this.timeoutController = timeoutController;
    }

    public int read(byte[] buffer, int offset, int length, int timeoutMillis) throws IOException {
        checkRange(buffer, offset, length);
        if (length == 0) return 0;
        if (timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis < 0");
        synchronized (readLock) {
            ensureOpen();
            if (timeoutController != null) timeoutController.setReadTimeoutMillis(timeoutMillis);
            try {
                return input.read(buffer, offset, length);
            } catch (SocketTimeoutException timeout) {
                ensureOpen();
                return 0;
            }
        }
    }

    public void write(byte[] buffer, int offset, int length) throws IOException {
        checkRange(buffer, offset, length);
        synchronized (writeLock) {
            ensureOpen();
            output.write(buffer, offset, length);
            output.flush();
        }
    }

    public void close() throws IOException {
        synchronized (writeLock) {
            if (closed) return;
            closed = true;
        }
        IOException failure = null;
        try { input.close(); } catch (IOException e) { failure = e; }
        try { output.close(); } catch (IOException e) {
            if (failure == null) failure = e;
        }
        if (failure != null) throw failure;
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("iAP2 byte stream is closed");
    }

    private static void checkRange(byte[] buffer, int offset, int length) {
        if (buffer == null) throw new NullPointerException("buffer");
        if (offset < 0 || length < 0 || offset > buffer.length - length) {
            throw new IndexOutOfBoundsException("invalid buffer range");
        }
    }
}
