package com.shilapi.xcertplay.network.safety;

/**
 * Read-only operation catalog for the future API-17 adapter.
 * This class creates descriptions only. It does not execute commands,
 * access files, start processes, or change device state.
 */
public final class HotspotReadOnlyOperation {
    public enum Kind {
        ROOT_IDENTITY,
        WIFI_SERVICE_STATE,
        CONNECTIVITY_SERVICE_STATE,
        NETWORK_INTERFACE_TABLE,
        WIFI_CONFIG_FILE_METADATA,
        HOSTAPD_BINARY_METADATA,
        DNSMASQ_BINARY_METADATA
    }

    public final Kind kind;
    public final String executable;
    public final String[] arguments;

    private HotspotReadOnlyOperation(Kind kind, String executable, String[] arguments) {
        this.kind = kind;
        this.executable = executable;
        this.arguments = arguments.clone();
    }

    public static HotspotReadOnlyOperation forKind(Kind kind) {
        if (kind == null) throw new IllegalArgumentException("kind required");
        switch (kind) {
            case ROOT_IDENTITY:
                return new HotspotReadOnlyOperation(kind, "/system/bin/id", new String[0]);
            case WIFI_SERVICE_STATE:
                return new HotspotReadOnlyOperation(kind, "/system/bin/dumpsys", new String[] {"wifi"});
            case CONNECTIVITY_SERVICE_STATE:
                return new HotspotReadOnlyOperation(kind, "/system/bin/dumpsys", new String[] {"connectivity"});
            case NETWORK_INTERFACE_TABLE:
                return new HotspotReadOnlyOperation(kind, "/system/bin/cat", new String[] {"/proc/net/dev"});
            case WIFI_CONFIG_FILE_METADATA:
                return new HotspotReadOnlyOperation(kind, "/system/bin/ls", new String[] {"-ln", "/data/misc/wifi/hostapd.conf"});
            case HOSTAPD_BINARY_METADATA:
                return new HotspotReadOnlyOperation(kind, "/system/bin/ls", new String[] {"-l", "/system/bin/hostapd", "/system/xbin/hostapd"});
            case DNSMASQ_BINARY_METADATA:
                return new HotspotReadOnlyOperation(kind, "/system/bin/ls", new String[] {"-l", "/system/bin/dnsmasq", "/system/xbin/dnsmasq"});
            default:
                throw new IllegalArgumentException("unsupported operation");
        }
    }
}
