import com.shilapi.xcertplay.network.safety.HotspotSafetyPrimitives;
import com.shilapi.xcertplay.network.safety.HotspotSafetyPrimitives.BackupMetadata;

public final class HotspotSafetyPrimitivesTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        BackupMetadata complete = new BackupMetadata(true,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            1000, 1000, 0600, "u:object_r:wifi_data_file:s0", true, true, true, true);
        check(complete.isRestorable(), "complete backup metadata");
        check(!new BackupMetadata(true, null, 1000, 1000, 0600, null,
            true, true, true, false).isRestorable(), "missing hash must fail closed");
        check(!new BackupMetadata(false, null, -1, -1, -1, null,
            false, false, false, false).isRestorable(), "missing existence snapshot must fail closed");
        check(new BackupMetadata(false, null, -1, -1, -1, null,
            true, false, false, false).isRestorable(), "captured originally-absent file");

        StringBuilder tail = new StringBuilder("S");
        for (int i = 1; i < 19; i++) tail.append(" 0");
        tail.append(" 9876");
        long ticks = HotspotSafetyPrimitives.parseProcStartTicks("321 (hostapd (owned)) " + tail);
        check(ticks == 9876L, "proc stat parser handles ')' inside comm");
        rejects(new Runnable() { public void run() {
            HotspotSafetyPrimitives.parseProcStartTicks("321 broken");
        }}, "malformed proc stat rejected");

        check(HotspotSafetyPrimitives.isSameProcess(321, 9876L, "/x/hostapd -f cfg",
            321, 9876L, "/x/hostapd -f cfg"), "same process identity");
        check(!HotspotSafetyPrimitives.isSameProcess(321, 9876L, "/x/hostapd -f cfg",
            321, 9877L, "/x/hostapd -f cfg"), "PID reuse rejected");
        check(!HotspotSafetyPrimitives.isSameProcess(321, 9876L, "/x/hostapd -f cfg",
            321, 9876L, "/system/bin/hostapd"), "command mismatch rejected");

        check("/data/local/tmp/txn/config".equals(
            HotspotSafetyPrimitives.requirePathUnder("/data/local/tmp/txn/config", "/data/local/tmp")),
            "approved path");
        rejects(new Runnable() { public void run() {
            HotspotSafetyPrimitives.requirePathUnder("/data/local/tmp/../misc/wifi/hostapd.conf", "/data/local/tmp");
        }}, "traversal rejected");
        rejects(new Runnable() { public void run() {
            HotspotSafetyPrimitives.requirePathUnder("/data/misc/wifi/hostapd.conf", "/data/local/tmp");
        }}, "outside root rejected");
        System.out.println("PASS: backup metadata, proc identity, PID reuse and path allowlist");
    }
}
