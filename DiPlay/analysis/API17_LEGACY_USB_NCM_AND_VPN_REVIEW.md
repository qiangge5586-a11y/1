# API 17 legacy USB claim, raw usbfs and VPN/lwIP review

Date: 2026-11-06

## Evidence and confidence

This review uses the original DiPlay 0.3.11 JADX Java-like sources and ELF metadata from the extracted ARMv7 APK. JADX reports 104 errors across the APK, so conclusions are limited to readable methods and cross-checked call paths. No code was executed against a Honda head unit.

## 1. USB interface claim behavior

### Public/framework claim

`LegacyUsbHostCompat.claim(SharedUsbConnection, UsbInterface)` first calls `connection.claimInterface(usbInterface, true)`. If that succeeds, it returns success. If it fails and real SDK is <= 28, it falls back to `LegacyUsbHostNative.forceClaim(fd, interfaceId)`; SDK > 28 returns failure without that fallback.

The overload taking `UsbDeviceConnection` follows the same pattern. The force-claim fallback is JNI loaded from `libusb_host_compat.so`.

### Separate NCM driver-disconnect gate

`LegacyNcmClaim.eligible(...)` is much narrower than the general force-claim path. It returns true only when all of the following match:

- API exactly 23 (not API 17 and not a general API <= 23 range).
- ABI exactly `armeabi-v7a`.
- USB vendor ID 1452 (Apple).
- CDC control interface class 2 / subclass 13.
- CDC data interface class 10.
- board or hardware string matches the embedded MTK / 8227L regular expression.

If ordinary framework claim fails, this API-23-only gate controls the separate native NCM claim/disconnect-and-restore path. The API-17 Honda target therefore does not qualify for that special driver-disconnect flow based on the reported OS baseline. Do not broaden the predicate without evidence and a dedicated safety review.

The special native path checks the active USB configuration with standard GET_CONFIGURATION before creating operations. On EBUSY it reads the bound driver and only permits `cdc_ncm` or `cdc_ether` before attempting atomic disconnect-and-claim. A restore callback reconnects the saved interface pair when the operation fails or exits.

## 2. Raw usbfs connection behavior

`RawSharedUsbConnection` is materially different from the public USB Host connection:

- It receives a `ParcelFileDescriptor` from `DirectUsbAccess.open(device, true)`; diagnostics say whether a root grant was used.
- It stores the raw file descriptor and performs control/bulk transfers through JNI using direct ByteBuffers.
- Transfers are serialized by a per-connection lock.
- Interface claim/release and alternate-setting selection are also native usbfs calls.
- `queue()` and `await()` deliberately throw UnsupportedOperationException; this path is synchronous-only.
- It uses reference counting and closes the ParcelFileDescriptor when the final reference is released.

The constructor's async-support boolean is not set true, so the raw usbfs path is not an asynchronous UsbRequest path. This is consistent with the API 17 synchronous NCM path, but it does not prove the Honda kernel exposes usable usbfs nodes or grants access.

## 3. Native library compatibility risk

The extracted ARMv7 APK contains these relevant native libraries:

- `libusb_host_compat.so`: Android ELF note identifies platform 19 / NDK r25c.
- `liblegacy_ncm_claim.so`: Android ELF note identifies platform 19 / NDK r25c.
- `liblwip_ncm_relay.so`: Android ELF note identifies platform 19 / NDK r25c.

Their direct NEEDED entries are limited to common platform libraries such as libc, libm, libdl and (for lwIP) liblog. This list alone is not enough to prove API 17 compatibility: the ELF platform note is newer than the target baseline, and symbol availability / runtime linker behavior must be checked against the actual API 17 bionic environment. Do not edit ELF notes to disguise this gap.

A useful distinction:
- The API-23-only `LegacyNcmClaim` driver-disconnect path is statically ineligible on API 17.
- The general `LegacyUsbHostCompat` force-claim / configuration / alternate-setting fallbacks can still attempt JNI calls on API 17, but depend on the API-19-marked `libusb_host_compat.so`.
- The raw usbfs data-transfer path calls JNI from `liblegacy_ncm_claim.so`, also marked platform 19.

These are separate fallback mechanisms and must not be conflated.

## 4. VPN and lwIP data-plane behavior

`CarPlayVpnService.attachUserspace()` constructs `LwipNcmBridge`, calls `start()`, and then wraps the AirPlay listener around that bridge. The wired VPN route is a different path: it creates a `VpnService.Builder` with the link-local IPv6 address, a `fe80::/10` route and MTU 1500, then calls `establish()` to obtain a TUN descriptor and attaches the IPv6 bridge.

`LwipNcmBridge.start()`:
1. Initializes the native lwIP stack with host MAC and link-local IPv6.
2. Sends an unsolicited neighbor advertisement.
3. Starts daemon RX and TX threads.
4. RX reads Ethernet frames from `NcmUsbBridge.recv(200)`, parses IPv6/ND and injects relevant packets into lwIP.
5. TX drains a bounded queue and sends frames using `NcmUsbBridge.send(..., WRITE_TIMEOUT_MILLIS)`.

`LwipNcmNative` loads `liblwip_ncm_relay.so` and exposes create, input-frame, port registration, snapshot and destroy JNI calls. Native load/create errors are caught as `UnsatisfiedLinkError` and reported as initialization failure rather than silently creating a functioning stack.

On close, the bridge stops the running flag, clears the TX queue, joins TX and RX threads with a 500 ms timeout each, interrupts a thread that remains alive, destroys the native lwIP handle, and closes NCM. This cleanup ordering and blocking behavior need runtime stress tests; a 500 ms join is not proof that native callbacks have fully quiesced.

## 5. API 17 porting implications

1. Keep the existing app passive. It must not claim USB interfaces or open usbfs nodes just to display device inventory.
2. Keep API-23-only driver disconnect logic out of the API-17 path.
3. Treat JNI fallbacks as unavailable until API 17 symbol and linker checks pass.
4. Before using raw usbfs, verify descriptor ownership, file-descriptor lifetime, permissions, interface ownership and close ordering using a controlled test device.
5. Implement fixture tests for interface selection and NCM claim policy separately from any JNI adapter.
6. Keep lwIP initialization behind an explicit capability check and never start it merely because the APK compiled.
7. Keep VPN TUN establishment and NCM bridge startup as distinct attach stages with independent failure reporting.
8. No ROOT write, interface claim, USB control/bulk transfer, VPN establishment or hotspot configuration operation is authorized by this static review.

## Conclusion

The original code has a general public USB claim path, older-API JNI force-claim fallbacks, a separate API-23-only MTK/Apple driver-disconnect gate, and a synchronous raw-usbfs data path. The API-17 target cannot use the API-23 special gate, and the API-19 native build notes create unresolved compatibility risk for the older fallback libraries. The lwIP userspace bridge is a multi-threaded JNI data plane; the VPN TUN path is a separate route and must be validated independently.

No API-17 emulator or Honda MY16ADA runtime test has been completed.
