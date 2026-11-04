package cn.autohack.rebuild;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-started, foreground-only, read-only ELM327 Bluetooth SPP client. */
public final class BluetoothObdController {
    public interface Listener {
        void onState(String state);
        void onTelemetry(Integer speedKph, Integer engineRpm,
                Integer coolantTempC, Integer fuelPercent);
    }

    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean stopped = true;
    private volatile long generation;
    private volatile BluetoothSocket socket;
    private ExecutorService worker;

    public BluetoothObdController(Listener listener) {
        this.listener = listener;
    }

    public synchronized void connect(BluetoothDevice device) {
        disconnect();
        if (device == null) {
            postState("No Bluetooth adapter selected.", generation);
            return;
        }
        final long session = generation;
        stopped = false;
        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> runSession(device, session));
    }

    public synchronized void disconnect() {
        generation++;
        stopped = true;
        BluetoothSocket active = socket;
        socket = null;
        closeSocket(active);
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }

    private boolean isActive(long session) {
        return !stopped && session == generation;
    }

    private void runSession(BluetoothDevice device, long session) {
        BluetoothSocket activeSocket = null;
        String terminalState = "OBD-II adapter disconnected.";
        try {
            postState("Connecting to paired OBD-II adapter…", session);
            activeSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            if (!isActive(session)) return;
            socket = activeSocket;
            activeSocket.connect();
            if (!isActive(session)) return;
            InputStream input = activeSocket.getInputStream();
            OutputStream output = activeSocket.getOutputStream();

            sendCommand(input, output, "ATZ", 3500, session);
            sendCommand(input, output, "ATE0", 1500, session);
            sendCommand(input, output, "ATL0", 1500, session);
            sendCommand(input, output, "ATS0", 1500, session);
            sendCommand(input, output, "ATH0", 1500, session);
            sendCommand(input, output, "ATSP0", 1500, session);
            if (!isActive(session)) return;
            postState("Connected. Discovering ECU-supported standard PIDs…", session);
            String bitmap00 = sendCommand(input, output,
                    ObdReadOnlyCommands.BITMAP_00, 1800, session);
            String bitmap20 = null;
            String bitmap40 = null;
            String nextBitmap = ObdPidQueryPlan.nextBitmapCommand(bitmap00, 0x00);
            if (nextBitmap != null && isActive(session)) {
                bitmap20 = sendCommand(input, output, nextBitmap, 1800, session);
                nextBitmap = ObdPidQueryPlan.nextBitmapCommand(bitmap20, 0x20);
                if (nextBitmap != null && isActive(session)) {
                    bitmap40 = sendCommand(input, output, nextBitmap, 1800, session);
                }
            }
            java.util.List<String> supportedCommands =
                    ObdPidQueryPlan.supportedReadCommands(bitmap00, bitmap20, bitmap40);
            if (!isActive(session)) return;
            postState("Connected. Polling only ECU-advertised read-only PIDs.", session);

            while (isActive(session) && activeSocket.isConnected()) {
                Integer speed = supportedCommands.contains(ObdReadOnlyCommands.SPEED)
                        ? decode(ObdReadOnlyCommands.SPEED, input, output, session, 0) : null;
                Integer rpm = supportedCommands.contains(ObdReadOnlyCommands.ENGINE_RPM)
                        ? decode(ObdReadOnlyCommands.ENGINE_RPM, input, output, session, 1) : null;
                Integer coolant = supportedCommands.contains(ObdReadOnlyCommands.COOLANT_TEMP)
                        ? decode(ObdReadOnlyCommands.COOLANT_TEMP, input, output, session, 2) : null;
                Integer fuel = supportedCommands.contains(ObdReadOnlyCommands.FUEL_LEVEL)
                        ? decode(ObdReadOnlyCommands.FUEL_LEVEL, input, output, session, 3) : null;
                if (!isActive(session)) break;
                postTelemetry(speed, rpm, coolant, fuel, session);
                Thread.sleep(1000L);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            if (isActive(session))
                terminalState = "OBD-II connection failed: " + safeMessage(error);
        } finally {
            if (session == generation) {
                stopped = true;
                if (socket == activeSocket) socket = null;
                postState(terminalState, session);
            }
            closeSocket(activeSocket);
        }
    }

    private Integer decode(String command, InputStream input, OutputStream output,
            long session, int field) throws IOException, InterruptedException {
        if (!ObdReadOnlyCommands.isAllowed(command)) return null;
        String response = sendCommand(input, output, command, 1800, session);
        switch (field) {
            case 0: return ObdPidDecoder.speedKph(response);
            case 1: return ObdPidDecoder.engineRpm(response);
            case 2: return ObdPidDecoder.coolantTempC(response);
            case 3: return ObdPidDecoder.fuelLevelPercent(response);
            default: return null;
        }
    }

    private String sendCommand(InputStream input, OutputStream output,
            String command, int timeoutMs, long session)
            throws IOException, InterruptedException {
        if (!ObdReadOnlyCommands.isAllowed(command)
                && !ObdReadOnlyCommands.isAdapterSetupCommand(command)) {
            throw new SecurityException("Only allowlisted adapter setup and read-only PIDs are permitted");
        }
        if (!isActive(session)) return "";
        output.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        output.flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (isActive(session) && System.currentTimeMillis() < deadline
                && response.size() < 4096) {
            if (input.available() > 0) {
                int value = input.read();
                if (value < 0 || value == '>') break;
                response.write(value);
            } else {
                Thread.sleep(15L);
            }
        }
        return response.toString("US-ASCII");
    }

    private void postState(String state, long session) {
        if (listener != null) mainHandler.post(() -> {
            if (session == generation) listener.onState(state);
        });
    }

    private void postTelemetry(Integer speed, Integer rpm, Integer coolant,
            Integer fuel, long session) {
        if (listener != null) mainHandler.post(() -> {
            if (session == generation)
                listener.onTelemetry(speed, rpm, coolant, fuel);
        });
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    private static void closeSocket(BluetoothSocket active) {
        if (active != null) {
            try { active.close(); } catch (IOException ignored) {}
        }
    }
}
