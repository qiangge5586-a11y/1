# Honda Android compatibility and rebuild workspace

This repository contains two separate workstreams. They share the Honda/Civic target context but are not interchangeable APKs.

## 1. DiPlay 0.3.11 Honda compatibility work

Target baseline recorded in `思域报告.pdf`: 2019 Honda Civic factory center display, MY16ADA / Andromeda / vcm30t30, Android 4.2.2 (API 17), ARMv7, 800×480. These are report values, not a live device probe.

- Original APK: `diplay0.3.11.apk`; SHA-256 is pinned in the CI workflows.
- Analysis: `DiPlay/analysis/` contains protocol call maps, API-17 compatibility reviews, iAP2/CSM source audits, USB/NCM findings and ROOT hotspot safety redesign.
- Compatibility primitives: `DiPlay/compat/` contains Java 7 offline iAP2 models, Bluetooth RFCOMM adapters, read-only USB/NCM descriptor models, NTB16 codecs and ROOT hotspot safety/transaction primitives.
- Android shell: `DiPlay/android/` builds a separate diagnostic APK with application ID `com.shilapi.xcertplay.compat`, minSdk 17 and targetSdk 28. It is not the original DiPlay app and must not replace it.

### Current DiPlay status

- iAP2 Link/CSM framing, wireless bootstrap models, sequence/window logic, bounded pump scaffolding, CSM send/receive adapters and source-parity audits are covered by Java 7 offline tests. The retransmission timer now mirrors the original engine's explicit receive-ACK deadline rather than taking a global minimum.
- Bluetooth RFCOMM connector follows secure → insecure → reflective fallback and shares one monotonic overall timeout across attempts. The default is 10 seconds; accepted range is 1–300 seconds. Pure Java deadline tests pass.
- USB enumeration remains read-only. NCM/NTB16 codec and interface selection are fixture-only; no USB endpoint transfer is wired.
- ROOT hotspot work retains the bottom-layer safety module: read-only preflight, backup metadata checks, process identity checks, append-only journal, recovery classification and transaction coordinator. No mutating production `RootOps` adapter is enabled.
- The isolated diagnostic dashboard can manually enumerate USB descriptors, inspect Bluetooth adapter state, and (after an explicit tap) run the fixed ROOT/hotspot read-only command catalog. It does not start iAP2, mutate hotspot settings, or exercise the live Bluetooth transport.

### Important boundaries

CI success proves source compilation, fixture tests, APK packaging and declared package/SDK metadata only. It does not prove API-17 runtime behavior, MY16ADA compatibility, a live RFCOMM/iPhone handshake, AirPlay type-130 tunnel setup, CarPlay startup, ROOT grant, hotspot modification or USB/NCM hardware operation. No vehicle-ready DiPlay replacement APK is currently produced.

## 2. HondaHack rebuild workstream

- Android project: `android/` (application ID `cn.autohack.rebuild`).
- Source snapshot: `HondaHackRebuild_source.zip`.
- This is a separate HondaHack rebuild project. Its APK, package identity, features and test results must not be conflated with the DiPlay compatibility APK.

## Build and verification

GitHub Actions workflows:

| Workflow | Scope |
|---|---|
| `.github/workflows/android-build.yml` | HondaHack rebuild unit tests, lint and debug APK |
| `.github/workflows/diplay-android-build.yml` | DiPlay diagnostic APK build and package/SDK verification |
| `.github/workflows/diplay-iap2-handshake.yml` | Java 7 iAP2, RFCOMM deadline and transport-model tests |
| `.github/workflows/diplay-hotspot-coordinator.yml` | ROOT hotspot safety, USB descriptor and NCM offline tests |
| `.github/workflows/diplay-native-audit.yml` | Original DiPlay native-library/API compatibility audit |
| `.github/workflows/diplay-decompile.yml` | Original APK verification and decompilation evidence |

Use the latest Actions result for the exact commit being evaluated; older green runs do not verify newer source changes.

## Immediate priorities

1. Finish source-parity review of the original iAP2 engine and integrate a production driver only after all event, timer, queue and close semantics are accounted for.
2. Validate RFCOMM connect/cancel/close behavior on API 17 and then on the actual head unit; do not infer success from desktop tests.
3. Keep wireless RFCOMM bootstrap, AirPlay IAP TCP tunnel and tunnel iAP2 as distinct phases with separate diagnostics.
4. Complete USB/NCM API-17 compatibility and native-library dependency gates before enabling the wired CarPlay data path.
5. Keep ROOT hotspot writes disabled until a device-specific read-only preflight, durable verified backup/restore implementation and separately confirmed test plan exist.

See the module READMEs and analysis reports for implementation details and evidence.
