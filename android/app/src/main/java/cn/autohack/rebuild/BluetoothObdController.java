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
    private volatile BluetoothSocket socket;
    private ExecutorService worker;

    public BluetoothObdController(Listener listener) {
        this.listener = listener;
    }

    public synchronized void connect(BluetoothDevice device) {
        disconnect();
        if (device == null) {
            postState("No Bluetooth adapter selected.");
            return;
        }
        stopped = false;
        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> runSession(device));
    }

    public synchronized void disconnect() {
        stopped = true;
        closeSocket();
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }

    private void runSession(BluetoothDevice device) {
        try {
            postState("Connecting to paired OBD-II adapter…");
            BluetoothSocket activeSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket = activeSocket;
            activeSocket.connect();
            InputStream input = activeSocket.getInputStream();
            OutputStream output = activeSocket.getOutputStream();

            sendCommand(input, output, "ATZ", 3500);
            sendCommand(input, output, "ATE0", 1500);
            sendCommand(input, output, "ATL0", 1500);
            sendCommand(input, output, "ATS0", 1500);
            sendCommand(input, output, "ATH0", 1500);
            sendCommand(input, output, "ATSP0", 1500);
            postState("Connected. Reading standard OBD-II PIDs only.");

            while (!stopped && activeSocket.isConnected()) {
                Integer speed = decode(ObdReadOnlyCommands.POLL_COMMANDS[0],
                        input, output, 0x0D, 1, 0);
                Integer rpm = decode(ObdReadOnlyCommands.POLL_COMMANDS[1],
                        input, output, 0x0C, 2, 1);
                Integer coolant = decode(ObdReadOnlyCommands.POLL_COMMANDS[2],
                        input, output, 0x05, 1, 2);
                Integer fuel = decode(ObdReadOnlyCommands.POLL_COMMANDS[3],
                        input, output, 0x2F, 1, 3);
                if (stopped) break;
                postTelemetry(speed, rpm, coolant, fuel);
                Thread.sleep(1000L);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            if (!stopped) postState("OBD-II connection failed: " + safeMessage(error));
        } finally {
            stopped = true;
            closeSocket();
            postState("OBD-II adapter disconnected.");
        }
    }

    private Integer decode(String command, InputStream input, OutputStream output,
            int pid, int byteCount, int field) throws IOException, InterruptedException {
        if (!ObdReadOnlyCommands.isAllowed(command)) return null;
        String response = sendCommand(input, output, command, 1800);
        switch (field) {
            case 0: return ObdPidDecoder.speedKph(response);
            case 1: return ObdPidDecoder.engineRpm(response);
            case 2: return ObdPidDecoder.coolantTempC(response);
            case 3: return ObdPidDecoder.fuelLevelPercent(response);
            default: return null;
        }
    }

    private String sendCommand(InputStream input, OutputStream output,
            String command, int timeoutMs) throws IOException, InterruptedException {
        if (!ObdReadOnlyCommands.isAllowed(command)
                && !command.startsWith("AT")) {
            throw new SecurityException("Only adapter setup and allowlisted read-only PIDs are permitted");
        }
        if (stopped) return "";
        output.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        output.flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!stopped && System.currentTimeMillis() < deadline
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

    private void postState(String state) {
        if (listener != null) mainHandler.post(() -> listener.onState(state));
    }

    private void postTelemetry(Integer speed, Integer rpm, Integer coolant, Integer fuel) {
        if (listener != null) mainHandler.post(() ->
                listener.onTelemetry(speed, rpm, coolant, fuel));
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    private synchronized void closeSocket() {
        BluetoothSocket active = socket;
        socket = null;
        if (active != null) {
            try { active.close(); } catch (IOException ignored) {}
        }
    }
}
