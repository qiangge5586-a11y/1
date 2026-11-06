import com.shilapi.xcertplay.network.safety.HotspotReadOnlyOperation;
import com.shilapi.xcertplay.network.safety.HotspotReadOnlyOperation.Kind;

public final class HotspotReadOnlyOperationTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        HotspotReadOnlyOperation root = HotspotReadOnlyOperation.forKind(Kind.ROOT_IDENTITY);
        check("/system/bin/id".equals(root.executable), "identity executable is fixed");
        check(root.getArguments().length == 0, "identity has no caller arguments");

        HotspotReadOnlyOperation wifi = HotspotReadOnlyOperation.forKind(Kind.WIFI_SERVICE_STATE);
        check("/system/bin/dumpsys".equals(wifi.executable), "wifi executable is fixed");
        check(wifi.getArguments().length == 1 && "wifi".equals(wifi.getArguments()[0]),
                "wifi service name is fixed");
        String[] leaked = wifi.getArguments();
        leaked[0] = "changed";
        check("wifi".equals(wifi.getArguments()[0]), "argument accessor returns defensive copy");

        HotspotReadOnlyOperation config = HotspotReadOnlyOperation.forKind(Kind.WIFI_CONFIG_FILE_METADATA);
        check(config.getArguments().length == 2, "metadata argument count is fixed");
        check("/data/misc/wifi/hostapd.conf".equals(config.getArguments()[1]),
                "only known config metadata path is inspected");

        try {
            HotspotReadOnlyOperation.forKind(null);
            throw new AssertionError("null operation must be rejected");
        } catch (IllegalArgumentException expected) { }

        for (Kind kind : Kind.values()) {
            HotspotReadOnlyOperation op = HotspotReadOnlyOperation.forKind(kind);
            check(op.executable.startsWith("/system/bin/"), "executable path must be absolute and fixed");
        }
        System.out.println("PASS: read-only operation catalog is immutable and fixed");
    }
}
