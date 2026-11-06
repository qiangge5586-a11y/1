# DiPlay 0.3.11 MFi Authentication — Source Audit and Optimization Gate

Date: 2026-11-06
Target: DiPlay 0.3.11, Honda MY16ADA / Android 4.2.2 / API 17
Evidence: original decompiled Smali from workflow artifact `diplay-0.3.11-decompiled.zip`; source paths listed below.
Scope: static source review only. No MFi coprocessor, iPhone, Bluetooth session, remote backend, or vehicle was contacted.

## Executive result

There are meaningful reliability and release-hygiene improvements available. The most important are (1) make the whole authentication deadline cover synchronous certificate/signature provider calls, (2) make I2C retry and cancellation bounded at the operation level, and (3) keep the bundled offline identity path strictly separated from production authentication.

Do not alter the cryptographic transcript, certificate chain, key derivation, signature format, or authentication success conditions without source-backed fixtures and legitimate MFi credentials. Do not fake success, replace certificates, or use the offline identity resources as a production credential.

## Source components located

- `mfi/Iap2MfiAuthenticationClient.smali`: iAP2 message-level MFi certificate/challenge exchange.
- `mfi/MfiAuthenticationClient.smali`: I2C authentication-coprocessor register protocol.
- `mfi/LocalMfiAuthenticationClient.smali` and its Companion: local software identity provider.
- `mfi/RemoteMfiAuthenticationClient.smali`: remote provider and certificate-info cache.
- `mfi/MfiAuthenticator.smali`: provider interface.
- `airplay/MfiSapAuthSetup.smali`: separate AirPlay MFi SAP setup.
- `DiPlayBootstrap.smali`: LOCAL identity asset installation/bootstrap.
- `orchestration/MfiTarget.smali`: LOCAL, USB_CH341, I2C, REMOTE targets.
- `orchestration/CarPlayController.smali`: runtime target dispatch and authentication lifecycle.
- APK assets include an `offline-mfi` identity fixture package. Its contents are intentionally not reproduced here.

## Confirmed protocol behavior

### iAP2 message exchange

The source recognizes:
- `0xAA00`: request certificate
- `0xAA01`: certificate response
- `0xAA02`: request challenge
- `0xAA03`: challenge signature response
- `0xAA04`: authentication failed
- `0xAA05`: authentication succeeded

The challenge parameter is bounded to 1..128 bytes. The certificate output is bounded to at most 65,525 bytes. The client defaults to a 30-second authentication timeout and accepts caller-supplied values up to 300,000 ms. Unexpected message IDs and malformed challenge parameters fail closed.

The client creates one monotonic deadline and uses the remaining budget for session receive/send waits. However, it invokes the authenticator's `readCertificate()` synchronously before entering the receive loop, and invokes `signChallenge()` synchronously while handling the challenge. Those provider methods do not accept the caller's deadline or a cancellation token. Thus the outer iAP2 timeout does not strictly bound total wall-clock authentication time when a provider call stalls or retries internally.

### I2C coprocessor provider

The source uses register-based I2C transactions:
- certificate length/data registers and challenge length/data registers;
- 128-byte certificate data windows;
- challenge input bounded to 1..128 bytes;
- authentication start/status polling, with a 10 ms initial delay, 10 ms polling interval and a 3-second status deadline;
- I2C transaction retry helper with a 2-second per-operation retry deadline and 50 ms retry delay;
- one static coprocessor lock serializes certificate reads and challenge signing.

The certificate reader validates the reported certificate length and reads it in bounded windows. This is good defensive behavior and should be preserved.

Two reliability gaps are visible:
1. The 2-second retry deadline is created separately for each register operation. A long certificate spanning many 128-byte windows can therefore consume many independent retry budgets; there is no single outer deadline for the complete certificate read.
2. The sleep helper restores the thread interrupt flag after InterruptedException, but the authentication polling loop catches transport failures and continues until its own deadline. Once interrupted, subsequent sleeps may return immediately, creating a tight polling loop instead of promptly cancelling the operation.

The static global lock is conservative and protects a shared coprocessor from interleaved register transactions. Do not replace it with per-instance locking unless the bus/device ownership model proves that multiple clients cannot address the same chip concurrently. A future per-bus/per-address lock is only an optimization after that proof.

### Local offline identity and release hygiene

The original APK contains an `offline-mfi` asset directory with certificate/private-identity material. `DiPlayBootstrap.ensure()` copies that directory into app-private storage when `MfiTarget.LOCAL` is selected; the UI/runtime configuration has a LOCAL target and its fallback/default path must be reviewed carefully.

The source also contains a local provider and a local signature self-test probe. Static evidence does not establish that the bundled offline identity is an authorized production MFi identity. Treat it as test-only material unless its authorization and intended deployment are independently documented.

Required release safeguard:
- keep offline identity fixtures out of production APKs and production source sets;
- add a release-artifact scan that fails if the offline identity directory or private-key-like files are packaged;
- never use fixture identity material as a substitute for an MFi authentication coprocessor or an authorized provider;
- make LOCAL/test provider selection explicit and visibly diagnostic; no silent fallback from I2C/USB/REMOTE failure to LOCAL.

No certificate, private key, signature, challenge bytes, or fixture contents are included in this audit.

### AirPlay MFi SAP is a separate phase

`MfiSapAuthSetup` uses an ephemeral X25519 key exchange and derives AES-CTR material through the source-defined hash/transcript steps. It calls the MFi authenticator for certificate/signature operations and constructs the SAP response. This is not the same exchange as the iAP2 `0xAA00..0xAA05` message flow.

Preserve the source transcript and cryptographic algorithms exactly. Optimize only resource handling, bounded execution, error classification and API-level compatibility unless protocol fixtures demonstrate a correctness defect. API 17 crypto-provider availability and performance still need runtime verification.

## Recommended optimizations

### P0 — Bound the entire provider operation

Introduce an authentication operation context carrying:
- one absolute monotonic deadline for the complete iAP2 authentication;
- phase deadlines capped by that overall deadline;
- cancellation/interruption state;
- generation/session token for reconnect isolation.

Provider calls should receive the same deadline/cancellation context, or be adapted behind a bounded worker whose completion cannot mutate a newer session. Do not reset a fresh full timeout for each certificate window or retry. Keep protocol-required I2C polling/retry cadence unchanged unless hardware traces justify a change.

### P0 — Correct interrupt/cancellation semantics

At every I2C retry/poll boundary:
- check cancellation and thread interruption before starting another bus transaction;
- propagate cancellation as a distinct terminal error;
- do not convert cancellation into another retry;
- preserve interrupt status for callers;
- guarantee the coprocessor lock is released on every exit.

Tests must prove an interrupted poll exits promptly and does not spin until the 3-second deadline.

### P0 — Separate test identity from production credentials

- Exclude `offline-mfi` fixtures from release artifacts.
- Require explicit test-mode selection for LOCAL.
- Do not silently switch to LOCAL after I2C, USB or REMOTE provider errors.
- Keep credential bytes out of logs, crash reports and test snapshots.
- Validate release APK contents with a deterministic archive scan.

### P1 — Improve I2C transport diagnostics and bounded reads

- Preserve the 128-byte register window unless the chip specification/source evidence supports another size.
- Record register index, requested byte count, retry count, elapsed time and failure class; never record certificate/challenge/signature bytes.
- Enforce one aggregate certificate-read deadline and a maximum total byte count.
- Distinguish NACK/no-device, short read, bus I/O failure, invalid length, coprocessor-reported error and authentication timeout.
- Avoid retrying deterministic malformed data or invalid parameters.

### P1 — Harden iAP2 exchange state

- Keep the existing strict message-ID handling and parameter validation.
- Ensure certificate-read and challenge-sign operations cannot outlive the session generation.
- On timeout, malformed response, explicit authentication failure or provider failure, fail the current authentication generation and prevent stale replies from being sent on a reconnected session.
- Log message IDs, phase, byte counts and elapsed time only; never log cryptographic payloads.

### P1 — Validate provider/backend selection

Audit persisted target loading, UI default, and runtime dispatch as one flow. The backend must be selected explicitly and surfaced in diagnostics:
- LOCAL: test-only provider, not a production fallback;
- I2C: coprocessor path;
- USB_CH341: external USB bridge path;
- REMOTE: remote provider with network availability and trust requirements.

Do not assume that selecting or initializing a backend proves that it can complete a challenge on the target hardware.

### P2 — API 17 and crypto runtime checks

The source uses Kotlin 2.2-era code and BouncyCastle in the local provider, plus the AirPlay crypto implementation. A successful modern CI build does not establish API 17 runtime compatibility. Isolate newer framework references, check provider availability, and run crypto known-answer tests on an API 17-compatible runtime before enabling the path on MY16ADA.

## Test plan before implementation

1. I2C fake-transport fixtures: exact register select/read/write order, short reads, NACK, transient I/O, permanent I/O, error-register read, certificate lengths at 0/1/128/129/max, final partial window.
2. Deadline tests with injected monotonic clock: retry budget exhaustion, whole-certificate deadline, challenge signing deadline, deadline arithmetic wraparound.
3. Cancellation tests: interrupt during initial delay, status polling, transaction retry, certificate window read; assert prompt exit and lock release.
4. iAP2 scripted-session tests: certificate request, challenge, valid signature reply, explicit fail, success, unknown message, missing parameter, malformed length, timeout while provider blocks, reconnect with stale provider completion.
5. Release hygiene: scan built APK for offline fixture directory/private identity resources; assert no such material in release.
6. API 17 bench test with a legitimate, known-compatible MFi provider and a known-compatible iPhone. Record phase timings and failure classes without recording credential bytes.

## Evidence boundary / not done

- This is a static Smali audit, not a live MFi authentication test.
- No MFi chip/I2C bus/CH341 device or remote MFi server was contacted.
- No iPhone pairing or iAP2 authentication was performed.
- No authentication algorithm, credential, certificate, private key or release artifact was modified.
- The compatibility repository does not yet contain a production MFi provider/session integration. This audit is a source-backed implementation gate, not a claim that MFi is already optimized.


## Follow-up implementation and backend-selection verification (2026-11-06)

A second pass traced the persisted backend selection path rather than relying only on the runtime config constructor:

- `AirPlayPersistence.loadMfiTarget()` reads the `mfi_target` preference. When the key is absent or does not match a known enum value, it returns `MfiTarget.LOCAL`.
- `DiPlayBootstrap.ensure(context, target)` copies the bundled `offline-mfi` resources into app-private storage when the selected target is LOCAL, and returns without copying for other targets.
- The runtime config's synthetic constructor default of USB_CH341 does not override the separate preference loader's LOCAL fallback. The preference loader is the important first-run/default path.
- This confirms a P0 release/configuration risk: a fresh install or invalid stored target can select LOCAL and bootstrap offline identity material without a user explicitly choosing test mode. The evidence does not establish that the fixture is authorized for production authentication.
- The controller dispatches LOCAL, USB_CH341, I2C and REMOTE through separate backend branches. This static pass did not find evidence sufficient to claim that every provider failure path is fully isolated from all fallback paths; verify each error/retry branch before production integration.

### Compatibility implementation added

- `compat/mfi/MfiOperationContext.java`: shared monotonic timeout, cancellation and session-generation boundary.
- `compat/mfi/MfiI2cCertificateReader.java`: source-grounded bounded certificate read using length register 0x30, data windows beginning at 0x31, 128-byte maximum windows, one caller-owned deadline and a process-wide reader lock. The hardware-specific register transport remains an interface and must enforce the same context during each blocking bus call.
- `compat/mfi/MfiBackendSelectionPolicy.java`: rejects missing/unknown persisted targets, allows LOCAL only with explicit test mode, and disallows automatic backend fallback.
- Fake transport and policy tests cover window boundaries (1, 128, 129 and 65,525 bytes), short reads, aggregate timeout, cancellation, lock release, invalid target and LOCAL test-only selection.

### Current implementation boundary

These classes are isolated compatibility primitives, not yet wired to the original app's real I2C/CH341/remote provider or the iAP2 MFi message exchange. The fake transport proves the bounded reader's behavior against the source-derived register-window contract; it does not prove the target car's I2C device, bus timing, retry semantics or authentication success. The original APK's persisted LOCAL fallback is documented as a finding, not modified by this compatibility-layer work.


### API 17 compatibility checkpoint

The new compatibility primitives use only Java language level 7 and core APIs available well before Android API 17 (`System.nanoTime`, `AtomicBoolean`, thread interruption state, `Locale.US`, arrays and synchronization). They compile in the Java 7 CI job and are included in the isolated Android module whose manifest/build metadata declares minSdk 17.

This is a source/build compatibility checkpoint, not an API 17 runtime certification. The original AirPlay SAP path still requires runtime verification for its X25519 implementation, AES/CTR and hash providers; the original local identity provider references BouncyCastle. No API 17 emulator/device test, real MFi coprocessor test, or legitimate-provider/iPhone authentication was performed. Keep production MFi enablement gated until those checks are run on the actual Android 4.2.2 target with authorized hardware/credentials.
