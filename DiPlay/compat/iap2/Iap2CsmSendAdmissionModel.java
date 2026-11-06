package com.shilapi.xcertplay.compat.iap2;

import java.util.List;

/**
 * Deterministic step model of Iap2CsmChannel.send() admission across link chunks.
 * It models wait/queue/timeout decisions only; it never blocks or writes transport bytes.
 */
public final class Iap2CsmSendAdmissionModel {
    public enum Status {
        QUEUED, COMPLETE, WAITING, TIMEOUT_FAIL_CLOSED, CLOSED, TERMINAL_FAILURE
    }

    public static final class Result {
        public final Status status;
        public final int chunkIndex;
        public final long remainingMillis;
        public final String terminalFailure;

        private Result(Status status, int chunkIndex, long remainingMillis,
                String terminalFailure) {
            this.status = status;
            this.chunkIndex = chunkIndex;
            this.remainingMillis = remainingMillis;
            this.terminalFailure = terminalFailure;
        }
    }

    private final List<byte[]> chunks;
    private final Iap2CsmSendDeadline deadline;
    private int nextChunk;
    private boolean failedClosed;

    public Iap2CsmSendAdmissionModel(byte[] encodedFrame, int peerMaxControlPayloadBytes,
            long startedAtNanos, long timeoutMillis) {
        this.chunks = Iap2CsmSendPlan.splitForLink(encodedFrame, peerMaxControlPayloadBytes);
        this.deadline = new Iap2CsmSendDeadline(startedAtNanos, timeoutMillis);
    }

    /**
     * Attempts exactly one chunk admission. Call again after capacity changes or time advances.
     * Capacity is checked before remaining time, matching a zero-timeout immediate admission.
     */
    public synchronized Result step(long nowNanos, Iap2LinkCommandQueueModel queue) {
        if (queue == null) throw new NullPointerException("queue");
        if (failedClosed) return result(Status.TIMEOUT_FAIL_CLOSED, 0L, null);
        if (nextChunk >= chunks.size()) return result(Status.COMPLETE, 0L, null);

        if (queue.isTerminated()) {
            String failure = queue.terminalFailure();
            return result(failure == null ? Status.CLOSED : Status.TERMINAL_FAILURE,
                    0L, failure);
        }
        if (queue.isClosing()) return result(Status.CLOSED, 0L, null);

        byte[] chunk = chunks.get(nextChunk);
        // enqueueIfCapacity performs the capacity check and insert under one queue lock.
        if (queue.enqueueIfCapacity(chunk)) {
            nextChunk++;
            return result(nextChunk == chunks.size() ? Status.COMPLETE : Status.QUEUED,
                    deadline.remainingMillis(nowNanos), null);
        }
        if (queue.isTerminated()) {
            String failure = queue.terminalFailure();
            return result(failure == null ? Status.CLOSED : Status.TERMINAL_FAILURE,
                    0L, failure);
        }
        if (queue.isClosing()) return result(Status.CLOSED, 0L, null);

        long remaining = deadline.remainingMillis(nowNanos);
        if (remaining == 0L) {
            failedClosed = true;
            return result(Status.TIMEOUT_FAIL_CLOSED, 0L, null);
        }
        return result(Status.WAITING, remaining, null);
    }

    public synchronized int queuedChunkCount() { return nextChunk; }
    public synchronized int totalChunkCount() { return chunks.size(); }
    public synchronized boolean isFailedClosed() { return failedClosed; }

    private Result result(Status status, long remainingMillis, String failure) {
        return new Result(status, nextChunk, remainingMillis, failure);
    }
}
