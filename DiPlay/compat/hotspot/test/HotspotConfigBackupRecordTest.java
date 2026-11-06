import com.shilapi.xcertplay.network.safety.HotspotConfigBackupRecord;

public final class HotspotConfigBackupRecordTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void rejects(Runnable r, String message) {
        try { r.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        final byte[] original = "interface=wlan0\nssid=fixture\n".getBytes();
        HotspotConfigBackupRecord record = HotspotConfigBackupRecord.capture(true,
                original, 1000, 1010, 0600, "u:object_r:wifi_data_file:s0", true,
                "/data/data/com.shihab.diplay/files/backup/hostapd.conf",
                "/data/data/com.shihab.diplay/files/backup");
        check(record.isRestorable(), "complete record is restorable");
        check(record.verifies(original), "exact bytes verify");
        check(!record.verifies("changed".getBytes()), "changed bytes rejected");
        check(!record.verifies(new byte[0]), "wrong length rejected");

        HotspotConfigBackupRecord absent = HotspotConfigBackupRecord.capture(false,
                null, -1, -1, -1, null, true, null,
                "/data/data/com.shihab.diplay/files/backup");
        check(absent.isRestorable() && absent.verifies(null),
                "originally absent config is represented as absence, not empty file");
        rejects(new Runnable() { public void run() {
            HotspotConfigBackupRecord.capture(false, new byte[] {1}, -1, -1, -1,
                    null, false, null, "/data/data/com.shihab.diplay/files/backup");
        }}, "absent original with bytes rejected");
        rejects(new Runnable() { public void run() {
            HotspotConfigBackupRecord.capture(true, null, 1000, 1000, 0600,
                    "ctx", true, "/data/data/com.shihab.diplay/files/backup/x",
                    "/data/data/com.shihab.diplay/files/backup");
        }}, "missing original bytes rejected");
        rejects(new Runnable() { public void run() {
            HotspotConfigBackupRecord.capture(true, original, 1000, 1000, 0600,
                    null, true, "/data/data/com.shihab.diplay/files/backup/x",
                    "/data/data/com.shihab.diplay/files/backup");
        }}, "missing required SELinux context rejected");
        rejects(new Runnable() { public void run() {
            HotspotConfigBackupRecord.capture(true, original, 1000, 1000, 0600,
                    "ctx", false, "/sdcard/backup/x",
                    "/data/data/com.shihab.diplay/files/backup");
        }}, "backup path outside app-private root rejected");
        rejects(new Runnable() { public void run() {
            HotspotConfigBackupRecord.capture(true, new byte[HotspotConfigBackupRecord.MAX_BACKUP_BYTES + 1],
                    1000, 1000, 0600, "ctx", true,
                    "/data/data/com.shihab.diplay/files/backup/x",
                    "/data/data/com.shihab.diplay/files/backup");
        }}, "oversized backup rejected");
        System.out.println("PASS: config backup record hashes, metadata and absence fail-closed");
    }
}
