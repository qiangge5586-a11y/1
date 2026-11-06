# Production iAP2 Engine Recovery — Source Evidence and Integration Gate

Date: 2026-11-06
Target: DiPlay 0.3.11, Honda MY16ADA / Android 4.2.2 / API 17
Repository branch: main

## Evidence recovered in this pass

The original decompile artifact was downloaded from GitHub Actions run #6, artifact ID `13478149398`, archive `diplay-0.3.11-decompiled.zip`. The archive contains the original Smali and JADX reconstructions:

- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkEngine.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkChannel.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2CsmChannel.smali`
- `decompiled/apktool/smali_classes3/com/shilapi/xcertplay/airplay/AirPlayIapTunnelStream.smali`
- `decompiled/apktool/smali_classes3/com/shilapi/xcertplay/airplay/CarPlayMediaEngine.smali`
- `decompiled/apktool/smali_classes3/com/shilapi/xcertplay/iap2/session/Iap2Session.smali`

JADX also emitted Java reconstructions, including a 96 KB `Iap2LinkEngine.java`, but that file imports Kotlin runtime, AndroidX, media3, Guava and synthetic backport helpers. The reconstruction metadata reports Kotlin 2.2.0 and the JADX output has known decompilation errors. It is useful as a navigation aid, not as a drop-in Java 7 production source file. The Smali remains the authoritative evidence for control flow and side effects.

## Production engine behavior that must be preserved

The source audit and recovered artifact confirm these public engine operations:

- `start()`: enter DETECTING and emit startup marker `FF 55 02 00 EE 10`.
- `feed(byte[], nowMillis)`: incrementally parse startup marker and link frames.
- `feedEof()`: terminalize the engine.
- `advanceTime(nowMillis)`: run marker, synchronization, delayed ACK and retransmission deadlines.
- `initiateSynchronizationIfDetecting(nowMillis)`: one-shot fallback entry used by the non-initiating wireless worker after its silence threshold.
- `sendSession(sessionId, payload, nowMillis)`: queue or transmit session payload under negotiated windows.
- `takeOutput()`: atomically drain encoded output.
- `pollEvent()`: drain ordered Writable / Control / Session / Dead events.
- `nextDeadlineMillis()`: earliest active protocol deadline.
- `isWritable()`, `state()`, `peerSynchronization()`: readiness and negotiated peer information.

The protocol state machine includes DETECTING, NEGOTIATING, NORMAL and DEAD. Its correctness depends on the full set of source-derived rules in `IAP2_LINK_ENGINE_SOURCE_AUDIT.md`: the separate six-byte startup marker and two-byte packet marker; nine-byte header; independent additive checksums; modulo-256 sequence arithmetic; cumulative ACK; Extended ACK missing-sequence retransmission; bounded out-of-order receive; pre-ready queue validation; delayed ACK; retry counters; 1 MiB pending output; and terminal cleanup that clears timers, output and pending events before emitting the Dead event.

## Required lifecycle composition

The production composition must be a single owner of each mutable protocol object:

1. RFCOMM bootstrap connector establishes the UUID stream. The connector's secure / insecure / reflective fallback is already ported, but its timeout currently applies per attempt, not to one aggregate connection deadline.
2. The connected BluetoothSocket is wrapped by `BluetoothRfcommByteStream`. Its reader thread feeds a bounded ring; the link pump is the sole owner of engine calls.
3. The bootstrap iAP2 engine and CSM channel complete accessory identification / authentication and wireless control messages. CSM receive and send queues must preserve the original 64-frame / 1 MiB bounds and one monotonic deadline per operation.
4. Wireless control notifications and the source-confirmed AirPlay DataStream SETUP type 130 negotiate the IAP tunnel. The TCP listener, peer connect deadline, encryption/read-key derivation, stream ID and data port belong to this phase, not to the RFCOMM engine.
5. Once the encrypted tunnel stream is available, create a separate link-channel/pump/engine generation over that tunnel stream and attach the CSM control channel only after link READY and a valid peer maximum control payload have been observed.
6. On any terminal failure, close the current generation's CSM, pump, stream and carrier exactly once. Ignore callbacks whose generation token is stale. Reconnect creates a fresh generation and fresh protocol state; no old timers, queued commands, CSM frames or tunnel callbacks may leak into it.

RFCOMM bootstrap and type-130 tunnel are distinct carriers and sessions. RFCOMM connected is not tunnel ready. TCP peer connected is not iAP2 link ready. iAP2 link ready is not CSM ready.

## Current repository implementation status

Already present in `compat/iap2` and `compat/bluetooth`:

- Source-aligned Java 7 link frame codec, decoder, negotiation, ACK/retransmit/window models and composed offline engine model.
- Single-owner `Iap2LinkPump` scaffold with bounded producer queue and EOF/error cleanup.
- Offline `Iap2LinkModelPumpDriver`, which adapts the model, not the recovered production engine.
- CSM framing, receive queue/coordinator, send chunk planning and pump sender models.
- RFCOMM connector and connected-socket byte-stream adapter.
- Caller-configured sticky overall/phase deadline budget model.

Not yet present or proven:

- A production Java 7 `Iap2LinkEngine` port with source-parity event ordering and timer semantics.
- A production `Iap2LinkChannel` facade that owns the engine and worker lifecycle.
- A production `Iap2CsmChannel` with source-parity blocking/wakeup/fail-closed semantics.
- An integrated RFCOMM bootstrap session, MFi authentication/control notification lifecycle and type-130 TCP tunnel controller in the compatibility app.
- A production tunnel-side link/CSM generation and reconnect supervisor.
- Live Android 4.2.2 runtime, iPhone pairing, vehicle Wi-Fi, tunnel or CSM verification.

The existing model driver must not be wired to live RFCOMM or tunnel traffic as a substitute for the production engine. That would turn a test model into an unverified protocol implementation.

## Implementation gates and acceptance tests

### Gate A — Production engine port
Port the recovered engine into a dependency-free Java 7 package. Keep source-derived state names and operation ordering. Add differential golden tests against the Smali/JADX evidence for:
- marker/SYN/ACK ordering, both initiation directions and fallback;
- every ACK / Extended ACK / data control-bit combination;
- sequence wrap, cumulative ACK boundaries and peer window limits;
- out-of-order duplicate/gap/reorder behavior;
- delayed ACK and retransmission deadline rearming;
- retry exhaustion, invalid peer limits, output budget overflow and terminal cleanup;
- event ordering and no event/output after DEAD.

### Gate B — Link channel / pump
Bind exactly one engine instance to one pump worker. All engine mutation, timer advancement, input feed, command drain, output drain and event dispatch must occur on that worker. Producers only use bounded FIFO APIs. Test EOF, I/O exception, close during blocked read, command saturation, timeout, listener exception and repeated close.

### Gate C — CSM
Implement original receive/send locking and wake-up behavior around the link control payload queue. Enforce 64 frames / 1 MiB receive queue, 64 commands / 1 MiB command queue, 65,525-byte link chunk maximum, peer negotiated CSM payload maximum and shared absolute monotonic deadlines. Malformed framing or overflow must fail closed.

### Gate D — wireless tunnel lifecycle
Implement bootstrap and tunnel as separately observable phases. Verify the source-derived 0x4300, 0x4301, 0x4E0D, 0x4E0E, 0x5702 and 0x5703 control flow, type-130 SETUP, IAP data-stream UUID, seed/key derivation, listener bind, peer arrival, encrypted stream delivery and tunnel-side iAP2 session. Use a monotonically increasing generation ID to reject stale callbacks.

### Gate E — fault injection and API 17
Run deterministic disconnect/timeout/reconnect tests at each phase boundary. Compile with Java 7 and minSdk 17. Then install on the actual MY16ADA head unit and collect logs for API 17 BluetoothSocket behavior, discovery cancellation, stream close unblocking, Wi-Fi route, TCP listener bind/peer arrival and sustained CSM exchange. CI compilation is not a substitute for this runtime test.

## Safety and claims

No live vehicle, iPhone, Bluetooth, Wi-Fi hotspot write, CAN or ROOT operation was performed in this pass. The ROOT hotspot module remains read-only in the app. No undocumented CAN frames or vehicle safety signal changes are allowed. Report offline tests, APK build, and physical-device validation as separate evidence classes.
