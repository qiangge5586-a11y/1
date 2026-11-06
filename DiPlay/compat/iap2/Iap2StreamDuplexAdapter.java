package com.shilapi.xcertplay.compat.iap2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

/**
 * Minimal blocking-stream adapter. Configure the underlying transport's read timeout
 * before use (e.g. BluetoothSocket.setSoTimeout); no API newer than Android 4.2 is used.
 * The caller owns the streams and this adapter; close() closes both exactly once.
 */
public final class Iap2StreamDuplexAdapter implements Iap2DuplexByteStream {
    private final InputStream input;
    private final OutputStream output;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private boolean closed;

    public Iap2StreamDuplexAdapter(InputStream input, OutputStream output) {
        if (input == null) throw new NullPointerException("input");
        if (output == null) throw new NullPointerException("output");
        this.input = input;
        this.output = output;
    }

    public int read(byte[] buffer, int offset, int length, int timeoutMillis) throws IOException {
        checkRange(buffer, offset, length);
        if (length == 0) return 0;
        if (timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis < 0");
        synchronized (readLock) {
            ensureOpen();
            try {
                return input.read(buffer, offset, length);
            } catch (SocketTimeoutException timeout) {
                synchronized (writeLock) {
                    ensureOpen();
                }
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
        IOException failure = null;
        synchronized (writeLock) {
            if (closed) return;
            closed = true;
        }
        synchronized (readLock) {
            try { input.close(); } catch (IOException e) { failure = e; }
            try { output.close(); } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
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
