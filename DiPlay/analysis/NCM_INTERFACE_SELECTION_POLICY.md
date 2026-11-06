# CDC-NCM interface selection policy review

Date: 2026-11-06

## Original DiPlay behavior inspected

Static inspection of the readable JADX output for `com.shilapi.xcertplay.transport.NcmFunctionDiscovery` shows this selection policy:

1. Select the first interface in configuration order with class 2 / subclass 13 as the CDC control interface.
2. Consider data interfaces with class 10 only when they expose exactly one bulk IN endpoint (direction 128, type 2) and exactly one bulk OUT endpoint (direction 0, type 2). Duplicate bulk endpoints in either direction make that candidate ineligible.
3. Among eligible data interfaces, prefer alternate setting 1. If more than one candidate has the same preference, the original stable minimum selection keeps the first candidate in configuration order.
4. For claiming, select the first interface with the same interface ID as the chosen data interface and alternate setting 0; if absent, use the chosen data interface itself.
5. On the control interface, expose a status endpoint only when exactly one interrupt IN endpoint (direction 128, type 3) exists. Zero or multiple matching endpoints yield no status endpoint, but do not invalidate the NCM function.

The source was recovered from JADX Java-like output. The original APK reports 104 decompilation errors overall, so this is a static-analysis conclusion and not a lossless source guarantee.

## Compatibility implementation

- `DiPlay/compat/ncm/NcmInterfaceSelector.java` models the descriptor data using plain Java objects.
- It has no Android USB imports and performs no USB permission request, open, claim, alternate-setting change, transfer, or ROOT operation.
- `NcmInterfaceSelectorTest.java` covers nine deterministic fixtures for the selection rules above. The selected endpoint model also preserves descriptor address and maximum packet size.
- The model is compiled into the isolated Android compatibility app and fed by the passive USB snapshot matcher. Selected endpoint address and maximum packet size are retained, but the result is not connected to any USB I/O path.

## Remaining integration gates

1. Compare the pure-Java selector against additional captured descriptors from Apple iPhone CarPlay configurations without opening the interface.
2. Preserve original configuration order and duplicate endpoint behavior when translating Android `UsbInterface` descriptors into the pure model.
3. Keep interface claiming and alternate-setting changes disabled until API 17 framework/JNI compatibility and device ownership behavior are separately validated.
4. Validate the selected alternate setting and endpoint max-packet sizes on a controlled test device before any Honda MY16ADA hardware test.

No live USB device or Honda head unit was accessed for this review.
