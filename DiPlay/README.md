# DiPlay 0.3.11 — Honda head-unit adaptation

## Original APK

- Repository path: `diplay0.3.11.apk` (repository root)
- SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`
- The decompile and native-audit workflows verify this hash before processing.

## Verified Honda head-unit baseline

Source: Library document `思域报告(1).pdf`.

- ROOT: obtained; report records `su -c id` returning UID 0.
- Android 4.2.2 / API 17.
- Build: `1.F1A4.65`; fingerprint `Honda/Andromeda/vcm30t30:4.2.2/1.F1A4.65/138:user/release-keys`.
- Device: `MY16ADA`; manufacturer Honda / MITSUBISHI ELECTRIC.
- Board/platform: Andromeda / `vcm30t30`.
- CPU: ARMv7, 4 cores, 900 MHz; ABI `armeabi-v7a, armeabi`.
- Display: 800 × 480, 160 dpi, landscape.
- RAM: 0.94 GB total; 0.27 GB available at report time.
- Wi-Fi: reported available. AP mode and hostapd availability remain unverified.

## GitHub Actions

1. **DiPlay APK Decompile** — verifies APK, generates JADX Java-like source and APKTool resources/Manifest/Smali, and uploads `diplay-0.3.11-decompiled`.
2. **DiPlay Native Library Compatibility Audit** — inspects every packaged `.so` ELF header, Android NDK platform note and DT_NEEDED dependencies; uploads `diplay-native-api17-audit`.

Run either from **Actions → workflow → Run workflow**. The original APK is already at the repository root; do not upload it to `DiPlay/original/`.

## Project decisions

- Keep the ROOT hotspot backend. Do not remove it.
- Make ROOT changes transactional: read current state, timestamped backup, validate, apply only after explicit user action, and rollback on failure.
- First perform read-only capability detection. The current probe is `tools/honda-readonly-probe.sh`.
- Do not assume `wlan0`, `nl80211`, `hostapd`, or `dnsmasq` exist until the actual Honda firmware is probed.
- Isolate BYD-specific bridges behind optional interfaces.
- Optimize for API 17, ARMv7, 800×480 landscape and limited RAM.
- Never treat an APK's `minSdkVersion=17` as proof that all code paths or native libraries work on Android 4.2.2.

## Reports and tools

- [Chinese static analysis report](analysis/DiPlay-0.3.11-静态分析报告.md)
- [Read-only Honda probe](tools/honda-readonly-probe.sh)
- [Native ELF audit script](tools/analyze-native-libs.sh)
- [Native audit workflow run #42141939470](https://github.com/qiangge5586-a11y/1/actions/runs/42141939470)
- [Decompilation workflow run #42140451737](https://github.com/qiangge5586-a11y/1/actions/runs/42140451737)

No modified APK has been built or tested on the Honda head unit yet. Static findings are not a substitute for an on-device test.
