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
