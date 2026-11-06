import com.shilapi.xcertplay.network.safety.Api17SuReadOnlyRunner;
import com.shilapi.xcertplay.network.safety.HotspotReadOnlyOperation;

public final class HotspotApi17SuRunnerTest {
    public static void main(String[] args) {
        HotspotReadOnlyOperation id =
                HotspotReadOnlyOperation.forKind(HotspotReadOnlyOperation.Kind.ROOT_IDENTITY);
        String command = Api17SuReadOnlyRunner.requireCatalogCommand(id.executable, id.getArguments());
        if (!"'/system/bin/id'".equals(command)) throw new AssertionError("fixed identity command");

        HotspotReadOnlyOperation wifi =
                HotspotReadOnlyOperation.forKind(HotspotReadOnlyOperation.Kind.WIFI_SERVICE_STATE);
        String wifiCommand = Api17SuReadOnlyRunner.requireCatalogCommand(wifi.executable, wifi.getArguments());
        if (!"'/system/bin/dumpsys' 'wifi'".equals(wifiCommand))
            throw new AssertionError("wifi command quoting");

        try {
            Api17SuReadOnlyRunner.requireCatalogCommand("/system/bin/sh",
                    new String[] {"-c", "rm -rf /data"});
            throw new AssertionError("arbitrary shell command accepted");
        } catch (SecurityException expected) { }

        try {
            Api17SuReadOnlyRunner.requireCatalogCommand("/system/bin/cat",
                    new String[] {"/data/misc/wifi/hostapd.conf", "; rm -rf /"});
            throw new AssertionError("argument injection accepted");
        } catch (SecurityException expected) { }

        try {
            Api17SuReadOnlyRunner.quote("bad\u0000value");
            throw new AssertionError("NUL accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("PASS: API17 su runner allowlist and argument safety");
    }
}
