# Passive USB Host enumeration

This module provides a read-only first look at USB devices attached to Android USB Host.

- API baseline: Android 3.1 / API 12 or newer; the target MY16ADA report identifies API 17.
- Uses only UsbManager.getDeviceList() and UsbDevice descriptor getters.
- Captures VID, PID, device class/subclass/protocol and interface count.
- Does not request USB permission, open devices, claim interfaces, access endpoints, transfer data, or change device state.
- Device paths and serial numbers are deliberately not collected or displayed.
- A missing USB Host service or empty list is reported as unavailable/no device, not treated as proof of hardware incompatibility.

The scanner is wired to the Chinese dashboard as a manual read-only scan. Actual device results still require testing on the API-17 Honda head unit. USB descriptor enumeration does not identify CarPlay, NCM, or any vendor-specific protocol.
