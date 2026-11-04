package cn.autohack.rebuild;

import android.os.Build;
import java.util.Arrays;

/** Read-only device facts. This class does not probe privileged APIs or change system state. */
public final class CapabilitySnapshot {
    public final String manufacturer = Build.MANUFACTURER;
    public final String model = Build.MODEL;
    public final String device = Build.DEVICE;
    public final String product = Build.PRODUCT;
    public final int sdk = Build.VERSION.SDK_INT;
    public final String release = Build.VERSION.RELEASE;
    public final String[] abis = Build.SUPPORTED_ABIS.clone();

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
