package com.shilapi.xcertplay.network.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsers for captured diagnostic text. No device or filesystem access. */
public final class HotspotDiagnosticParsers {
    private static final Pattern UID = Pattern.compile("(?:^|\\s)uid=([0-9]+)\\(");
    private static final Pattern GID = Pattern.compile("(?:^|\\s)gid=([0-9]+)\\(");
    private static final Pattern INTERFACE = Pattern.compile("^\\s*([A-Za-z0-9_.:-]+):\\s+(.+)$");
    private static final Pattern MODE = Pattern.compile("[bcdlps-][rwxStTs-]{9}");
    private HotspotDiagnosticParsers() { }

    public static final class Identity {
        public final long uid, gid;
        private Identity(long uid, long gid) { this.uid = uid; this.gid = gid; }
        public boolean isRoot() { return uid == 0L; }
    }
    public static final class InterfaceCounters {
        public final String name;
        public final long[] counters;
        private InterfaceCounters(String name, long[] counters) {
            this.name = name; this.counters = counters.clone();
        }
    }
    public static final class FileMetadata {
        public final String mode;
        public final long uid, gid, size;
        private FileMetadata(String mode, long uid, long gid, long size) {
            this.mode = mode; this.uid = uid; this.gid = gid; this.size = size;
        }
    }

    public static Identity parseIdentity(String output) {
        if (output == null) throw new IllegalArgumentException("identity output missing");
        Matcher u = UID.matcher(output), g = GID.matcher(output);
        if (!u.find() || !g.find()) throw new IllegalArgumentException("uid/gid missing");
        String uidText = u.group(1), gidText = g.group(1);
        if (u.find() || g.find()) throw new IllegalArgumentException("ambiguous uid/gid");
        return new Identity(parseNonNegative(uidText), parseNonNegative(gidText));
    }

    public static List<InterfaceCounters> parseInterfaceTable(String output) {
        if (output == null) throw new IllegalArgumentException("interface output missing");
        String[] lines = output.split("\\r?\\n");
        List<InterfaceCounters> found = new ArrayList<InterfaceCounters>();
        for (String line : lines) {
            Matcher m = INTERFACE.matcher(line);
            if (!m.matches()) continue;
            String[] fields = m.group(2).trim().split("\\s+");
            if (fields.length != 16) throw new IllegalArgumentException("interface counter count invalid");
            long[] values = new long[16];
            for (int i = 0; i < fields.length; i++) values[i] = parseNonNegative(fields[i]);
            String name = m.group(1);
            for (InterfaceCounters prior : found)
                if (prior.name.equals(name)) throw new IllegalArgumentException("duplicate interface");
            found.add(new InterfaceCounters(name, values));
        }
        if (found.isEmpty()) throw new IllegalArgumentException("no interface rows found");
        return Collections.unmodifiableList(found);
    }

    /** Parses one numeric-owner long-list row. Does not establish SELinux context. */
    public static FileMetadata parseNumericLsRow(String output) {
        if (output == null) throw new IllegalArgumentException("metadata output missing");
        String[] lines = output.trim().split("\\r?\\n");
        if (lines.length != 1) throw new IllegalArgumentException("expected exactly one metadata row");
        String[] fields = lines[0].trim().split("\\s+");
        if (fields.length < 8 || !MODE.matcher(fields[0]).matches())
            throw new IllegalArgumentException("metadata row format invalid");
        return new FileMetadata(fields[0], parseNonNegative(fields[2]),
                parseNonNegative(fields[3]), parseNonNegative(fields[4]));
    }

    private static long parseNonNegative(String value) {
        if (value == null || !value.matches("[0-9]+"))
            throw new IllegalArgumentException("non-numeric or negative value");
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("numeric value overflow"); }
    }
}
