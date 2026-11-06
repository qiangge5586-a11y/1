package com.shilapi.xcertplay.compat.mfi;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Source-grounded, deadline-bounded certificate reader for the MFi I2C register protocol.
 * Register transport remains hardware-specific and must bound each bus call using the
 * same operation context. Java 7 / Android API 17 compatible.
 */
public final class MfiI2cCertificateReader {
    public static final int CERTIFICATE_LENGTH_REGISTER = 0x30;
    public static final int CERTIFICATE_DATA_REGISTER = 0x31;
    public static final int WINDOW_BYTES = 128;
    public static final int MAX_CERTIFICATE_BYTES = 65525;

    public interface RegisterTransport {
        /** Reads exactly count bytes or throws; must honor context's remaining budget. */
        byte[] readRegister(int register, int count, MfiOperationContext context);
    }

    public static final class TransportDataException extends IllegalStateException {
        public TransportDataException(String message) { super(message); }
    }

    /*
     * Retain one process-wide coprocessor lock, as the original implementation serialized
     * access globally. Unlike synchronized, timed acquisition consumes the operation budget.
     */
    private static final ReentrantLock COPROCESSOR_LOCK = new ReentrantLock();

    private final RegisterTransport transport;
    private final long generation;

    public MfiI2cCertificateReader(RegisterTransport transport, long generation) {
        if (transport == null) throw new IllegalArgumentException("transport == null");
        this.transport = transport;
        this.generation = generation;
    }

    /**
     * Reads the 16-bit big-endian length at 0x30 and then 128-byte windows from 0x31 onward.
     * Lock acquisition, register reads, and every window share one caller-owned deadline.
     */
    public byte[] readCertificate(int maximumLength, MfiOperationContext context) {
        if (maximumLength < 1 || maximumLength > MAX_CERTIFICATE_BYTES)
            throw new IllegalArgumentException("maximumLength must be in 1..65525");
        if (context == null) throw new IllegalArgumentException("context == null");

        context.checkActive(generation);
        boolean locked;
        try {
            locked = COPROCESSOR_LOCK.tryLock(context.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MfiOperationContext.OperationCancelledException(
                    "interrupted while waiting for MFi coprocessor");
        }
        if (!locked)
            throw new MfiOperationContext.OperationTimeoutException(
                    "deadline exceeded waiting for MFi coprocessor");

        try {
            context.checkActive(generation);
            byte[] lengthBytes = transport.readRegister(
                    CERTIFICATE_LENGTH_REGISTER, 2, context);
            context.checkActive(generation);
            requireExactLength(lengthBytes, 2, "certificate length");

            int length = ((lengthBytes[0] & 0xff) << 8) | (lengthBytes[1] & 0xff);
            if (length < 1 || length > maximumLength)
                throw new TransportDataException("certificate length outside configured bounds");

            byte[] certificate = new byte[length];
            int offset = 0;
            int window = 0;
            while (offset < length) {
                context.checkActive(generation);
                int count = Math.min(WINDOW_BYTES, length - offset);
                byte[] part = transport.readRegister(
                        CERTIFICATE_DATA_REGISTER + window, count, context);
                context.checkActive(generation);
                requireExactLength(part, count, "certificate window");
                System.arraycopy(part, 0, certificate, offset, count);
                offset += count;
                window++;
            }
            context.checkActive(generation);
            return certificate;
        } finally {
            COPROCESSOR_LOCK.unlock();
        }
    }

    private static void requireExactLength(byte[] data, int expected, String phase) {
        if (data == null || data.length != expected)
            throw new TransportDataException(phase + " returned an unexpected byte count");
    }
}
