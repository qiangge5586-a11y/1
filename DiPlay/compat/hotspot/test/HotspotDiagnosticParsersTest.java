import java.util.List;
import com.shilapi.xcertplay.network.safety.HotspotDiagnosticParsers;
import com.shilapi.xcertplay.network.safety.HotspotDiagnosticParsers.Identity;
import com.shilapi.xcertplay.network.safety.HotspotDiagnosticParsers.InterfaceCounters;
import com.shilapi.xcertplay.network.safety.HotspotDiagnosticParsers.FileMetadata;

public final class HotspotDiagnosticParsersTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void rejects(Runnable r, String message) {
        try { r.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        Identity root = HotspotDiagnosticParsers.parseIdentity("uid=0(root) gid=0(root) groups=0(root)\n");
        check(root.isRoot() && root.gid == 0L, "root identity parsed");
        Identity user = HotspotDiagnosticParsers.parseIdentity("uid=1000(system) gid=1000(system)\n");
        check(!user.isRoot(), "non-root identity distinguished");
        rejects(new Runnable() { public void run() {
            HotspotDiagnosticParsers.parseIdentity("uid=0(root) groups=0(root)");
        }}, "missing gid rejected");
        rejects(new Runnable() { public void run() {
            HotspotDiagnosticParsers.parseIdentity("uid=0(root) gid=0(root) uid=1(x)");
        }}, "ambiguous identity rejected");

        String table = "Inter-| Receive | Transmit\n"
            + " face |bytes packets errs drop fifo frame compressed multicast|bytes packets errs drop fifo colls carrier compressed\n"
            + "  wlan0: 100 2 0 0 0 0 0 0 200 3 0 0 0 0 0 0\n"
            + "  lo: 10 1 0 0 0 0 0 0 10 1 0 0 0 0 0 0\n";
        List<InterfaceCounters> rows = HotspotDiagnosticParsers.parseInterfaceTable(table);
        check(rows.size() == 2 && "wlan0".equals(rows.get(0).name), "interface rows parsed");
        check(rows.get(0).counters[0] == 100L, "counter value parsed");
        rejects(new Runnable() { public void run() {
            HotspotDiagnosticParsers.parseInterfaceTable("wlan0: 1 2 3\n");
        }}, "truncated counters rejected");
        rejects(new Runnable() { public void run() {
            HotspotDiagnosticParsers.parseInterfaceTable("wlan0: 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 -1\n");
        }}, "negative counters rejected");

        FileMetadata meta = HotspotDiagnosticParsers.parseNumericLsRow(
            "-rw------- 1 1000 1010 4096 2026-11-06 08:00 hostapd.conf\n");
        check(meta.uid == 1000L && meta.gid == 1010L && meta.size == 4096L,
                "numeric metadata parsed");
        rejects(new Runnable() { public void run() {
            HotspotDiagnosticParsers.parseNumericLsRow("cannot access: No such file or directory");
        }}, "missing file diagnostic rejected");
        rejects(new Runnable() { public void run() {
            HotspotDiagnosticParsers.parseNumericLsRow("-rw------- 1 system system 20 date time file");
        }}, "non-numeric ownership rejected");
        System.out.println("PASS: diagnostic parsers reject malformed and ambiguous fixtures");
    }
}
