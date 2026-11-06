# DiPlay API 17 compatibility audit (static review)

Date: 2026-11-06

## Scope

Reviewed the isolated prototype at `DiPlay/android`, including its manifest, app Gradle configuration and current Chinese dashboard Activity. This is a source-level compatibility review only. It is not a runtime test on Android 4.2.2 or the Honda MY16ADA head unit.

## Findings

- `minSdk 17` is declared, matching the report baseline Android 4.2.2 / API 17.
- The app uses Java 7 source and target compatibility.
- The launcher Activity currently uses long-established framework classes and methods: `Activity`, `Bundle`, `ScrollView`, `LinearLayout`, `TextView`, `ViewGroup.LayoutParams`, display density, text sizing, padding, background colors and `setContentView`.
- The Activity does not currently call `su`, start a process, access Wi-Fi APIs, enumerate USB devices, access Bluetooth, read system files, or modify system settings.
- The manifest requests no runtime permissions. Its launcher Activity is explicitly landscape.
- `android:screenSize` in `configChanges` has existed since API 13; `supportsRtl` is available from API 17.
- The build uses compile SDK 35 and target SDK 28 while retaining min SDK 17. Build-time success and AAPT metadata checks prove package metadata, not that every framework behavior is identical on the target firmware.

## Compatibility gates still open

1. Install and launch on an actual API 17 emulator/device. No such test has been completed.
2. Check the actual firmware's package installer behavior and launcher registration.
3. Verify 800x480 rendering, density, font fallback, scrolling and touch targets on the factory screen.
4. Inspect all transitive/native libraries before adding them. The original DiPlay APK contains native libraries whose minimum Android API compatibility is not established by this prototype.
5. Add features behind explicit API-level checks and keep API 17 implementations free of newer framework references at class-load time.
6. Validate USB Host availability and permissions, Bluetooth stack behavior, and Honda-specific services independently before porting those features.

## USB module next-step design

The next implementation should remain a passive, isolated USB inventory module:
- model USB device identity using vendor ID, product ID, device class/subclass/protocol and interface descriptors;
- use Android USB Host APIs only after checking `PackageManager.FEATURE_USB_HOST` and API 12+ availability;
- request user permission through the platform USB permission flow; do not silently claim access;
- do not open endpoints, send control/bulk transfers, claim interfaces, or alter attached device state in the first stage;
- provide fixture-based parser/model tests that run without a physical USB accessory;
- keep USB device discovery separate from CarPlay/NCM protocol handling.

## Conclusion

The current dashboard source appears to use API-17-era framework functionality and compiles as a minSdk-17 APK. API 17 runtime compatibility remains **unverified** until installed and launched on an API 17 environment and then on the target head unit. No ROOT, hotspot, USB, Bluetooth or CarPlay hardware operation was performed during this review.


## Original DiPlay 0.3.11 protocol-source review (2026-11-06)

The original decompiled artifact was retrieved and inspected, so the following observations are based on concrete source methods rather than class names alone. This does not change the runtime-unverified status above.

### USB Host / iAP2 / NCM

- `IphoneUsbHost.openIap2UsbSession()` uses `UsbManager.openDevice()`, selects the CarPlay configuration, finds USBMUX bulk endpoints, and claims the USBMUX interface. The class explicitly waits 400 ms when SDK < 21 before opening the device.
- The Android USB Host framework is available from API 12, so API 17 has the public API surface needed for basic device enumeration and USB connection. Actual target firmware behavior, permission UI, host controller, and iPhone descriptor layout remain unknown.
- `NcmUsbBridge` gates asynchronous `UsbRequest` reads on real SDK >= 24; API 17 therefore follows its synchronous bulkTransfer path. This avoids a newer async code path, but sustained throughput, timeout behavior, endpoint halt recovery and kernel-driver ownership have not been measured on Honda firmware.
- `DirectUsbHost` is not the same as the public USB Host path: it opens a sysfs-selected usbfs node through `RawSharedUsbConnection`, reads raw descriptors, and can send vendor request 82/index 4 to request re-enumeration. Its own diagnostic says root grant is allowed. This requires explicit root/device-node permissions and is not appropriate for the current passive scanner.

### Bluetooth RFCOMM

- The wireless controller uses the iAP2 iPhone RFCOMM service UUID `00000000-deca-fade-deca-deafdecacafe`.
- `AndroidBluetoothAdapter` tries secure RFCOMM, then insecure RFCOMM, then a reflective hidden API `createInsecureRfcommSocket(1)`. The public Bluetooth socket APIs predate API 17, but the reflective fallback is ROM-dependent and must be isolated and tested on the Honda Bluetooth stack.
- The adapter performs socket connect on a worker thread with a default 10-second wait and wraps the result in a duplex stream with a reader thread. The controller adds its own retry and connection diagnostics.
- Bluetooth discovery, bond selection, permissions and vendor Bluetooth adapter behavior still need device-level validation. Current compatibility APK only reads adapter state and does not exercise this connection path.

### Wi-Fi hotspot API branches

- The original controller only selects Wi-Fi Direct when real SDK >= 28 and LocalOnlyHotspot when real SDK >= 26. Neither API branch is available on Android 4.2.2/API 17.
- API 17 adaptation therefore must use a separately validated legacy path: existing car hotspot, configured manual hotspot, or the ROOT/hostapd backend if the Honda firmware supports it. Do not reference newer hotspot classes in API17-loaded code paths without class-load-safe isolation.
- The original ROOT/hostapd backend is high risk and must not be ported verbatim. Existing analysis identified global process termination, hostapd config overwrite/cleanup, unconditional Wi-Fi re-enable and shell path construction risks.

### Native NCM / VPN data plane

- The wired stack attaches NCM to `CarPlayVpnService`; that service constructs `LwipNcmBridge`, which starts NCM RX/TX loops and connects frames to the lwIP native component. This is not a simple Java-only USB feature.
- Eight original native libraries carry an Android API 19 platform note. API17 compatibility cannot be inferred from successful APK compilation; inspect ELF dependencies, imported symbols and runtime linker behavior before loading any of them.
- Native API notes must not be edited to disguise a mismatch. If a library genuinely requires API19 symbols, it needs a compatible rebuild or a safe feature fallback.

## Updated porting order

1. Keep the current app's USB and Bluetooth probes passive.
2. Add offline fixture tests for USB descriptor/configuration selection and NCM NTB16 framing before any hardware I/O.
3. Add API17-compatible public USB Host session code only after test coverage and permission UX are complete.
4. Keep raw usbfs/ROOT direct mode isolated behind an explicit, separately authorized diagnostic path.
5. Validate Bluetooth RFCOMM fallback behavior on API17 without enabling or changing system Bluetooth state automatically.
6. Audit/replace native lwIP and NCM dependencies before attempting VPN integration.
7. Only then consider wireless iAP2 bootstrap and hotspot handoff. ROOT hotspot configuration writes remain disabled until transactional backup/rollback and process ownership checks are independently verified.


## Offline NCM codec milestone (2026-11-06)

A pure Java 7 NTB16 byte-array codec was added at `DiPlay/compat/ncm/Ntb16Codec.java`. It is a fixture-only port of the original NTH16/NDP16 framing shape; it does not open USB, send frames, use ROOT, or start a network interface. The self-test passed in GitHub Actions run #48, including round-trip, little-endian sequence, 512-byte boundary/ZLP marker, invalid signature/range/header, maximum u16 NTB length and invalid arguments. See [NCM module README](../compat/ncm/README.md).

The Android compatibility build now includes this pure-Java primitive as an offline component. This does not constitute a functional NCM USB driver or a completed API17 NCM hardware test.


## Legacy USB claim and VPN/lwIP findings (2026-11-06)

See [API 17 legacy USB claim, raw usbfs and VPN/lwIP review](API17_LEGACY_USB_NCM_AND_VPN_REVIEW.md). Important correction to the generic compatibility summary:

- The original special NCM driver-disconnect gate is hard-coded to API 23 + ARMv7 + Apple VID 1452 + CDC-NCM interfaces + an MTK/8227L board/hardware match. It is not eligible for the report's API 17 baseline.
- General legacy force-claim/configuration/alternate-setting fallbacks call JNI in `libusb_host_compat.so`; raw usbfs transfer uses JNI in `liblegacy_ncm_claim.so`. Both extracted ELF files carry Android platform 19 notes, so API 17 runtime compatibility remains unresolved.
- The userspace lwIP bridge is a native JNI stack with separate NCM RX/TX threads. The VPN TUN establish path is separate from the userspace attach path; neither has been tested on the target.
