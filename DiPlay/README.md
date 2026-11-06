# DiPlay 0.3.11 — Honda head-unit adaptation

## Original APK

- Repository path: `diplay0.3.11.apk` (repository root)
- SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`
- JADX produced Java-like output with 104 reported decompilation errors; APKTool decoded resources, Manifest and Smali. Do not treat JADX output as lossless source.

## Verified Honda head-unit baseline

Source: Library document `思域报告(1).pdf`.

- ROOT: obtained; report records `su -c id` returning UID 0.
- Android 4.2.2 / API 17; build `1.F1A4.65`.
- Fingerprint: `Honda/Andromeda/vcm30t30:4.2.2/1.F1A4.65/138:user/release-keys`.
- Device: `MY16ADA`; manufacturer Honda / MITSUBISHI ELECTRIC.
- Board/platform: Andromeda / `vcm30t30`.
- CPU: ARMv7, 4 cores, 900 MHz; ABI `armeabi-v7a, armeabi`.
- Display: 800 × 480, 160 dpi, landscape.
- RAM: 0.94 GB total; 0.27 GB available at report time.
- Wi-Fi: reported available. AP mode, interface, driver and hostapd availability remain unverified.

## Current isolated compatibility app

A dedicated project now exists at `DiPlay/android/`, separate from the root `android/` HondaHackRebuild project.

- Application ID: `com.shilapi.xcertplay.compat` (does not replace the original DiPlay package).
- minSdk 17, targetSdk 28, Java 7 source compatibility.
- Chinese landscape diagnostic dashboard with report baseline clearly distinguished from live measurements.
- USB Host read-only interface/endpoint descriptor enumeration with offline CDC-NCM shape matching; API 17 alternate-setting metadata is marked unknown.
- Bluetooth adapter read-only status check.
- Offline Java 7 NTB16 codec and incremental stream decoder, with 40 synthetic tests total (no USB I/O).
- Hotspot safety transaction models and simulation tests are included; no real ROOT executor or system-write path is enabled.

Latest successful APK build containing USB and Bluetooth read-only modules, the offline NTB16 codec, the incremental NTB16 stream decoder, the pure-Java CDC-NCM interface selector, API-17-safe passive USB interface/endpoint snapshots, and the corrected Chinese Bluetooth status display: [GitHub Actions run #69](https://github.com/qiangge5586-a11y/1/actions/runs/42156454962).

Artifact: `diplay-honda-compat-debug`, ID `13475400691`, archive size 36,240 bytes; archive digest `sha256:fdcd827e41e4bf35a7c6097f50d588476ea97b5df2f0728f4b800485f611791a` (expires 2026-11-20). Downloaded archive inspection confirmed it contains `app-debug.apk` (39,915 bytes); APK SHA-256: `83c1d66e7a02b15c3afa8cf90660d990feb4024ba45b42ee83f20684810d344d`.

The matching descriptor/NCM test workflow [run #87](https://github.com/qiangge5586-a11y/1/actions/runs/42156454922) passed: 14 NTB16 codec cases, 11 stream-decoder cases, 9 CDC-NCM interface-selector cases, and 6 USB-to-NCM snapshot matcher cases (40 tests total across NCM/USB). The matcher now carries the selected endpoint address and max-packet-size metadata forward for later API-17 transfer planning.
The build verifies compilation, APK packaging, application ID, minSdk 17 and targetSdk 28. It is not a full DiPlay 0.3.11 recreation and has not yet been installed or tested on the Honda head unit.



## Previous CI build record (2026-11-06, run #25)

The latest Android workflow completed successfully, including Gradle assembly, AAPT metadata verification and artifact upload:
https://github.com/qiangge5586-a11y/1/actions/runs/42149522090

Artifact: `diplay-honda-compat-debug` (artifact ID `13471009807`, 26,585 bytes; expires 2026-11-20). Archive digest: `sha256:035090779625eff20ee4cbc8e01eeda8d2cf0e568f71e4cbddf6e8b1464908bb`.

## GitHub Actions and project records

- [DiPlay Android build workflow](../.github/workflows/diplay-android-build.yml)
- [Chinese static analysis report](analysis/DiPlay-0.3.11-静态分析报告.md)
- [API 17 compatibility audit](analysis/API17_COMPATIBILITY_AUDIT.md)
- [Read-only Honda probe](tools/honda-readonly-probe.sh)
- [Native ELF audit script](tools/analyze-native-libs.sh)
- [ROOT hotspot safety redesign](analysis/ROOT_HOTSPOT_SAFETY_REDESIGN.md)
- [Automated hotspot safety scanner](tools/audit-hotspot-safety.py)
- [Bluetooth passive status probe](compat/bluetooth/README.md)
- [USB read-only scanner](compat/usb/README.md)
- [Offline NCM/NTB16 codec](compat/ncm/README.md)
- [Protocol path map](analysis/DiPlay_PROTOCOL_PATH_MAP.md)
- [Original NCM/NTB16 codec comparison](analysis/NCM_NTB16_CODEC_COMPARISON.md)
- [CDC-NCM interface selection policy review](analysis/NCM_INTERFACE_SELECTION_POLICY.md)
- [API 17 legacy USB claim, raw usbfs and VPN/lwIP review](analysis/API17_LEGACY_USB_NCM_AND_VPN_REVIEW.md)

## Safety and remaining gates

- Keep the ROOT hotspot backend as a project goal; do not remove it.
- The current app does not call `su`, start/stop hostapd or dnsmasq, or modify Wi-Fi/system configuration.
- ROOT hotspot changes must remain transactional: read state, create and verify backup, apply only after explicit user authorization, and rollback on failure.
- Do not assume `wlan0`, `nl80211`, `hostapd`, or `dnsmasq` exist until the actual Honda firmware is probed.
- USB and Bluetooth checks are passive only; neither has been verified on the MY16ADA hardware.
- Eight of nine original native libraries carry an Android NDK API 19 platform note. API 17 loading must be tested; do not alter ELF notes to disguise incompatibility.
- Remaining work: API 17 runtime test, native library loading checks, Bluetooth/USB hardware verification, original DiPlay protocol path porting, CarPlay/NCM integration and vehicle testing.

A successful CI build is not proof of runtime or vehicle compatibility. Do not treat this prototype as a vehicle-ready replacement for the original app.
