# ROOT Hotspot Transaction Coordinator

This is the first executable Java-side transaction coordinator for the Honda/API 17 adaptation. It is intentionally separate from the decompiled APK: the original APK has no reproducible source project, and this class is not yet wired into DiPlay.

## Implemented
- Java 7-compatible transaction state machine and strict transaction ID validation.
- Read-only preflight must complete before backup.
- Apply is blocked until a verified backup exists.
- Only the process identity returned by this transaction can be passed to stopIfStillOwned.
- Failure after backup triggers rollback; failure before backup is marked RECOVERY_REQUIRED.
- Journal transitions are persisted through an injected interface.

## Production adapter still required
1. Fixed-command su protocol with no user-controlled shell interpolation.
2. Actual Wi-Fi interface and framework state discovery.
3. Durable backup of exact bytes, existence, uid/gid, mode and SELinux context where supported.
4. Hash verification using a tool present on target firmware or bundled implementation.
5. PID/start-time/cmdline checks to guard PID reuse.
6. Durable journal and boot-time recovery.
7. Device-specific network state capture and restore.

If required metadata cannot be captured or restored, the adapter must fail closed before changing the live system.

## Verification and limits
CoordinatorSelfTest covers success and post-launch verification failure with rollback. The workflow compiles with Java 7 language/API compatibility and executes the test. This does not execute su, Wi-Fi, hostapd, dnsmasq or vehicle commands. This is not yet a patch to the original APK, and no modified APK or vehicle-side test is claimed.


## Current repository status (2026-11-06)

The coordinator, self-test and GitHub Actions workflow are committed. The workflow uses JDK 8 to compile source/target 1.7, because newer JDK releases no longer accept Java 7 source/target flags. A local Java 8-bytecode smoke test passed in the available JDK 21 environment; the repository workflow is the authoritative Java 7 bytecode check.

The next integration step is to implement a real RootOps adapter against the decompiled DiPlay APIs and test it against a disposable rooted filesystem. The adapter must treat partial failures inside apply() as its own rollback responsibility until it can return a ProcessIdentity. The current coordinator is a tested control-flow boundary, not yet a safe device driver.

## Safety primitives added (2026-11-06)

`HotspotSafetyPrimitives.java` is a pure Java 7 helper with no filesystem, process, network or shell side effects. It provides:
- fail-closed validation of backup existence/hash/uid/gid/mode and required SELinux-context metadata;
- Linux `/proc/<pid>/stat` start-time parsing that handles spaces and closing parentheses inside the process comm field;
- exact PID + start-ticks + cmdline identity comparison to reject PID reuse and unrelated daemons;
- absolute-path allowlist validation with traversal rejection.

`HotspotSafetyPrimitivesTest.java` covers valid and incomplete backup metadata, originally absent config, malformed proc stat, PID reuse, command mismatch, approved paths and traversal/out-of-root paths.

GitHub Actions run #6 compiled all helper/test sources with Temurin JDK 8 using Java 7 source and bytecode targets. The job log confirms both self-tests passed:
- `PASS: success and verified rollback paths`
- `PASS: backup metadata, proc identity, PID reuse and path allowlist`

Run: https://github.com/qiangge5586-a11y/1/actions/runs/42144865377

This is unit-test evidence for pure Java primitives only. No actual backup bytes, filesystem metadata, SELinux context, /proc files, su process, Wi-Fi service, hostapd or dnsmasq were accessed. The production `RootOps` adapter, durable journal/reboot recovery, original APK integration and real-device verification remain unimplemented.
