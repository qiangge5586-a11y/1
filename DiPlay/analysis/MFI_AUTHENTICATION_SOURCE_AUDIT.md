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


## CI verification record (2026-11-06)

- Java 7 offline protocol/MFi test run: [run 42200211252](https://github.com/qiangge5586-a11y/1/actions/runs/42200211252) — passed. MFi checks: operation context 16; I2C certificate reader 1,065; backend selection policy 12. Existing iAP2/RFCOMM test suite also passed.
- Android compatibility APK build and hygiene scan: [run 42200474000](https://github.com/qiangge5586-a11y/1/actions/runs/42200474000) — passed. APK metadata confirmed minSdk 17 and targetSdk 28; hygiene scanner's 3 tests passed; built APK scan reported no offline identity/private-key material.
- Artifact: `diplay-honda-compat-debug`, ID `13493602871`, SHA-256 `dbd3c12bc4adad87248de96bbe0d3ccf08dab37a11de6f25d922a123806693de`.

These are offline Java/CI and APK archive checks only. They do not prove API 17 device runtime behavior, actual I2C/CH341 access, legitimate MFi authentication, iPhone pairing, or operation on the Civic head unit.


## Provider-session boundary follow-up (2026-11-06)

Added a Java 7/API-17-compatible `MfiProviderSession` adapter boundary around the certificate and challenge-signing phases:

- The same `MfiOperationContext` instance is passed to both provider methods; remaining time is not reset between phases.
- The session enforces NEW → CERTIFICATE_READY → CHALLENGE_SIGNED transitions and fails closed on provider errors, invalid certificate/signature results, malformed challenge lengths, cancellation, timeout, interruption, or generation mismatch.
- Returned certificate/signature buffers and submitted challenge buffers are copied at the boundary to avoid provider-side mutation after return.
- `cancel()` cancels the shared operation context; stale session generations are rejected before provider work and again after provider completion.
- Important limitation: this wrapper cannot forcibly interrupt a provider implementation that ignores the context while blocked. Each real I2C/USB/remote provider must use the same context to bound every blocking operation and check it before/after work. This is an integration contract, not an implemented hardware provider.

CI: [Java 7 iAP2/MFi test run 42201889265](https://github.com/qiangge5586-a11y/1/actions/runs/42201889265) passed. `MfiProviderSessionTest` passed 14 checks; operation context 16, I2C certificate reader 1,065, backend selection 12, plus the existing iAP2/RFCOMM offline suite passed. This is host-side offline testing only; no real MFi chip, iPhone, API 17 device, or vehicle session was exercised.

The isolated Android app source set already includes `../../compat/mfi`, so the new provider-session class is part of Android compilation input. A separate APK build for this exact commit is still required before claiming packaged APK verification.

## Next integration gate

The next code step is to adapt the original iAP2 MFi message exchange (`0xAA00..0xAA05`) to this provider-session contract while preserving the exact wire transcript and success/failure semantics. The compatibility repository still has no production iAP2 MFi message client or hardware-backed provider; do not enable authentication or claim interoperability until that integration is source-parity tested and exercised with an authorized provider and iPhone.

Android package check for the provider-session code: [build run 42201889184](https://github.com/qiangge5586-a11y/1/actions/runs/42201889184) passed Gradle assemble, AAPT package/SDK metadata verification and MFi APK hygiene scanner. Artifact ID: 13495475371; SHA-256: 076ce8eacd48abbc30c252e9f88b8974c16f49c60032a84d90068dfcd11198c2. This packages the compatibility session boundary but does not connect it to a production authenticator or validate API 17 runtime behavior.


## Source-parity follow-up: iAP2 exchange and cancellation (2026-11-06)

A direct second inspection of the original decompiled Iap2MfiAuthenticationClient.smali and Iap2AuthenticationMessages.smali confirms:

- The original client reads the certificate before entering its receive loop, but sends the certificate response only after receiving 0xAA00.
- MfiCertificateType has two values: MFI and BAA.
- For MFI, the certificate response uses the source accessoryCertificate() builder, which builds parameter 0 as bytes.
- For BAA, the source calls accessoryCertificateBody() on a provider-supplied body after parsing it as an Iap2ParameterList; this is not equivalent to treating the body as a single certificate byte string.
- The source also has a distinct baaCertificatePackage(leaf, intermediate) builder: parameter 0 is leaf bytes, parameter 1 is u8 value 1, parameter 2 is intermediate bytes.
- Challenge extraction validates the incoming message ID and reads parameter 0. The caller then enforces challenge length 1..128.
- The signature response builder places signature bytes in parameter 0.
- The original flow accepts 0xAA05 as success only after the challenge/signature path, and throws on 0xAA04.

The compatibility coordinator is intentionally still a state/provider boundary: it does not yet serialize Iap2Frame or reproduce the MFI/BAA certificate builders. The BAA provider contract is not represented by the current single-certificate MfiProviderSession; do not enable BAA through that adapter until the leaf/intermediate/body contract is added and fixture-tested.

### Cancellation race fixed in compatibility coordinator

The coordinator previously held its monitor while calling provider methods. A blocking certificate read or challenge signer could therefore prevent a reconnect thread from acquiring the coordinator monitor to cancel the operation. Provider work now runs outside the coordinator monitor with explicit in-progress states. Completion re-enters the monitor and verifies that the operation state has not been cancelled or superseded before returning a reply. MfiProviderSession.cancel() also now cancels the shared context after signature completion, allowing terminal cleanup to invalidate the context.

Java 7 offline test run: run 42207282507 — compile/test step passed, including the new blocked-provider cancellation test. Android package run 42207282456 was still running at the time of this audit update.

Evidence boundary: no iPhone, real coprocessor, I2C bus, CH341 adapter, remote provider, or API 17 device was exercised.


## Source-parity follow-up: MFi CSM parameter and message codec (2026-11-06)

A further inspection of the original `Iap2AuthenticationMessages`, `Iap2ParameterList`, `Iap2Parameter` and `Iap2CsmFramer` implementations establishes the exact framing contract now represented in compatibility code:

- A CSM frame is a six-byte header: magic `0x4040`, total frame length u16 big-endian, message ID u16 big-endian, followed by payload.
- Each parameter is `length:u16be` (including its four-byte header), `parameterId:u16be`, then opaque value bytes. The parser rejects a truncated header, length below four, or length beyond the remaining body.
- The original MFI certificate and signature builders each encode parameter 0 as bytes.
- The original BAA body path parses the supplied byte array as a complete parameter list, then uses that same body directly in the AuthenticationCertificate frame. It does not add a new parameter wrapper.
- The separate BAA package builder emits parameter 0 = leaf bytes, parameter 1 = u8 value 1, parameter 2 = intermediate bytes.
- The original challenge extractor first requires the request-challenge-response message ID, then reads parameter 0.

Added `Iap2ParameterCodec` and `MfiIap2MessageCodec`, plus offline tests for MFI certificate/signature, BAA body pass-through, BAA leaf/intermediate package, challenge extraction, malformed parameter lengths and malformed CSM frame headers. The test is now part of the Java 7 CI workflow.

This is a wire-format compatibility layer only. It is not yet connected to `Iap2CsmReceiveCoordinator` / `Iap2CsmPumpSender` or to the RFCOMM/TCP transport. Challenge length enforcement and the provider operation deadline remain in the provider/session layer. BAA provider material acquisition is still not implemented; these builders only encode already-supplied bytes. No real MFi hardware, iPhone, API 17 device, or vehicle was tested.


## Source-parity follow-up: MFi CSM adapter boundary (2026-11-06)

Added `MfiIap2CsmAdapter` to connect complete inbound CSM frames to the existing generation-safe exchange coordinator:

- Incoming `0xAA00` now drives certificate acquisition and emits a complete `0xAA01` CSM frame using parameter 0.
- Incoming `0xAA02` is decoded through the source-audited parameter parser, passed to the challenge signer, and emitted as a complete `0xAA03` CSM frame.
- Incoming `0xAA04` / `0xAA05` are routed to the coordinator's terminal failure/success transitions and produce no outbound reply.
- Unexpected, malformed, or out-of-order messages fail the current exchange; adapter errors request cancellation rather than allowing a stale exchange to continue.

Offline Java 7 CI: [run 42208373713](https://github.com/qiangge5586-a11y/1/actions/runs/42208373713) passed. The new adapter test passed 9 checks; the MFi message codec passed 16 checks; the coordinator passed 16 checks; provider-session passed 21 checks; I2C certificate reader passed 1,070 checks; the existing iAP2/RFCOMM offline suite also passed.

Integration boundary remains explicit: the adapter consumes/returns complete CSM frames but does not read from `Iap2CsmReceiveCoordinator`, enqueue into `Iap2CsmPumpSender`, or write to RFCOMM/type-130 TCP itself. The transport owner must call it from the single-threaded CSM pump and submit returned frames through the existing bounded sender. This is the next integration task. BAA encoding helpers are present, but provider-side BAA identity/material selection remains disabled/unimplemented. No MFi chip, iPhone, API 17 device, or Civic head unit was exercised.


## Follow-up: CSM queue and pump bridge (2026-11-06)

Added `MfiIap2CsmPumpBridge` as a single-consumer compatibility layer. It drains complete messages from `Iap2CsmReceiveCoordinator`, reconstructs complete CSM frames, passes them to `MfiIap2CsmAdapter`, and submits generated responses through `Iap2CsmPumpSender`. This preserves the existing bounded pump command queue, peer maximum control-payload splitting, and shared capacity deadline.

The bridge must run on a dedicated application/CSM consumer worker, not on the link-pump worker or its callbacks. AA04/AA05 remain terminal peer outcomes with no response frame. Explicit teardown cancels the provider session and closes the old pump. The new offline test exercises concatenated AA00, AA02 and AA05 messages through the receive queue and verifies AA01/AA03 are emitted in order.

The Java 7 CI workflow now runs this integration test. The compatibility-layer receive/adapter/send path is connected; a production iAP2 Driver, concrete provider implementation, worker lifecycle integration, and API 17 hardware validation are still outstanding.


## Follow-up: dedicated CSM worker lifecycle (2026-11-06)

Added `MfiIap2CsmWorker` and a blocking `processNext(timeout)` entry point on the bridge. The worker now waits on the receive coordinator's bounded queue instead of requiring external polling, processes messages serially, and reports peer success, peer rejection, or transport/processing failure through a listener. It runs on a dedicated daemon thread and must not share the link-pump worker thread.

On peer AA05, the exchange coordinator now releases the completed provider session/context after recording SUCCEEDED. This prevents a completed authentication operation from retaining its deadline and provider state. The integration test now exercises the worker thread, ordered AA01/AA03 responses, AA05 success callback, and provider cleanup (8 checks).

Java 7 offline suite passed: [run 42211396714](https://github.com/qiangge5586-a11y/1/actions/runs/42211396714). Android build for the same source revision is still being verified. This remains compatibility-layer integration; the production iAP2 Driver and actual hardware/provider remain unvalidated.


## Generation-bound CSM session ownership (2026-11-06)

Added `compat/mfi/MfiIap2SessionOwner.java` as the lifecycle boundary for one
iAP2 MFi control-channel generation.

- The owner receives a positive, externally assigned generation token and owns
  exactly one receive coordinator, CSM bridge and worker.
- Only ordered packets delivered by that generation's iAP2 receive window may
  be passed to `acceptDeliveredPackets`.
- Packet admission and teardown are serialized. Once closed, the old owner
  rejects all further packet delivery.
- Teardown marks the generation inactive before stopping the worker; the
  worker cancels provider work and closes its old pump. Authentication callbacks
  are guarded so a callback from a retired generation is not forwarded.
- A reconnect must construct a new owner and new receive/bridge/adapter chain.
  Do not call `reset()` on an old receive coordinator to reuse it across links.
- This is a lifecycle composition primitive, not evidence that the repository
  already has a production source-parity `Iap2LinkEngine`. The caller must
  connect it to the real receive-window delivery and transport supervisor when
  that production engine exists.

Validation boundary: Java 7 source compatibility is covered by the iAP2
workflow compile step. No physical MFi coprocessor, iPhone, Bluetooth vehicle
head unit, or API-17 hardware validation is claimed.
