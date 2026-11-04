package cn.autohack.rebuild;

import java.util.Locale;

/** Parses standard ELM327 ISO 15765 response payloads without opening a Bluetooth connection. */
public final class Elm327ResponseParser {
    private Elm327ResponseParser() {}

    /**
     * Finds the 0x41 + PID positive-response marker and returns the following data bytes.
     * Accepts spaced/unspaced responses, optional CAN headers, command echo and ELM prompt.
     */
    public static int[] parsePidBytes(String response, int pid, int byteCount) {
        if (response == null || pid < 0 || pid > 0xFF || byteCount < 1 || byteCount > 4) {
            return null;
        }
        String compact = response.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
        String marker = String.format(Locale.ROOT, "41%02X", pid);
        int markerIndex = compact.indexOf(marker);
        if (markerIndex < 0) return null;
        int dataStart = markerIndex + marker.length();
        int dataEnd = dataStart + byteCount * 2;
        if (dataEnd > compact.length()) return null;
        int[] bytes = new int[byteCount];
        try {
            for (int i = 0; i < byteCount; i++) {
                int start = dataStart + i * 2;
                bytes[i] = Integer.parseInt(compact.substring(start, start + 2), 16);
            }
            return bytes;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
