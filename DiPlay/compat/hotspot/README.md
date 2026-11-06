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

The coordinator self-test was expanded in commit `57b626cc943930f95d6d3e01ff61819b530d2f42` to verify that a missing/unverified backup blocks `apply()` and does not attempt restore, and that malformed transaction IDs are rejected. GitHub Actions run #8 passed; its log records `PASS: success, rollback, backup gate and transaction ID validation` plus the safety-primitives test pass: https://github.com/qiangge5586-a11y/1/actions/runs/42144965197.

## Durable journal prototype (2026-11-06)

`AppendOnlyHotspotJournal.java` adds an app-private append-only journal with SHA-256 hash chaining, OS file locking, UTF-8 detail encoding, a 1 MiB size ceiling, and `FileDescriptor.sync()` before returning. Reads validate the complete chain and fail closed on malformed, truncated, or tampered records. The constructor requires an existing writable private directory and rejects a journal path whose canonical parent escapes that directory.

`AppendOnlyHotspotJournalTest.java` verifies empty state, multiple persisted transitions, UTF-8 round-trip, tamper detection, and refusal to append to a corrupted journal. GitHub Actions run #12 passed all three test suites, including `PASS: durable journal append, UTF-8 recovery and tamper fail-closed`: https://github.com/qiangge5586-a11y/1/actions/runs/42145390895.

This is a durable journal primitive, not the complete recovery system. It does not yet implement boot-time recovery decisions, backup file byte storage/restoration, network-state restoration, Android process lifecycle integration, or production RootOps. The journal must be placed in DiPlay's app-private internal storage; do not place it on removable/shared storage. No car head-unit ROOT commands or system writes were run.

## Startup recovery classification (2026-11-06)

`HotspotRecoveryPolicy.java` classifies the latest verified journal state without performing any action: `NEW`/`PREFLIGHTED`/`BACKED_UP` are classified as no system change recorded; `ACTIVE` requires process identity verification; `ROLLED_BACK` is clean; `APPLYING`/`ROLLING_BACK`/`RECOVERY_REQUIRED` require manual recovery. This deliberately does not auto-stop daemons or restore files during app startup. `HotspotRecoveryPolicyTest.java` covers every state and the empty-journal case. CI coverage is wired into the current workflow; see [latest hotspot workflow runs](https://github.com/qiangge5586-a11y/1/actions).

The policy is only a decision primitive. A production startup recovery coordinator still needs to validate journal ownership and backup integrity, verify the recorded process PID/start-time/cmdline against live `/proc`, and require a verified backup before any restore. Ambiguous or corrupt state must remain blocked for operator review.

Recovery-policy CI note: run #17 exposed a test-fixture issue (the test attempted to call the journal Entry constructor outside its package); this was a test-only visibility problem, not a production compile/runtime failure. The test was corrected to persist and reload real journal entries. Run #19 then completed successfully and the log confirms all four suites passed, including `PASS: startup recovery classification fails closed`: https://github.com/qiangge5586-a11y/1/actions/runs/42145618880.

Security limitation: the SHA-256 chain detects accidental corruption and edits that do not recompute the chain; it is not an authenticated log. An attacker able to rewrite the entire app-private journal can recompute the hashes. Before production use, consider an app-keystore-backed MAC/signature where supported by the actual API 17 environment, and define key-loss/recovery behavior. `FileDescriptor.sync()` syncs the file contents but this prototype does not fsync the containing directory after first creation. Treat it as a tested persistence primitive, not a tamper-proof audit log or complete power-loss guarantee.


## Fixed read-only operation catalog (2026-11-06)

`HotspotReadOnlyOperation.java` introduces a small API-17-friendly catalog of fixed diagnostic operations: ROOT identity, Wi-Fi/connectivity service state, network interface counters, and metadata checks for the known Wi-Fi configuration and hostapd/dnsmasq binary paths. Each plan uses a fixed absolute executable and a fixed argument array; callers cannot supply an executable, arbitrary path, or shell fragment.

The catalog is descriptive only. It does not execute a process, invoke a shell, access the filesystem, or modify device state. It intentionally contains no process termination, file write/restore, interface configuration, or daemon launch operation. `HotspotReadOnlyOperationTest.java` checks fixed mappings and per-instance argument-array isolation.

The test was added to the Java 7 CI workflow. Run #24 was triggered by commit `43368bb2aa1fb7577e66a13a53d2f4662037e8cc`; its result must be checked before treating this addition as CI-verified.

This catalog is not yet a production ROOT adapter. The next implementation gate is a fake executor with fixture outputs, strict output parsing, timeout/error handling, and tests proving that no arbitrary command or path can be introduced. Only after those tests should an actual read-only process executor be considered.


## Fake read-only executor boundary (2026-11-06)

`HotspotReadOnlyExecutor.java` adds an injectable `Runner` boundary. It accepts only the fixed operation enum, obtains executable/argv from the catalog, applies bounded timeout and output-size settings, and fails closed on null results, timeout, nonzero exit status, or oversized output. The operation catalog now returns defensive copies of argument arrays; callers cannot mutate a stored plan through its accessor.

`HotspotReadOnlyExecutorTest.java` uses a fake runner and fixture strings only. It verifies fixed argv delivery, timeout propagation, successful fixture output, timeout failure, nonzero exit failure, output-size rejection, and invalid bounds. The fake runner does not start a process and no su/shell command is executed.

The GitHub workflow now compiles and runs this sixth suite. This is an executor boundary and simulation test, not a production process runner. A real Android 4.2.2 adapter remains blocked until the exact device command paths, output formats, process I/O handling, and timeout behavior are verified. The next safe step is to add typed parsers for fixture outputs (ROOT identity, interface table and metadata) and test malformed/ambiguous outputs before considering any real read-only process execution.
