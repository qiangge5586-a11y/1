package cn.autohack.rebuild;

import java.util.ArrayList;
import java.util.List;

/** Builds a strictly read-only SAE J1979 Mode 01 polling plan for an ELM327 adapter. */
public final class ObdPidQueryPlan {
    private static final int[] DISPLAY_PIDS = {
            0x04, 0x05, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11,
            0x2F, 0x33, 0x5C, 0x5E
    };

    private ObdPidQueryPlan() {}

    /** First asks the ECU which standard Mode 01 PIDs it supports. */
    public static String initialBitmapCommand() {
        return "0100";
    }

    /**
     * Returns the next supported-PID bitmap query when the current bitmap advertises one.
     * Only the standard 0100 -> 0120 -> 0140 discovery chain is used.
     */
    public static String nextBitmapCommand(String response, int bitmapBasePid) {
        if (bitmapBasePid != 0x00 && bitmapBasePid != 0x20) return null;
        int nextPid = bitmapBasePid + 0x20;
        return isPidSupported(response, bitmapBasePid, nextPid)
                ? String.format(java.util.Locale.ROOT, "01%02X", nextPid) : null;
    }

    /** Returns supported dashboard PIDs in a stable order; absent/malformed bitmaps add none. */
    public static List<String> supportedReadCommands(
            String bitmap00, String bitmap20, String bitmap40) {
        List<String> commands = new ArrayList<>();
        for (int pid : DISPLAY_PIDS) {
            boolean supported = pid <= 0x20
                    ? isPidSupported(bitmap00, 0x00, pid)
                    : pid <= 0x40
                    ? isPidSupported(bitmap20, 0x20, pid)
                    : isPidSupported(bitmap40, 0x40, pid);
            if (supported) commands.add(String.format(java.util.Locale.ROOT, "01%02X", pid));
        }
        return commands;
    }

    static boolean isPidSupported(String response, int bitmapBasePid, int pid) {
        if (response == null || pid <= bitmapBasePid || pid > bitmapBasePid + 0x20) {
            return false;
        }
        int[] bytes = Elm327ResponseParser.parsePidBytes(response, bitmapBasePid, 4);
        if (bytes == null) return false;
        long mask = ((long) bytes[0] << 24) | ((long) bytes[1] << 16)
                | ((long) bytes[2] << 8) | (long) bytes[3];
        int bit = 32 - (pid - bitmapBasePid);
        return ((mask >>> bit) & 1L) != 0L;
    }
}
