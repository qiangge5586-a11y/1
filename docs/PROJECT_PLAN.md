# HondaHack 7.6.0 Rebuild Project

## Status (2026-11-04)

### Verified Phase 1 — original APK archived and integrity checked

- Correct Library entry: `/本田黑科技7.6.0_可联网_去注销_任意.apk`.
- Materialized original APK to the working container without modifying the Library source.
- File size: 3,893,398 bytes.
- Recomputed SHA-256: `e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe`; exact match with the reverse-engineering report.
- ZIP/APK structure validated: 986 entries; `AndroidManifest.xml`, `resources.arsc`, one `classes.dex` (1,909,752 bytes), and three ARMv7 native libraries (`libnative.so`, `libmp3lame.so`, `libxposed.so`).
- Assets include `assets/VERSION` and `assets/xposed_init`.
- APK signature-related META-INF files are present; signer certificate details and binary manifest decoding remain pending.
- Status: phase 1 complete; phase 2 inventory/decode in progress.


- Reverse-engineering report located in the ChatGPT Library: `本田黑科技_7.6.0_逆向分析.md` (353 lines).
- A source archive already exists in this repository: `HondaHackRebuild_source.zip` (29,808 bytes). Its contents have not yet been verified in this execution environment.
- The original 7.6.0 APK itself was not found in the Library search results. The report identifies the analyzed modified sample as 3,893,398 bytes with SHA-256 `E52AED97A77BC4BE1E4D248BB9526EDAFAAE428EA20B8926F7763E532CC55FBE`.
- The official comparison APK described in the report is HondaHack 4.8.16 (2,410,380 bytes), not the 7.6.0 baseline. Do not treat it as an exact substitute.

## Goal

Rebuild a functionally equivalent, maintainable Android application based on independently implemented behavior and verified compatibility. Preserve the original feature concepts while avoiding blind reuse of unknown binaries or unsafe system modifications.

## Functional scope from the reverse-engineering report

1. Steering-wheel key remapping and app launching.
2. Startup video / startup voice configuration.
3. Instrument-cluster data and navigation presentation.
4. Navigation audio ducking and audio-focus coordination.
5. Local APK installation and app management.
6. External-storage permission diagnostics / supported repair guidance.
7. Startup/autorun management and safe cleanup tools.
8. Display, screen saver, status information, screenshot utilities.
9. Voice assistant integration.
10. Vehicle-specific adapters for Honda Civic/CR-V and documented Mitsubishi/Clarion service interfaces where authorized.

## Architecture direction

- Android application modules separated by feature and vehicle adapter.
- A capability-probe layer that detects Android version, board, vehicle/head-unit family, available services, root availability, and supported APIs.
- Read-only diagnostics by default.
- Privileged operations must be explicit, individually confirmed, logged, reversible where possible, and gated by a verified recovery path.
- No remote kill switch, remote deletion of `su`, hidden signature spoofing, credential bypass, or silent system-partition modification.
- Use documented interfaces and user-authorized integration. Do not assume that the 2019 Civic factory head unit exposes every feature found in the report.

## Build and validation stages

- [ ] Acquire and verify the exact original 7.6.0 APK; record SHA-256 and signer.
- [ ] Inspect the existing `HondaHackRebuild_source.zip` archive and reconcile its files with the report.
- [ ] Produce a reproducible APK inventory: manifest, resources, DEX classes, native libraries, permissions, components, assets.
- [ ] Map each feature to UI, service, broadcast, preference keys, and vehicle-side dependencies.
- [ ] Implement the safe baseline app shell and feature modules.
- [ ] Implement read-only vehicle capability detection and mock/demo mode.
- [ ] Implement compatible features incrementally with unit/instrumentation tests.
- [ ] Validate on emulator, then bench hardware, then vehicle while parked.
- [ ] Produce signed debug/release APKs and installation/rollback documentation.

## Critical safety notes

The report describes replacement of `/system/bin/app_process`, overwriting `platform.xml`, installing Xposed, granting privileged permissions, and remote commands that can remove system/root components. These operations can brick a head unit or compromise vehicle-system integrity. They are not part of the default rebuild. No installation should be attempted on a vehicle until compatibility, backup, and recovery procedures are independently verified.

## Current blocker

The exact original 7.6.0 APK is not present in the Library search results currently available. The reverse-engineering report references a local Windows path, but that path is not accessible here. The existing source ZIP is in this GitHub repository, but its archive contents still need to be extracted and audited before claiming a completed rebuild.
