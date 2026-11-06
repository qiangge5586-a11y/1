# DiPlay Honda Compatibility Android Project

This is an isolated Android application project for the DiPlay 0.3.11 Honda adaptation work. It is deliberately separate from the repository's existing `android/` HondaHackRebuild project.

## Build target
- Android Gradle Plugin 8.7.3 / Gradle 8.9
- Compile SDK 35
- Minimum SDK 17 (Android 4.2.2)
- Target SDK 28
- Java source/target compatibility 7
- ARMv7 device baseline documented in the DiPlay analysis report

The application ID is `com.shilapi.xcertplay.compat`, intentionally distinct from the original DiPlay package so this prototype does not replace the installed original application.

## Current contents
- Minimal Chinese-language landscape launcher screen.
- The pure Java hotspot safety/diagnostic classes under `../compat/hotspot` are included in the app compilation source set.
- No runtime permissions, ROOT process executor, hotspot write operation, service launch, or system configuration mutation is enabled.

## Build
From this directory, with JDK 17, Android SDK platform 35 and Gradle 8.9:

```sh
gradle --no-daemon :app:assembleDebug
```

GitHub Actions workflow: `.github/workflows/diplay-android-build.yml`. A successful build proves that this isolated source project compiles and packages; it does not prove API-17 runtime behavior or vehicle compatibility. No APK should be treated as vehicle-ready until installed and tested on the documented MY16ADA head unit under a separately approved test procedure.


## First CI package (2026-11-06)

The isolated project completed its first successful GitHub Actions Android build in run #4:
https://github.com/qiangge5586-a11y/1/actions/runs/42147380599

Artifact: `diplay-honda-compat-debug` (artifact ID `13470194027`, 21,128 bytes; expires 2026-11-20). The artifact archive digest reported by Actions is `sha256:993b8b4e6643f81186bd8fdff3638290b0f9c7fd3c454c97d7e3d8348034527b`.

This confirms a debug APK was assembled and uploaded by CI. It is an initial Chinese-language shell containing the safety/diagnostic classes, not a feature-complete recreation of DiPlay 0.3.11. It has not been installed on the Honda head unit, and ROOT/hotspot operations remain disabled.


## Chinese diagnostic dashboard build (2026-11-06)

The Chinese diagnostic dashboard update was assembled and uploaded in GitHub Actions run #6:
https://github.com/qiangge5586-a11y/1/actions/runs/42147789283

Artifact: `diplay-honda-compat-debug` (artifact ID `13471180642`, 22,314 bytes; expires 2026-11-20). Reported artifact archive digest: `sha256:92f5e32a09694ab618db75b25b3a24562f585c1ba9043c8fded3b4fa48cf9b01`.

The dashboard displays the MY16ADA report baseline and clearly labels live ROOT, Wi-Fi, interface, config-file and daemon checks as not yet measured. It performs no device probing and does not invoke su. The APK build and artifact upload steps succeeded; this is still not an on-device API-17 or vehicle test.


## APK metadata verification build (2026-11-06)

Android workflow run #8 completed successfully:
https://github.com/qiangge5586-a11y/1/actions/runs/42147908587

The workflow assembled the debug APK, then used AAPT to verify:
- application ID: `com.shilapi.xcertplay.compat`
- minimum SDK: 17
- target SDK: 28

Artifact: `diplay-honda-compat-debug` (artifact ID `13470558794`, 22,305 bytes; expires 2026-11-20). Reported archive digest: `sha256:fa363cd1986aff1adb4d96c50726cd7ab48c364e8a1ccdc74119cfc6696463b6`.

The successful metadata check confirms the package identity and declared SDK levels. It does not establish installation, launch, rendering, API-17 runtime behavior or compatibility with the MY16ADA head unit.

## API 17 static review

A source-level review of the current manifest and dashboard Activity is recorded in [API 17 compatibility audit](../analysis/API17_COMPATIBILITY_AUDIT.md). The current screen uses basic framework UI APIs and has no ROOT, Wi-Fi, USB or Bluetooth side effects. Runtime compatibility is still unverified; an API 17 emulator/device and the actual head unit are required for that gate.


## USB and Bluetooth read-only probes (2026-11-06)

The dashboard now includes:
- USB Host descriptor enumeration via `UsbManager.getDeviceList()`. It does not request device permission, open devices, claim interfaces, access endpoints, or transfer data.
- Bluetooth adapter status via `BluetoothAdapter.getDefaultAdapter()`, `getState()`, and `isEnabled()`; it reads the adapter name only when enabled. It does not enable/disable Bluetooth, start discovery, pair, or connect.
- Manifest declares only the legacy normal `BLUETOOTH` permission for adapter-name access; no `BLUETOOTH_ADMIN` permission is declared.

The Bluetooth implementation is in `../compat/bluetooth/BluetoothReadOnlyProbe.java`. The Android build workflow now watches `DiPlay/compat/bluetooth/**` so future changes to that module trigger compilation.

Latest successful build containing the Bluetooth probe and corrected dashboard: [run #42149355016](https://github.com/qiangge5586-a11y/1/actions/runs/42149355016).

Artifact: `diplay-honda-compat-debug` (artifact ID `13472055975`, 26,587 bytes; archive digest `sha256:7756ecc3b823f4b3edaf64b058360daf3ec2bf726ba1d6f16fc4688be2e25e65`).

This proves compilation, APK packaging, AAPT package identity and SDK metadata checks. It does not prove the Bluetooth adapter behaves correctly on API 17 or on the MY16ADA head unit. Real-device installation and testing remain pending.
