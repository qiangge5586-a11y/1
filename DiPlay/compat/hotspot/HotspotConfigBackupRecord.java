package com.shilapi.xcertplay.network.safety;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Immutable description of a captured config backup. It performs no file I/O.
 * Callers must supply bytes and independently captured metadata.
 */
public final class HotspotConfigBackupRecord {
    public static final int MAX_BACKUP_BYTES = 1024 * 1024;

    public final boolean originalExisted;
    public final int originalSize;
    public final String originalSha256;
    public final int uid;
    public final int gid;
    public final int mode;
    public final String selinuxContext;
    public final boolean contextRequired;
    public final String backupPath;

    private HotspotConfigBackupRecord(boolean existed, int size, String hash,
            int uid, int gid, int mode, String context, boolean contextRequired,
            String backupPath) {
        this.originalExisted = existed;
        this.originalSize = size;
        this.originalSha256 = hash;
        this.uid = uid;
        this.gid = gid;
        this.mode = mode;
        this.selinuxContext = context;
        this.contextRequired = contextRequired;
        this.backupPath = backupPath;
    }

    public static HotspotConfigBackupRecord capture(boolean originalExisted,
            byte[] originalBytes, int uid, int gid, int mode, String selinuxContext,
            boolean contextRequired, String backupPath, String approvedBackupRoot) {
        if (!originalExisted) {
            if (originalBytes != null && originalBytes.length != 0)
                throw new IllegalArgumentException("absent original cannot have backup bytes");
            if (backupPath != null && backupPath.length() != 0)
                throw new IllegalArgumentException("absent original must not claim a backup file");
            return new HotspotConfigBackupRecord(false, 0, null, -1, -1, -1,
                    null, contextRequired, null);
        }
        if (originalBytes == null) throw new IllegalArgumentException("original bytes missing");
        if (originalBytes.length > MAX_BACKUP_BYTES)
            throw new IllegalArgumentException("backup exceeds size limit");
        if (uid < 0 || gid < 0) throw new IllegalArgumentException("numeric owner metadata required");
        if (mode < 0 || mode > 07777) throw new IllegalArgumentException("invalid file mode");
        if (contextRequired && (selinuxContext == null || selinuxContext.length() == 0))
            throw new IllegalArgumentException("required SELinux context missing");
        String safePath = HotspotSafetyPrimitives.requirePathUnder(backupPath, approvedBackupRoot);
        return new HotspotConfigBackupRecord(true, originalBytes.length, sha256(originalBytes),
                uid, gid, mode, selinuxContext, contextRequired, safePath);
    }

    public boolean verifies(byte[] backupBytes) {
        if (!originalExisted) return backupBytes == null || backupBytes.length == 0;
        if (backupBytes == null || backupBytes.length != originalSize) return false;
        return originalSha256.equals(sha256(backupBytes));
    }

    public boolean isRestorable() {
        if (!originalExisted) return originalSize == 0 && originalSha256 == null
                && backupPath == null;
        return originalSize >= 0 && originalSize <= MAX_BACKUP_BYTES
                && originalSha256 != null && originalSha256.matches("[0-9a-f]{64}")
                && uid >= 0 && gid >= 0 && mode >= 0 && mode <= 07777
                && (!contextRequired || (selinuxContext != null && selinuxContext.length() > 0))
                && backupPath != null && backupPath.length() > 0;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(64);
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xff;
                if (value < 16) out.append('0');
                out.append(Integer.toHexString(value));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
