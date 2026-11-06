package com.shilapi.xcertplay.compat.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbEndpoint;
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

    private List<UsbConfigurationSnapshot> readConfigurations(UsbDevice device) {
        List<UsbConfigurationSnapshot> configurations =
                new ArrayList<UsbConfigurationSnapshot>();
        int configurationCount = device.getConfigurationCount();
        if (configurationCount < 0) configurationCount = 0;
        if (configurationCount > 32) configurationCount = 32;
        for (int c = 0; c < configurationCount; c++) {
            UsbConfiguration configuration = device.getConfiguration(c);
            if (configuration == null) continue;
            List<UsbConfigurationSnapshot.InterfaceSnapshot> interfaces =
                    new ArrayList<UsbConfigurationSnapshot.InterfaceSnapshot>();
            int interfaceCount = Math.min(Math.max(configuration.getInterfaceCount(), 0), 255);
            for (int i = 0; i < interfaceCount; i++) {
                UsbInterface usbInterface = configuration.getInterface(i);
                if (usbInterface == null) continue;
                List<UsbConfigurationSnapshot.EndpointSnapshot> endpoints =
                        new ArrayList<UsbConfigurationSnapshot.EndpointSnapshot>();
                int endpointCount = Math.min(Math.max(usbInterface.getEndpointCount(), 0), 255);
                for (int e = 0; e < endpointCount; e++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(e);
                    if (endpoint == null) continue;
                    endpoints.add(new UsbConfigurationSnapshot.EndpointSnapshot(
                            endpoint.getAddress(), endpoint.getAttributes(),
                            endpoint.getDirection(), endpoint.getType(),
                            endpoint.getMaxPacketSize(), endpoint.getInterval()));
                }
                interfaces.add(new UsbConfigurationSnapshot.InterfaceSnapshot(
                        usbInterface.getId(), usbInterface.getInterfaceClass(),
                        usbInterface.getInterfaceSubclass(), usbInterface.getInterfaceProtocol(),
                        usbInterface.getAlternateSetting(), endpoints));
            }
            configurations.add(new UsbConfigurationSnapshot(configuration.getId(),
                    configuration.getName(), configuration.getAttributes(),
                    configuration.getMaxPower(), interfaces));
        }
        return configurations;
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
                    List<UsbConfigurationSnapshot> configurations =
                            readConfigurations(device);
                    result.add(new UsbDeviceDescriptor(device.getVendorId(),
                            device.getProductId(), device.getDeviceClass(),
                            device.getDeviceSubclass(), device.getDeviceProtocol(), count,
                            configurations));
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
