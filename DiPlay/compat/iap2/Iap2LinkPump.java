package com.shilapi.xcertplay.compat.iap2;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Single-owner iAP2 link pump. A concrete, source-audited engine adapter supplies Driver;
 * this class owns all Driver calls and all stream I/O on one worker thread.
 *
 * Driver methods must be non-blocking and deterministic. nextDeadlineMillis returns
 * Long.MAX_VALUE when no timer is pending. The carrier stream must have a read timeout
 * no greater than the pump's 100 ms poll interval.
 */
public final class Iap2LinkPump {
    public interface Driver {
        void start(long nowMillis) throws IOException;
        void onBytes(byte[] bytes, int length, long nowMillis) throws IOException;
        void onTimer(long nowMillis) throws IOException;
        byte[] drainOutput() throws IOException;
        boolean isTerminal();
        String terminalReason();
        long nextDeadlineMillis();
    }

    /**
     * Callbacks run on the pump worker. Implementations must return quickly and dispatch
     * UI work to the main thread; blocking here stalls transport reads and timer progress.
     */
    public interface Listener {
        void onReady();
        void onClosed(String reason);
        void onFailure(IOException failure);
    }

    public static final int MAX_COMMANDS = 64;
    public static final int MAX_COMMAND_BYTES = 1048576;
    public static final int MAX_CONTROL_PAYLOAD = 65525;
    private static final int READ_BUFFER_BYTES = 8192;
    private static final int MAX_POLL_MILLIS = 100;
    private static final int MAX_COMMANDS_PER_TURN = 8;

    private final Iap2DuplexByteStream stream;
    private final Driver driver;
    private final Listener listener;
    private final Object lock = new Object();
    private final ArrayDeque<byte[]> commands = new ArrayDeque<byte[]>();
    private int commandBytes;
    private Thread worker;
    private boolean started;
    private boolean closing;
    private boolean terminated;
    private boolean readyNotified;

    public Iap2LinkPump(Iap2DuplexByteStream stream, Driver driver, Listener listener) {
        if (stream == null || driver == null || listener == null) {
            throw new NullPointerException("pump dependency");
        }
        this.stream = stream;
        this.driver = driver;
        this.listener = listener;
    }

    public void start() {
        synchronized (lock) {
            if (started) throw new IllegalStateException("pump already started");
            if (closing) throw new IllegalStateException("pump is closing");
            started = true;
            worker = new Thread(new Runnable() {
                public void run() { runPump(); }
            }, "iAP2-Link-Pump");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** Bounded producer API. Capacity is released when the worker dequeues the command. */
    public boolean sendControl(byte[] payload) {
        if (payload == null) throw new NullPointerException("payload");
        if (payload.length > MAX_CONTROL_PAYLOAD) {
            throw new IllegalArgumentException("control payload exceeds " + MAX_CONTROL_PAYLOAD);
        }
        synchronized (lock) {
            if (!started || closing || terminated || commands.size() >= MAX_COMMANDS
                    || payload.length > MAX_COMMAND_BYTES - commandBytes) return false;
            commands.addLast(Arrays.copyOf(payload, payload.length));
            commandBytes += payload.length;
            lock.notifyAll();
            return true;
        }
    }

    /**
     * Producer-side capacity wait. Never call this from Driver/Listener callbacks or the pump
     * worker. The timeout is shared by the capacity wait only; a zero timeout still admits
     * immediately when capacity is already available.
     */
    public boolean sendControlAwaitCapacity(byte[] payload, long timeoutMillis) {
        if (timeoutMillis < 0L) throw new IllegalArgumentException("negative timeout");
        return sendControlAwaitCapacityNanos(payload,
                java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
    }

    /** Package-private nanosecond variant for multi-chunk senders sharing one deadline. */
    boolean sendControlAwaitCapacityNanos(byte[] payload, long timeoutNanos) {
        if (payload == null) throw new NullPointerException("payload");
        if (timeoutNanos < 0L) throw new IllegalArgumentException("negative timeout");
        if (payload.length > MAX_CONTROL_PAYLOAD) {
            throw new IllegalArgumentException("control payload exceeds " + MAX_CONTROL_PAYLOAD);
        }
        final long startedAt = System.nanoTime();
        synchronized (lock) {
            while (true) {
                if (!started || closing || terminated) return false;
                if (commands.size() < MAX_COMMANDS
                        && payload.length <= MAX_COMMAND_BYTES - commandBytes) {
                    commands.addLast(Arrays.copyOf(payload, payload.length));
                    commandBytes += payload.length;
                    lock.notifyAll();
                    return true;
                }
                long elapsed = System.nanoTime() - startedAt;
                long remaining = timeoutNanos - elapsed;
                if (remaining <= 0L) return false;
                long waitMillis = remaining / 1000000L;
                int waitNanos = (int) (remaining % 1000000L);
                try {
                    lock.wait(waitMillis, waitNanos);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    public void close() {
        synchronized (lock) {
            if (closing || terminated) return;
            closing = true;
            commands.clear();
            commandBytes = 0;
            lock.notifyAll();
        }
        try {
            stream.close(); // Must unblock a pending read.
        } catch (IOException e) {
            listener.onFailure(e);
        }
    }

    public boolean isTerminated() {
        synchronized (lock) { return terminated; }
    }

    private void runPump() {
        String reason = "iAP2 link pump stopped";
        IOException failure = null;
        byte[] input = new byte[READ_BUFFER_BYTES];
        try {
            driver.start(elapsedRealtimeMillis());
            drainDriverOutput();
            notifyReadyIfNeeded();
            while (!isClosing()) {
                long now = elapsedRealtimeMillis();
                drainDriverOutput();
                deliverCommands(now);
                if (driver.isTerminal()) {
                    reason = driver.terminalReason();
                    break;
                }
                long deadline = driver.nextDeadlineMillis();
                long wait = MAX_POLL_MILLIS;
                if (deadline != Long.MAX_VALUE) {
                    wait = Math.max(1L, Math.min((long) MAX_POLL_MILLIS, deadline - now));
                }
                int count = stream.read(input, 0, input.length, (int) wait);
                now = elapsedRealtimeMillis();
                if (count < 0) {
                    reason = "iAP2 transport EOF";
                    break;
                }
                if (count > 0) driver.onBytes(input, count, now);
                driver.onTimer(now);
                drainDriverOutput();
                notifyReadyIfNeeded();
                if (driver.isTerminal()) {
                    reason = driver.terminalReason();
                    break;
                }
            }
        } catch (IOException e) {
            failure = e;
            reason = e.getMessage() == null ? "iAP2 transport I/O failure" : e.getMessage();
        } catch (RuntimeException e) {
            failure = new IOException("iAP2 pump driver failure: " + e.getMessage());
            reason = failure.getMessage();
        } finally {
            synchronized (lock) {
                terminated = true;
                closing = true;
                commands.clear();
                commandBytes = 0;
                lock.notifyAll();
            }
            try { stream.close(); } catch (IOException closeError) {
                if (failure == null) failure = closeError;
            }
            if (failure != null) listener.onFailure(failure);
            listener.onClosed(reason);
        }
    }

    // Readiness is deliberately optional in the Driver contract; adapters may expose it
    // through a marker interface without coupling this pump to an unverified model.
    public interface Readiness {
        boolean isReady();
    }

    private boolean driverIsReady() {
        return driver instanceof Readiness && ((Readiness) driver).isReady();
    }

    private void notifyReadyIfNeeded() {
        if (!readyNotified && driverIsReady()) {
            readyNotified = true;
            listener.onReady();
        }
    }

    /** Monotonic milliseconds; wall-clock adjustments must not shift protocol deadlines. */
    private static long elapsedRealtimeMillis() {
        return System.nanoTime() / 1000000L;
    }

    private boolean isClosing() {
        synchronized (lock) { return closing; }
    }

    private void deliverCommands(long nowMillis) throws IOException {
        // Bound each outbound burst so a full producer queue cannot indefinitely postpone
        // inbound ACK/data processing and timer advancement on this single-owner worker.
        for (int delivered = 0; delivered < MAX_COMMANDS_PER_TURN; delivered++) {
            byte[] command;
            synchronized (lock) {
                command = commands.pollFirst();
                if (command == null) return;
                commandBytes -= command.length;
                lock.notifyAll();
            }
            if (driver instanceof CommandSink) {
                ((CommandSink) driver).sendControl(command, nowMillis);
            } else {
                throw new IOException("iAP2 Driver does not implement CommandSink");
            }
            drainDriverOutput();
        }
    }

    public interface CommandSink {
        void sendControl(byte[] payload, long nowMillis) throws IOException;
    }

    private void drainDriverOutput() throws IOException {
        byte[] output = driver.drainOutput();
        if (output != null && output.length > 0) stream.write(output, 0, output.length);
    }
}
