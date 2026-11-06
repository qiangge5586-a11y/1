package com.shilapi.xcertplay.compat.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * API-17 RFCOMM connector following the original DiPlay fallback order.
 *
 * Calls are blocking and must run on a worker thread. Pairing and user consent
 * remain the caller's responsibility. cancel() closes the active attempt.
 */
public final class BluetoothRfcommConnector {
    public static final String DEFAULT_SERVICE_UUID =
            "00000000-deca-fade-deca-deafdecacafe";
    public static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 10000L;
    private static final long FALLBACK_DELAY_MILLIS = 100L;

    private volatile BluetoothSocket activeSocket;
    private volatile boolean cancelled;

    public synchronized BluetoothSocket connect(String deviceAddress, String serviceUuid) throws IOException {
        return connect(deviceAddress, serviceUuid, DEFAULT_CONNECT_TIMEOUT_MILLIS);
    }

    public synchronized BluetoothSocket connect(String deviceAddress, String serviceUuid,
            long timeoutMillis) throws IOException {
        if (deviceAddress == null || deviceAddress.length() == 0) {
            throw new IllegalArgumentException("deviceAddress is empty");
        }
        if (serviceUuid == null) throw new NullPointerException("serviceUuid");
        if (timeoutMillis <= 0L) throw new IllegalArgumentException("timeoutMillis <= 0");
        cancelled = false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new IOException("Bluetooth adapter unavailable");
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(deviceAddress);
        } catch (IllegalArgumentException invalidAddress) {
            throw new IOException("Invalid Bluetooth device address", invalidAddress);
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(serviceUuid);
        } catch (IllegalArgumentException invalidUuid) {
            throw new IOException("Invalid RFCOMM service UUID", invalidUuid);
        }

        Throwable lastError = null;
        BluetoothSocket socket = attempt(device, adapter, uuid, timeoutMillis, 0);
        if (socket != null) return socket;
        lastError = lastFailure;

        if (cancelled) throw new IOException("RFCOMM connection cancelled", lastError);
        sleepBeforeFallback();
        socket = attempt(device, adapter, uuid, timeoutMillis, 1);
        if (socket != null) return socket;
        if (lastFailure != null) lastError = lastFailure;

        if (cancelled) throw new IOException("RFCOMM connection cancelled", lastError);
        socket = attempt(device, adapter, uuid, timeoutMillis, 2);
        if (socket != null) return socket;
        if (lastFailure != null) lastError = lastFailure;
        throw new IOException("Failed to connect Bluetooth RFCOMM to " + deviceAddress, lastError);
    }

    private volatile Throwable lastFailure;

    private BluetoothSocket attempt(final BluetoothDevice device, final BluetoothAdapter adapter,
            UUID uuid, long timeoutMillis, int mode) {
        final BluetoothSocket socket;
        try {
            if (mode == 0) {
                socket = device.createRfcommSocketToServiceRecord(uuid);
            } else if (mode == 1) {
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } else {
                Method method = device.getClass().getMethod("createInsecureRfcommSocket",
                        Integer.TYPE);
                socket = (BluetoothSocket) method.invoke(device, Integer.valueOf(1));
            }
        } catch (Throwable failure) {
            lastFailure = failure;
            return null;
        }
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        activeSocket = socket;
        Thread worker = new Thread(new Runnable() {
            public void run() {
                try {
                    try {
                        adapter.cancelDiscovery();
                    } catch (SecurityException ignored) {
                        // The original implementation tolerates missing scan permission.
                    }
                    for (int i = 0; i < 10 && adapter.isDiscovering() && !cancelled; i++) {
                        try { Thread.sleep(50L); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("RFCOMM connect interrupted", interrupted);
                        }
                    }
                    if (cancelled) throw new IOException("RFCOMM connection cancelled");
                    socket.connect();
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    finished.countDown();
                }
            }
        }, "aosp-rfcomm-connect");
        worker.setDaemon(true);
        worker.start();

        try {
            if (!finished.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                closeQuietly(socket);
                lastFailure = new IOException("Timed out connecting AOSP RFCOMM");
                return null;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            closeQuietly(socket);
            cancelled = true;
            lastFailure = new IOException("RFCOMM connect interrupted", interrupted);
            return null;
        } finally {
            if (activeSocket == socket) activeSocket = null;
        }
        if (failure.get() != null) {
            lastFailure = failure.get();
            closeQuietly(socket);
            return null;
        }
        if (cancelled) {
            closeQuietly(socket);
            lastFailure = new IOException("RFCOMM connection cancelled");
            return null;
        }
        lastFailure = null;
        return socket;
    }

    private void sleepBeforeFallback() throws IOException {
        try {
            Thread.sleep(FALLBACK_DELAY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancelled = true;
            throw new IOException("RFCOMM fallback interrupted", interrupted);
        }
    }

    /** Cancels the in-flight connect attempt by closing its socket. */
    public void cancel() {
        cancelled = true;
        BluetoothSocket socket = activeSocket;
        if (socket != null) closeQuietly(socket);
    }

    private static void closeQuietly(BluetoothSocket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
