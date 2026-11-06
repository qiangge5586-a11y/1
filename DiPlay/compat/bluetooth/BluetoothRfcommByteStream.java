package com.shilapi.xcertplay.compat.bluetooth;

import android.bluetooth.BluetoothSocket;
import com.shilapi.xcertplay.compat.iap2.Iap2DuplexByteStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Bounded full-duplex adapter for an already-connected Bluetooth RFCOMM socket.
 *
 * A dedicated reader thread converts the socket's indefinitely blocking InputStream
 * into the timeout-aware iAP2 stream contract. The queue is capped at 1 MiB.
 * Socket connection/pairing is intentionally performed by the caller, not here.
 */
public final class BluetoothRfcommByteStream implements Iap2DuplexByteStream {
    public static final int MAX_BUFFERED_BYTES = 1048576;
    private static final int READ_CHUNK_BYTES = 8192;

    private final BluetoothSocket socket;
    private final InputStream input;
    private final OutputStream output;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private final BoundedByteRingBuffer ring = new BoundedByteRingBuffer(MAX_BUFFERED_BYTES);
    private boolean eof;
    private boolean closed;
    private IOException readFailure;
    private final Thread reader;

    /** The socket must already be connected. This constructor starts one reader worker. */
    public BluetoothRfcommByteStream(BluetoothSocket connectedSocket) throws IOException {
        if (connectedSocket == null) throw new NullPointerException("connectedSocket");
        if (!connectedSocket.isConnected()) throw new IOException("RFCOMM socket is not connected");
        socket = connectedSocket;
        input = socket.getInputStream();
        output = socket.getOutputStream();
        reader = new Thread(new Runnable() {
            public void run() { readLoop(); }
        }, "iAP2-RFCOMM-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        byte[] chunk = new byte[READ_CHUNK_BYTES];
        try {
            while (true) {
                int count = input.read(chunk);
                if (count < 0) {
                    synchronized (readLock) { eof = true; readLock.notifyAll(); }
                    return;
                }
                if (count == 0) continue;
                int offset = 0;
                while (offset < count) {
                    synchronized (readLock) {
                        while (!closed && ring.remainingCapacity() == 0) readLock.wait();
                        if (closed) return;
                        int copy = Math.min(count - offset, ring.remainingCapacity());
                        if (!ring.offer(chunk, offset, copy)) throw new IOException("RFCOMM receive ring rejected writable bytes");
                        offset += copy;
                        readLock.notifyAll();
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException failure) {
            synchronized (readLock) {
                if (!closed) readFailure = failure;
                eof = true;
                readLock.notifyAll();
            }
        }
    }

    public int read(byte[] buffer, int offset, int length, int timeoutMillis) throws IOException {
        checkRange(buffer, offset, length);
        if (timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis < 0");
        if (length == 0) return 0;
        long deadline = System.nanoTime() + timeoutMillis * 1000000L;
        synchronized (readLock) {
            while (ring.size() == 0 && !eof && !closed) {
                if (timeoutMillis == 0) return 0;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return 0;
                long millis = remaining / 1000000L;
                int nanos = (int) (remaining % 1000000L);
                try {
                    readLock.wait(millis, nanos);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("RFCOMM read interrupted");
                }
            }
            if (ring.size() == 0) {
                if (readFailure != null) throw readFailure;
                return -1;
            }
            int count = ring.read(buffer, offset, length);
            readLock.notifyAll();
            return count;
        }
    }

    public void write(byte[] buffer, int offset, int length) throws IOException {
        checkRange(buffer, offset, length);
        synchronized (writeLock) {
            synchronized (readLock) {
                if (closed) throw new IOException("RFCOMM stream is closed");
            }
            output.write(buffer, offset, length);
            output.flush();
        }
    }

    public void close() throws IOException {
        synchronized (writeLock) {
            synchronized (readLock) {
                if (closed) return;
                closed = true;
                readLock.notifyAll();
            }
            socket.close();
        }
    }

    public int bufferedBytes() {
        synchronized (readLock) { return ring.size(); }
    }

    private static void checkRange(byte[] buffer, int offset, int length) {
        if (buffer == null) throw new NullPointerException("buffer");
        if (offset < 0 || length < 0 || offset > buffer.length - length) {
            throw new IndexOutOfBoundsException("invalid buffer range");
        }
    }
}
