# DiPlay API 17 ROOT executor implementation status

Date: 2026-11-06
Target: Honda MY16ADA / Andromeda / vcm30t30, Android 4.2.2 (API 17), ARMv7.

## Build artifact verification

The Android Build workflow run [#42159980783](https://github.com/qiangge5586-a11y/1/actions/runs/42159980783) completed successfully on commit `0d6055902664b83bd38d8484c5a6aa846b8bb1b3`.

- Artifact: `diplay-honda-compat-debug`, ID `13477010196`.
- Artifact ZIP SHA-256: `fbdc97b6547a76436983099d75b6b08a65603adf72afd055bc8b46010a03a8c7`.
- APK file SHA-256: `e3117e7cab7b14325250b49a657151f67dddd21a1f2389fb38a34d7194321c24`.
- Package: `com.shilapi.xcertplay.compat`.
- Version: `0.1.0-honda-alpha` (versionCode 1).
- minSdk: 17; targetSdk: 28; compileSdk: 35.
- Label: `DiPlay 思域适配验证版`.
- This is a small debug compatibility shell, not a rebuilt production DiPlay release and not proof of vehicle installation or runtime compatibility.

## Implemented in this stage

- Added `Api17SuReadOnlyRunner`, a Java 7/API 17-compatible `su -c` runner for the fixed `HotspotReadOnlyOperation` catalog only.
- Rejects any executable/argument pair not exactly present in the read-only catalog; arbitrary shell commands and injected extra arguments are rejected.
- Uses a bounded command timeout, drains stdout/stderr concurrently, caps captured output, and destroys a timed-out process.
- Added a self-test for catalog acceptance, shell argument quoting and rejection of arbitrary commands/extra arguments.
- Wired the new test into the Java 7 Hotspot Actions workflow.
- The Android app source set already includes `DiPlay/compat/hotspot`, so the runner is included in subsequent debug builds.

## Safety boundary (intentional)

This stage adds only read-only diagnostics. The runner cannot write configuration, change Wi-Fi state, signal daemons, or restore files. No concrete mutating ROOT adapter is enabled.

Before a mutating adapter can be implemented safely, the MY16ADA device must be connected and a read-only probe must establish:
- actual config and backup locations, filesystem and atomic-rename behavior;
- available `su`, `cp`, `mv`, `chmod`, `chown`, hash and SELinux tools and their exact behavior;
- actual interface names, Wi-Fi framework state and current addresses/routes;
- hostapd/dnsmasq executable paths and pre-existing PID/start-time/cmdline identities;
- original config bytes, existence, owner/group/mode and SELinux context.

No path is assumed from generic Android/Linux conventions. Any missing or unverifiable prerequisite must stop the transaction before writes. The production adapter must then implement durable verified backup, same-filesystem staging/atomic replacement, metadata/context restoration, exact process ownership checks, bounded TERM/KILL, Wi-Fi/interface restoration, and crash/reboot recovery. The first device session must remain read-only; a separate explicit confirmation is required after showing the captured preflight and verified backup summary before the first write.

## Verification

- Existing coordinator workflow #42159980766 passed before this new runner was added.
- A new run must pass the expanded Java 7 suite before this runner is considered CI-verified.
- Android build #42159980783 passed on the earlier source commit; the newly added runner requires a fresh build run.
- No live ROOT command has been sent to the user's vehicle, no hotspot configuration has been changed, and no in-vehicle test has been performed.


## 2026-11-06 CI follow-up

- Expanded Hotspot Actions run [#42160365707](https://github.com/qiangge5586-a11y/1/actions/runs/42160365707) passed on commit `ae42884a1b2d169aa156561c0daf7d600cce00d1`.
- The Java 7 compile and full self-test step completed successfully. The new runner test printed: `PASS: API17 su runner allowlist and argument safety`; the coordinator, backup metadata, journal, recovery, read-only executor, diagnostic parser, USB and NCM tests also passed.
- The matching Android Build run [#42160365649](https://github.com/qiangge5586-a11y/1/actions/runs/42160365649) is still in progress at the Gradle setup stage at the time of this update; no APK artifact for this exact commit is confirmed yet.
- A prior build on commit `8f1fa6a692906536d4b5892baa84a9165f9a0426` passed and included the runner source; the current `ae42884` change only exposes the validator for its self-test. The new build is still required for final artifact confirmation.
