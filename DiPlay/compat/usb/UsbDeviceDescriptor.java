package com.shilapi.xcertplay.compat.usb;

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

    public UsbDeviceDescriptor(int vendorId, int productId, int deviceClass,
            int deviceSubclass, int deviceProtocol, int interfaceCount) {
        requireUnsigned16("vendorId", vendorId);
        requireUnsigned16("productId", productId);
        requireUnsigned8("deviceClass", deviceClass);
        requireUnsigned8("deviceSubclass", deviceSubclass);
        requireUnsigned8("deviceProtocol", deviceProtocol);
        requireUnsigned8("interfaceCount", interfaceCount);
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
