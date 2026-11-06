# Passive USB Host enumeration

This module provides a read-only first look at USB devices attached to Android USB Host.

- API baseline: Android 3.1 / API 12 or newer; the target MY16ADA report identifies API 17.
- Uses only UsbManager.getDeviceList() and UsbDevice descriptor getters.
- Captures VID, PID, device class/subclass/protocol, interface classes, endpoint direction/type/address/max-packet-size/interval, and the interface count.
- Uses only API-12 public descriptor getters for the API-17 target. Android documents `UsbDevice.getConfigurationCount/getConfiguration`, `UsbConfiguration`, and `UsbInterface.getAlternateSetting` as API 21+, so this scanner does not link against them. It records a device-level interface group and marks alternate setting as unknown when the API does not expose it.
- Does not request USB permission, open devices, claim interfaces, perform endpoint I/O, transfer data, or change device state. It does read endpoint descriptor metadata.
- Device paths and serial numbers are deliberately not collected or displayed.
- A missing USB Host service or empty list is reported as unavailable/no device, not treated as proof of hardware incompatibility.

The scanner is wired to the Chinese dashboard as a manual read-only scan. A pure-Java matcher can identify a CDC-NCM-shaped interface set from available descriptors and retains the selected bulk endpoint addresses and maximum packet sizes, but on API 17 alternate-setting metadata is unavailable, so the result is explicitly incomplete and must not be treated as a confirmed usable NCM function. Actual device results still require testing on the API-17 Honda head unit. USB descriptor enumeration does not identify CarPlay, iAP2, or any vendor-specific protocol by itself.
