package com.shilapi.xcertplay.network.safety;

import java.io.UnsupportedEncodingException;

/**
 * Pure Java 7 safety primitives. This class performs no I/O and executes no
 * shell commands; production RootOps must use these checks before any change.
 */
public final class HotspotSafetyPrimitives {
    private HotspotSafetyPrimitives() {}

    public static final class BackupMetadata {
        public final boolean originalExisted;
        public final String sha256;
        public final int uid;
        public final int gid;
        public final int mode;
        public final String selinuxContext;
        public final boolean existenceCaptured;
        public final boolean ownerCaptured;
        public final boolean modeCaptured;
        public final boolean contextRequired;

        public BackupMetadata(boolean existed, String hash, int ownerUid, int ownerGid,
                int fileMode, String context, boolean existenceWasCaptured,
                boolean ownerWasCaptured, boolean modeWasCaptured, boolean requireContext) {
            originalExisted = existed;
            sha256 = hash;
            uid = ownerUid;
            gid = ownerGid;
            mode = fileMode;
            selinuxContext = context;
            existenceCaptured = existenceWasCaptured;
            ownerCaptured = ownerWasCaptured;
            modeCaptured = modeWasCaptured;
            contextRequired = requireContext;
        }

        public boolean isRestorable() {
            if (!existenceCaptured) return false;
            if (!originalExisted) return sha256 == null;
            if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) return false;
            if (!ownerCaptured || uid < 0 || gid < 0) return false;
            if (!modeCaptured || mode < 0 || mode > 07777) return false;
            if (contextRequired && (selinuxContext == null || selinuxContext.length() == 0)) return false;
            return true;
        }
    }

    /**
     * Parses Linux /proc/<pid>/stat starttime (field 22). The comm field may
     * contain spaces and ')' characters, so parsing must begin after its last ')'.
     */
    public static long parseProcStartTicks(String statLine) {
        if (statLine == null) throw new IllegalArgumentException("stat line missing");
        int close = statLine.lastIndexOf(')');
        if (close < 0 || close + 1 >= statLine.length())
            throw new IllegalArgumentException("malformed proc stat");
        String tail = statLine.substring(close + 1).trim();
        String[] fields = tail.split("\\s+");
        if (fields.length <= 19) throw new IllegalArgumentException("proc stat lacks starttime");
        try {
            long ticks = Long.parseLong(fields[19]);
            if (ticks <= 0) throw new IllegalArgumentException("invalid starttime");
            return ticks;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid proc starttime", e);
        }
    }

    public static String decodeCmdline(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("cmdline missing");
        int end = bytes.length;
        while (end > 0 && bytes[end - 1] == 0) end--;
        if (end == 0) throw new IllegalArgumentException("cmdline empty");
        try {
            String value = new String(bytes, 0, end, "UTF-8").replace('\u0000', ' ');
            if (value.trim().length() == 0) throw new IllegalArgumentException("cmdline empty");
            return value;
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 unavailable", impossible);
        }
    }

    /** Exact identity comparison prevents signalling a reused PID or unrelated daemon. */
    public static boolean isSameProcess(int expectedPid, long expectedStartTicks,
            String expectedCmdline, int currentPid, long currentStartTicks, String currentCmdline) {
        return expectedPid > 1 && expectedStartTicks > 0 && expectedCmdline != null
                && expectedCmdline.length() > 0 && expectedPid == currentPid
                && expectedStartTicks == currentStartTicks
                && expectedCmdline.equals(currentCmdline);
    }

    /**
     * Rejects traversal and paths outside an explicitly approved absolute root.
     * This validates a path only; it does not make a shell command or perform I/O.
     */
    public static String requirePathUnder(String path, String approvedRoot) {
        if (path == null || approvedRoot == null || !approvedRoot.startsWith("/")
                || !path.startsWith("/") || path.indexOf('\u0000') >= 0
                || path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0)
            throw new IllegalArgumentException("invalid absolute path");
        String root = trimTrailingSlash(approvedRoot);
        if (path.equals(root) || !path.startsWith(root + "/"))
            throw new IllegalArgumentException("path outside approved root");
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("..".equals(parts[i]) || ".".equals(parts[i]))
                throw new IllegalArgumentException("path traversal");
        }
        return path;
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 1 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }
}
