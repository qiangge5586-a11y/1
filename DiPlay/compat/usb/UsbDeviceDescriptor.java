package com.shilapi.xcertplay.compat.usb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Passive USB descriptor value object.
 * No USB permission, endpoint access or device I/O.
 */
public final class UsbDeviceDescriptor {
    private final int vendorId;
    private final int productId;
    private final int deviceClass;
    private final int deviceSubclass;
    private final int deviceProtocol;
    private final int interfaceCount;
    private final List<UsbConfigurationSnapshot> configurations;

    public UsbDeviceDescriptor(int vendorId, int productId, int deviceClass,
            int deviceSubclass, int deviceProtocol, int interfaceCount) {
        this(vendorId, productId, deviceClass, deviceSubclass, deviceProtocol,
                interfaceCount, Collections.<UsbConfigurationSnapshot>emptyList());
    }

    public UsbDeviceDescriptor(int vendorId, int productId, int deviceClass,
            int deviceSubclass, int deviceProtocol, int interfaceCount,
            List<UsbConfigurationSnapshot> configurations) {
        requireUnsigned16("vendorId", vendorId);
        requireUnsigned16("productId", productId);
        requireUnsigned8("deviceClass", deviceClass);
        requireUnsigned8("deviceSubclass", deviceSubclass);
        requireUnsigned8("deviceProtocol", deviceProtocol);
        requireUnsigned8("interfaceCount", interfaceCount);
        if (configurations == null) throw new NullPointerException("configurations");
        this.configurations = Collections.unmodifiableList(
                new ArrayList<UsbConfigurationSnapshot>(configurations));
        for (UsbConfigurationSnapshot configuration : this.configurations) {
            if (configuration == null) throw new IllegalArgumentException("null configuration");
        }
        this.vendorId = vendorId;
        this.productId = productId;
        this.deviceClass = deviceClass;
        this.deviceSubclass = deviceSubclass;
        this.deviceProtocol = deviceProtocol;
        this.interfaceCount = interfaceCount;
    }

    private static void requireUnsigned16(String name, int value) {
        if (value < 0 || value > 65535) throw new IllegalArgumentException(name + " out of range");
    }
    private static void requireUnsigned8(String name, int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException(name + " out of range");
    }
    public int getVendorId() { return vendorId; }
    public int getProductId() { return productId; }
    public int getDeviceClass() { return deviceClass; }
    public int getDeviceSubclass() { return deviceSubclass; }
    public int getDeviceProtocol() { return deviceProtocol; }
    public int getInterfaceCount() { return interfaceCount; }
    public List<UsbConfigurationSnapshot> getConfigurations() { return configurations; }
    public int getInterfaceGroupCount() { return configurations.size(); }
    /** Compatibility alias; this counts snapshot groups, not USB configurations. */
    public int getConfigurationCount() { return configurations.size(); }

    public String getDescriptorKey() {
        return hex4(vendorId) + ":" + hex4(productId) + ":"
                + hex2(deviceClass) + ":" + hex2(deviceSubclass) + ":"
                + hex2(deviceProtocol) + ":" + interfaceCount;
    }
    private static String hex4(int value) {
        String s = Integer.toHexString(value).toUpperCase();
        return "0000".substring(s.length()) + s;
    }
    private static String hex2(int value) {
        String s = Integer.toHexString(value).toUpperCase();
        return "00".substring(s.length()) + s;
    }
}
