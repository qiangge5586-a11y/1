package cn.autohack.rebuild;

import android.os.Build;
import java.util.Arrays;

/** Read-only device facts. This class does not probe privileged APIs or change system state. */
public final class CapabilitySnapshot {
    public final String manufacturer = Build.MANUFACTURER;
    public final String model = Build.MODEL;
    public final String device = Build.DEVICE;
    public final String product = Build.PRODUCT;
    public final String board = Build.BOARD;
    public final String hardware = Build.HARDWARE;
    public final String fingerprint = Build.FINGERPRINT;
    public final int sdk = Build.VERSION.SDK_INT;
    public final String release = Build.VERSION.RELEASE;
    public final String[] abis = Build.VERSION.SDK_INT >= 21
            ? Build.SUPPORTED_ABIS.clone() : legacyAbis();

    private static String[] legacyAbis() {
        String primary = Build.CPU_ABI == null ? "" : Build.CPU_ABI;
        String secondary = Build.CPU_ABI2 == null ? "" : Build.CPU_ABI2;
        if (secondary.isEmpty() || secondary.equals(primary)) return new String[]{primary};
        return new String[]{primary, secondary};
    }

    public String toDisplayString() {
        return "Manufacturer: " + manufacturer
                + "\nModel: " + model
                + "\nDevice: " + device
                + "\nProduct: " + product
                + "\nAndroid: " + release + " (API " + sdk + ")"
                + "\nABIs: " + Arrays.toString(abis)
                + "\nRoot access: not tested (no commands executed)";
    }
}
