# HondaHack 7.6.0 Rebuild Project

## Status (2026-11-04)

### Verified Phase 1 — original APK archived and integrity checked

- Correct Library entry: `/本田黑科技7.6.0_可联网_去注销_任意.apk`.
- Materialized a read-only working copy from the Library; original Library bytes were not modified.
- File size: 3,893,398 bytes.
- Recomputed SHA-256: `e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe`; exact match with the reverse-engineering report.
- ZIP/APK integrity test passed; the archive contains 986 entries, one `classes.dex` (1,909,752 bytes), `resources.arsc`, and three ARMv7 native libraries.
- Binary manifest structural decode and signer certificate inventory are documented in `MANIFEST_COMPONENT_AUDIT.md` and `APK_BASELINE_INVENTORY.md`.
- Reverse-engineering report located in Library: `本田黑科技_7.6.0_逆向分析.md`.
- Existing starter source archive: `HondaHackRebuild_source.zip` (29,808 bytes; 47 entries); see `SOURCE_ARCHIVE_AUDIT.md`.
- Official comparison APK in the report is version 4.8.16, not an exact substitute for the 7.6.0 sample.

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

- [x] Acquire and verify the exact original 7.6.0 APK; record SHA-256 and signer.
- [x] Inspect the existing `HondaHackRebuild_source.zip` archive structure and reconcile its files with the report.
- [x] Produce the structural APK inventory: manifest, resources, DEX classes, native libraries, permissions, components, assets.
- [x] Map major feature families to observed components/actions and a safe rebuild traceability matrix.
- [x] Implement the safe baseline app shell and multiple independent feature previews.
- [x] Implement read-only capability detection and mock/demo mode.
- [ ] Freshly review the high-value original classes using a locally available JADX source export; raw decompilation output is not currently in Library/repo.
- [ ] Implement remaining compatible features incrementally with unit/instrumentation tests.
- [ ] Validate on emulator, then bench hardware, then vehicle while parked.
- [ ] Produce signed debug/release APKs and installation/rollback documentation.

## Critical safety notes

The report describes replacement of `/system/bin/app_process`, overwriting `platform.xml`, installing Xposed, granting privileged permissions, and remote commands that can remove system/root components. These operations can brick a head unit or compromise vehicle-system integrity. They are not part of the default rebuild. No installation should be attempted on a vehicle until compatibility, backup, and recovery procedures are independently verified.

## Current blocker

The exact 7.6.0 APK is available in the Library and its hash matches the reverse-engineering report. The raw `jadx-out/sources` tree is still not present in the Library or GitHub repository. Phase 35 adds `tools/decompile-original.sh`, pinned to the official JADX 1.5.3 archive and its published SHA-256, plus WSL2/ext4 instructions to avoid NTFS case-collision loss (`d.java` vs `D.java`). The helper has not yet been run end-to-end in this environment because the 116 MB release archive could not be downloaded here. Therefore method-level behavior is still supported by the supplied report and DEX structural audits, not by a fresh decompilation review. Do not claim independent method-level verification until the WSL2 script has completed and its output has been reviewed.
