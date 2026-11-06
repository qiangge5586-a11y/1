package com.shilapi.xcertplay.network.safety;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;

/**
 * Append-only, hash-chained journal. Intended for an app-private directory.
 * Uses an OS file lock and sync() before returning. No ROOT or shell access.
 */
public final class AppendOnlyHotspotJournal implements HotspotTransactionCoordinator.Journal {
    private static final String ZERO_HASH =
        "0000000000000000000000000000000000000000000000000000000000000000";
    private static final long MAX_BYTES = 1024L * 1024L;
    private final File journalFile;

    public static final class Entry {
        public final String transactionId;
        public final HotspotTransactionCoordinator.State state;
        public final long timestampMillis;
        public final String detail;
        public final String hash;
        Entry(String id, HotspotTransactionCoordinator.State s, long t, String d, String h) {
            transactionId=id; state=s; timestampMillis=t; detail=d; hash=h;
        }
    }

    public AppendOnlyHotspotJournal(File appPrivateDirectory) {
        if (appPrivateDirectory == null) throw new IllegalArgumentException("directory required");
        try {
            File dir = appPrivateDirectory.getCanonicalFile();
            if (!dir.isDirectory() || !dir.canWrite())
                throw new IllegalArgumentException("journal directory must already exist and be writable");
            journalFile = new File(dir, "hotspot-transactions.log");
            if (!dir.equals(journalFile.getCanonicalFile().getParentFile()))
                throw new IllegalArgumentException("journal file escapes private directory");
            if (journalFile.exists() && (!journalFile.isFile() || !journalFile.canWrite()))
                throw new IllegalArgumentException("journal file is not a writable regular file");
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("cannot resolve journal directory", e);
        }
    }

    public synchronized void persist(String id, HotspotTransactionCoordinator.State state,
            String detail) throws Exception {
        validateId(id);
        if (state == null) throw new IllegalArgumentException("state required");
        String safeDetail = detail == null ? "" : detail;
        if (safeDetail.length() > 2048) throw new IllegalArgumentException("detail too long");
        String detailHex = toHex(safeDetail.getBytes("UTF-8"));
        RandomAccessFile raf = new RandomAccessFile(journalFile, "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock = null;
        try {
            lock = channel.lock();
            if (raf.length() > MAX_BYTES) throw new java.io.IOException("journal size limit exceeded");
            String previous = lastHash(raf);
            long timestamp = System.currentTimeMillis();
            String body = id + "|" + state.name() + "|" + timestamp + "|" + detailHex + "|" + previous;
            String hash = sha256(body);
            raf.seek(raf.length());
            raf.write((body + "|" + hash + "\n").getBytes("US-ASCII"));
            raf.getFD().sync();
        } finally {
            if (lock != null) lock.release();
            channel.close();
            raf.close();
        }
    }

    /** Returns the latest valid entry, or null for an empty journal. Corruption fails closed. */
    public synchronized Entry readLatest() throws Exception {
        if (!journalFile.exists()) return null;
        RandomAccessFile raf = new RandomAccessFile(journalFile, "r");
        FileChannel channel = raf.getChannel();
        FileLock lock = null;
        try {
            lock = channel.lock(0L, Long.MAX_VALUE, true);
            if (raf.length() > MAX_BYTES) throw new java.io.IOException("journal size limit exceeded");
            Entry latest = null;
            String expectedPrevious = ZERO_HASH;
            while (raf.getFilePointer() < raf.length()) {
                String line = raf.readLine();
                if (line == null || line.length() == 0)
                    throw new java.io.IOException("empty or truncated journal record");
                String[] p = line.split("\\|", -1);
                if (p.length != 6) throw new java.io.IOException("malformed journal record");
                validateId(p[0]);
                HotspotTransactionCoordinator.State state;
                long time;
                try {
                    state = HotspotTransactionCoordinator.State.valueOf(p[1]);
                    time = Long.parseLong(p[2]);
                } catch (RuntimeException e) {
                    throw new java.io.IOException("invalid journal state or timestamp", e);
                }
                if (time <= 0 || !expectedPrevious.equals(p[4]))
                    throw new java.io.IOException("journal chain mismatch");
                String body = p[0] + "|" + p[1] + "|" + p[2] + "|" + p[3] + "|" + p[4];
                String actual = sha256(body);
                if (!actual.equals(p[5])) throw new java.io.IOException("journal checksum mismatch");
                String detail = new String(fromHex(p[3]), "UTF-8");
                latest = new Entry(p[0], state, time, detail, actual);
                expectedPrevious = actual;
            }
            return latest;
        } finally {
            if (lock != null) lock.release();
            channel.close();
            raf.close();
        }
    }

    private String lastHash(RandomAccessFile raf) throws Exception {
        long old = raf.getFilePointer();
        try {
            raf.seek(0L);
            String previous = ZERO_HASH;
            while (raf.getFilePointer() < raf.length()) {
                String line = raf.readLine();
                if (line == null || line.length() == 0)
                    throw new java.io.IOException("empty or truncated journal record");
                String[] p = line.split("\\|", -1);
                if (p.length != 6 || !previous.equals(p[4]))
                    throw new java.io.IOException("journal chain malformed");
                try {
                    validateId(p[0]);
                    HotspotTransactionCoordinator.State.valueOf(p[1]);
                    if (Long.parseLong(p[2]) <= 0) throw new IllegalArgumentException("timestamp");
                    fromHex(p[3]);
                } catch (RuntimeException invalid) {
                    throw new java.io.IOException("journal record fields invalid", invalid);
                }
                String body = p[0] + "|" + p[1] + "|" + p[2] + "|" + p[3] + "|" + p[4];
                String actual = sha256(body);
                if (!actual.equals(p[5])) throw new java.io.IOException("journal checksum mismatch");
                previous = actual;
            }
            return previous;
        } finally {
            raf.seek(old);
        }
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{8,64}"))
            throw new IllegalArgumentException("invalid transaction id");
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
        return toHex(digest);
    }
    private static String toHex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i=0;i<bytes.length;i++) {
            int v=bytes[i]&255; out[i*2]=alphabet[v>>>4]; out[i*2+1]=alphabet[v&15];
        }
        return new String(out);
    }
    private static byte[] fromHex(String value) throws java.io.IOException {
        if (value.length() % 2 != 0) throw new java.io.IOException("odd hex length");
        byte[] out = new byte[value.length()/2];
        for (int i=0;i<out.length;i++) {
            int hi=Character.digit(value.charAt(i*2),16), lo=Character.digit(value.charAt(i*2+1),16);
            if (hi<0||lo<0) throw new java.io.IOException("invalid hex detail");
            out[i]=(byte)((hi<<4)|lo);
        }
        return out;
    }
}
