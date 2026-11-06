package com.shilapi.xcertplay.compat.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Read-only USB Host enumeration for API 12+.
 * It only reads descriptors exposed by UsbManager; it never opens a device,
 * requests permission, claims interfaces, accesses endpoints, or transfers data.
 */
public final class UsbHostReadOnlyScanner {
    public static final class ScanResult {
        private final List<UsbDeviceDescriptor> devices;
        private final String status;

        private ScanResult(List<UsbDeviceDescriptor> devices, String status) {
            this.devices = devices;
            this.status = status;
        }

        public List<UsbDeviceDescriptor> getDevices() { return devices; }
        public String getStatus() { return status; }
    }

    public ScanResult scan(Context context) {
        if (context == null) {
            return new ScanResult(new ArrayList<UsbDeviceDescriptor>(), "上下文不可用");
        }
        Object service = context.getSystemService(Context.USB_SERVICE);
        if (!(service instanceof UsbManager)) {
            return new ScanResult(new ArrayList<UsbDeviceDescriptor>(), "系统未提供 USB Host 服务");
        }
        try {
            HashMap<String, UsbDevice> found = ((UsbManager) service).getDeviceList();
            List<UsbDeviceDescriptor> result = new ArrayList<UsbDeviceDescriptor>();
            if (found != null) {
                for (UsbDevice device : found.values()) {
                    if (device == null) continue;
                    int count = device.getInterfaceCount();
                    if (count < 0) count = 0;
                    if (count > 255) count = 255;
                    result.add(new UsbDeviceDescriptor(device.getVendorId(),
                            device.getProductId(), device.getDeviceClass(),
                            device.getDeviceSubclass(), device.getDeviceProtocol(), count));
                }
            }
            return new ScanResult(result, result.isEmpty()
                    ? "未发现 USB 设备（只读枚举）"
                    : "发现 " + result.size() + " 个 USB 设备（只读枚举）");
        } catch (SecurityException denied) {
            return new ScanResult(new ArrayList<UsbDeviceDescriptor>(), "USB 设备列表读取被系统拒绝");
        } catch (RuntimeException unavailable) {
            return new ScanResult(new ArrayList<UsbDeviceDescriptor>(), "USB Host 服务暂不可用");
        }
    }
}
