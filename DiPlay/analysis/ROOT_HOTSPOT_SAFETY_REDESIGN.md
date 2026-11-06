# DiPlay ROOT Hotspot Safety Redesign

## Scope and evidence

Target baseline: Honda MY16ADA / Andromeda / vcm30t30, Android 4.2.2 (API 17), ARMv7, rooted.

Reviewed from the original `diplay0.3.11.apk` decompilation artifact:

- `com.shilapi.xcertplay.network.HostapdHotspotManager`
- `com.shilapi.xcertplay.network.HostapdConfig`
- `com.shilapi.xcertplay.network.RootShell`

The decompiled Java-like source is partial output from JADX; behavior must be cross-checked against Smali before patching. The findings below are directly visible in the extracted methods and were locally checked against the decompilation artifact.

## Confirmed high-risk behavior

1. **Global process-name kill:** `killStaleForStartScript()` and `killStaleForTeardownScript()` iterate every `/proc/[0-9]*`, inspect `comm`, and issue `kill -9` for any process named `hostapd` or `dnsmasq`. This does not establish that DiPlay owns those processes.
2. **Legacy broad kill:** `killStaleScript()` parses `ps` and kills matching process rows without an app-owned PID registry.
3. **System config overwrite:** startup copies generated configuration directly to `/data/misc/wifi/hostapd.conf` without a visible prior backup.
4. **System config deletion:** `removeGeneratedFilesScript()` includes `/data/misc/wifi/hostapd.conf` in an `rm -f` command. This is not a temporary/generated path and must never be deleted as ordinary cleanup.
5. **Wi-Fi state not snapshotted:** startup issues `WifiCtl OFF`; teardown issues `WifiCtl ON`. The reviewed manager does not persist the original framework Wi-Fi state before toggling it.
6. **RootShell path concatenation:** `writeFile(path, content)` builds a shell command using string concatenation for `path`. It is safe only if all callers use fixed trusted paths; it must not accept user-controlled paths.

## Required replacement architecture

Do not patch by simply changing `kill -9` to `kill`. The replacement must be ownership-aware and transaction-based.

### A. Read-only preflight

Collect, without changing state:

- actual wireless interface(s) from sysfs and `/proc/net/dev`; do not assume `wlan0`
- Wi-Fi framework state and current connectivity
- existing hostapd/dnsmasq PIDs, `/proc/<pid>/comm`, `cmdline`, executable link and process start-time
- existence, hash, owner, group, mode and SELinux context of the existing hostapd config
- available `hostapd`, `dnsmasq`, `iw`, `ip`, `ifconfig`, `restorecon`, `chcon` and hash utilities
- current interface addresses and routes

Unknown or incomplete capability means **do not start**. The report must show the missing capability.

### B. Durable backup transaction

Before any write:

1. Acquire a single-instance lock.
2. Create an app-owned backup directory under a root-protected location, mode 0700.
3. Record whether the config existed. If it existed, copy exact bytes and preserve mode/uid/gid; capture SELinux context where available.
4. Record the original Wi-Fi framework state, interface state, addresses/routes, and the exact PIDs/start-time identities of any pre-existing services.
5. Hash the backup and verify it before proceeding.
6. Persist a transaction record with a unique ID and state `PREPARED`.

If any required metadata cannot be preserved or verified, abort before changing the device.

### C. Apply

- Require an explicit user action after presenting the preflight summary and backup result.
- Render configuration only from validated values (SSID/passphrase/channel).
- Stage under a unique temporary filename; validate the config with hostapd's test/config mode if supported.
- Never overwrite the system config until the backup is verified.
- Prefer atomic rename on the same filesystem; otherwise use a verified copy-and-restore path.
- Record only PIDs actually launched by this transaction, including process start-time and command identity.
- Never stop or signal processes merely because their name is hostapd/dnsmasq.

### D. Rollback and recovery

On startup failure, timeout, user stop, app crash recovery or reboot recovery:

1. Signal only verified app-owned processes (TERM, bounded wait, then KILL only if identity still matches).
2. Restore the original config bytes, existence state, owner/group/mode and SELinux context.
3. Restore the Wi-Fi framework state to the recorded pre-transaction state, not unconditionally ON.
4. Restore only network interface state changed by this transaction.
5. Verify the restored config hash and state.
6. Persist `ROLLED_BACK` or `RECOVERY_REQUIRED`; never silently discard a failed transaction.

If the original config did not exist, rollback removes only the transaction-created file. It must not remove a pre-existing file.

### E. Cleanup allowlist

Only transaction-owned temporary files may be removed (for example the transaction's own staged config and log files). The system path `/data/misc/wifi/hostapd.conf` is explicitly forbidden in generic generated-file cleanup.

## API 17 implementation constraints

- Use Java 7-compatible language/library features if implementing the helper in the legacy APK.
- Avoid API 21+ or API 26+ classes in eagerly loaded paths.
- Use the existing API 17-compatible `su` process mechanism only after validating exit status, timeout and output handling.
- Do not depend on GNU-only shell flags; Honda Android 4.2 toolbox/Posix shell may be limited.
- Avoid assumptions about `stat`, `sha256sum`, `restorecon`, `chcon`, `readlink` or `ip`; probe availability and fail closed if required metadata cannot be restored.
- Do not change the live head unit during static analysis.

## Verification plan

1. Static audit must report all six findings above against the original APK.
2. Unit tests cover transaction state transitions, missing original config, backup hash mismatch, permission/context preservation failures, timeout, process PID reuse and interrupted recovery.
3. Shell integration tests run against a disposable test root with fake `su`, `ps`, `/proc`, Wi-Fi control and config paths.
4. On the Honda device, run the read-only probe first and archive output.
5. Only after code review and a separately confirmed recovery plan should a user-triggered backup/restore test occur while parked and with no dependency on the hotspot for recovery.

## Automated verification result

- GitHub Actions run #42142476134 completed successfully: [decompile + ROOT hotspot safety audit](https://github.com/qiangge5586-a11y/1/actions/runs/42142476134).
- Artifact: `diplay-0.3.11-decompiled` (55.6 MB, retained until 2026-12-06).
- The generated `hotspot-safety-audit.md` independently reported all six configured findings: three CRITICAL and three HIGH (including legacy kill, config deletion/overwrite, Wi-Fi state toggling and shell path concatenation).
- The workflow success means the audit completed and the artifact was uploaded; it does not mean the original hotspot implementation passed the safety audit.

## Current implementation status

- [x] Static findings confirmed in decompiled source.
- [x] Automated source-pattern audit added to GitHub Actions.
- [ ] Implement transaction manager and integrate into a reproducible DiPlay source/build project.
- [ ] Replace global process-name kills with ownership-aware process tracking.
- [ ] Remove system config from generic cleanup.
- [ ] Verify backup/restore metadata behavior on API 17.
- [ ] Run bench and in-vehicle parked tests.

No live ROOT command has been issued to the user's vehicle, and no modified DiPlay APK has yet been built.
