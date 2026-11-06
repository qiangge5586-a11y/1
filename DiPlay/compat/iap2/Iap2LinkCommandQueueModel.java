package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Offline mirror of Iap2LinkChannel's pending command queue.
 * Capacity is released when the pump removes a command, not when bytes hit the wire.
 */
public final class Iap2LinkCommandQueueModel {
    public static final int MAX_PENDING_COMMANDS = 64;
    public static final int MAX_PENDING_COMMAND_BYTES = 1048576;

    private final ArrayDeque<byte[]> commands = new ArrayDeque<byte[]>();
    private int commandBytes;
    private boolean closing;
    private boolean terminated;
    private String terminalFailure;

    public synchronized boolean hasCapacity(int bytes) {
        if (bytes < 0 || bytes > 65525) return false;
        return commands.size() < MAX_PENDING_COMMANDS
                && bytes <= MAX_PENDING_COMMAND_BYTES - commandBytes;
    }

    /** Mirrors sendControlAwaitCapacity after its wait predicate has become true. */
    public synchronized boolean enqueueIfCapacity(byte[] payload) {
        if (payload == null) throw new NullPointerException("payload");
        if (payload.length > 65525) throw new IllegalArgumentException("control payload exceeds 65525 bytes");
        if (terminated || closing || !hasCapacity(payload.length)) return false;
        commands.addLast(Arrays.copyOf(payload, payload.length));
        commandBytes += payload.length;
        return true;
    }

    /** Mirrors drainCommands: remove first, decrement bytes, then wake capacity waiters. */
    public synchronized byte[] takeForPump() {
        if (commands.isEmpty()) return null;
        byte[] payload = commands.removeFirst();
        commandBytes -= payload.length;
        return Arrays.copyOf(payload, payload.length);
    }

    public synchronized void beginClose() { closing = true; }
    public synchronized void terminate(String failure) {
        terminated = true;
        terminalFailure = failure;
    }

    public synchronized boolean isClosing() { return closing; }
    public synchronized boolean isTerminated() { return terminated; }
    public synchronized String terminalFailure() { return terminalFailure; }
    public synchronized int queuedCommands() { return commands.size(); }
    public synchronized int queuedBytes() { return commandBytes; }
}
