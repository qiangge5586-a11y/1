# iAP2 Link Engine Evidence Matrix

Date: 2026-11-06
Target: DiPlay 0.3.11 original APK, package `com.shihab.diplay`
Purpose: turn the existing link-layer audit into a reproducible source-to-test plan before implementing any link protocol behavior.

## Evidence availability

- The repository contains the APK and the decompile workflow definition.
- The decompile workflow is configured to upload `diplay-0.3.11-decompiled` for 30 days.
- The original Smali is not committed into the main source tree; it is available in the Actions artifact.
- Latest decompile run #7: `42166504575`, completed successfully; artifact `diplay-0.3.11-decompiled`, ID `13478968581`, expires `2026-12-06T05:08:19Z`.
- The immediately preceding successful artifact, run `42166126298`, artifact ID `13478149398`, was downloaded and inspected; ZIP SHA-256: `ff6a124c3e10458361a5f408dff37d97420dbbf05aabd29d1482e31746a7b27a`.
- The artifact contains original APKTool Smali for `Iap2LinkEngine`, `Iap2LinkChannel`, `Iap2LinkEngine$Companion`, `Header`, `Packet`, `ReceiveBuffer`, `SessionDescriptor`, and `SynchronizationPayload` under `smali_classes4`.
- A source-grounded summary and golden wireless synchronization vector are now preserved in `IAP2_LINK_ENGINE_SOURCE_FINDINGS.md`; the raw artifact remains temporary.
- Correction: the earlier statement that no decompile run was found was incomplete because the initial inspection covered only the newest page of workflow runs.

The artifact is temporary (expires `2026-12-06T01:15:20Z`). Preserve source excerpts or a reproducible source-audit note in the repository; do not rely on the temporary artifact as the only long-term evidence.

## Source extraction checklist

| Area | Required source evidence | Required extraction | Status |
|---|---|---|---|
| Engine construction | `Iap2LinkChannel.runPump()` | constructor args, initial state, initiate flag | Prior Smali audit; raw excerpt not in repo |
| Synchronization | `Iap2LinkEngine` and `SynchronizationPayload$Companion` Smali | marker, 9-byte frame header, additive checksums, payload schema, wireless default golden vector | Source-audited; offline golden-vector test added |
| Sequence numbers | `Iap2LinkEngine` and `Companion` Smali | initial TX=99, modulo-256 increment/distance, ACK field | Source-audited; codec tests cover wrap |
| Normal ACK | `handleAcknowledgement`, `sendAcknowledgement`, `writePacket` | piggyback cumulative ACK and outgoing window drain | Source-audited; full state tests remain |
| Extended ACK | `handleExtendedAcknowledgement`, `sendExtendedAcknowledgement` | payload is explicit missing-sequence byte list | Source-audited; retransmit state tests remain |
| Reordering | `handleData` and `ReceiveBuffer` | circular window, duplicate suppression, bounded out-of-order list, ordered delivery | Source-audited; full state tests remain |
| Retransmission | `sendPacket`, `retransmitDuePacket`, `advanceTime` | earliest deadline, exact retry-count terminal boundary | Source-audited; full timer tests remain |
| Peer limits | `peerLimitsAreUsable`, `peerPayloadIsAcceptable`, ACK path | maxLength > 10 and payload <= maxLength - 10 | Source-audited; negotiation tests remain |
| Ready gate | `handleSynchronization`, `handleAcknowledgement`, `setWritable`, `drainEvents` | peer sync + usable peer limit + queued payload fit + peer ACK | Source-audited; full state tests remain |
| EOF / close | `runPump`, `feedEof`, `die` | EOF is terminal; worker forwards EOF to engine | Source-audited; lifecycle tests remain |

## Required fixture set before implementing a link engine

1. Accessory-initiated synchronization request and response, captured as exact byte arrays.
2. Phone-initiated synchronization request and response, if both directions are supported by the original engine.
3. One normal data packet and its ACK in both directions.
4. One extended ACK packet with at least two outstanding packets.
5. Out-of-order arrival followed by in-order completion.
6. Duplicate packet and stale ACK handling.
7. Retransmission timeout, retry, ACK cancellation, and retry exhaustion.
8. Peer maximum control payload negotiation at minimum, ordinary, and maximum accepted values.
9. EOF before link-ready and EOF after link-ready.
10. Invalid checksum, invalid length, unknown control bit, and invalid session ID.

Each fixture must record: APK SHA-256, class/method origin, direction, exact input bytes, exact expected output bytes/events, and timing assumptions. A fixture copied from a log or generated from a guessed protocol is not source-grounded.

## Safe implementation sequence

- Phase A — restore source evidence: obtain the workflow artifact or rerun `.github/workflows/diplay-decompile.yml`; retain the relevant Smali and decompile metadata in a reviewable artifact.
- Phase B — transcribe behavior: map each state mutation and wire field to a Smali method/offset; resolve any conflict between JADX and Smali in favor of Smali.
- Phase C — build golden fixtures: derive deterministic vectors from source paths and, where possible, compare with the original implementation in an isolated harness.
- Phase D — implement the offline engine model: keep it separate from Android, Bluetooth, TCP, USB, crypto, and ROOT operations.
- Phase E — test transitions and fuzz malformed input; only then consider integration behind a disabled-by-default feature gate.
- Phase F — validate on hardware only after explicit user authorization and a safe rollback plan.

## Guardrails

- Do not invent or hardcode a synchronization marker, checksum algorithm, ACK interpretation, or sequence-number rule.
- Do not treat the AirPlay DataStream type 130, encrypted IAP carrier frame, iAP2 link packet, and CSM frame as interchangeable.
- Do not weaken checksum, authentication, encryption, retry, or bounds checks to make a test pass.
- Offline test success is not evidence of an iPhone handshake or MY16ADA vehicle compatibility.
- No live Bluetooth pairing, TCP tunnel, USB claim, ROOT command, or hotspot configuration write is performed by this matrix.

## Next acceptance criteria

The offline link wire-format codec may now be extended using the preserved source findings. A full state-machine port still requires:
- source-grounded fixtures for peer sync/ACK, extended ACK and retransmission cases;
- positive and negative transition tests for ready gate, sequence windows, out-of-order delivery, timeout and EOF;
- Java 7 compatibility and passing CI;
- explicit hardware-test authorization before any live vehicle session.


## First restored source findings (2026-11-06)

Source: `Iap2LinkEngine.smali` methods `writePacket`, `parseAvailable`, `processFrame`; `Iap2LinkEngine$Companion.smali` methods `checksum`, `checksumValid`, `parseHeader`, `readU16`, `sequenceDistance`, `nextSequence`; `Iap2LinkEngine$SynchronizationPayload.smali` method `encode`.

- Detection preamble is six bytes `FF 55 02 00 EE 10`; after it is consumed, the engine enters NEGOTIATING and emits synchronization.
- Normal link frame marker is `FF 5A`, not the detection preamble.
- Normal header is 9 bytes: marker[0..1], total frame length u16 BE[2..3], control[4], sequence[5], acknowledgement[6], session ID[7], additive checksum[8]. Header checksum makes the unsigned byte sum of all 9 header bytes equal 0 modulo 256.
- Payload frames append a separate additive checksum byte; payload plus checksum sums to 0 modulo 256. A null payload is a 9-byte control frame; a non-null payload frame length is payload length + 10.
- Sequence numbers are u8; next sequence wraps modulo 256; distance is `(sequence - previous) & 0xFF`.
- Synchronization payload begins with version u8, maxOutgoing u8, then maxLength/retransmissionTimeout/acknowledgementTimeout as u16 BE, maxRetransmissions u8, maxAcknowledgements u8, followed by 3-byte session descriptors (id, kind, version).
- The offline codec and two golden vectors are now in `Iap2LinkFrameCodec.java` and `test/Iap2LinkFrameCodecTest.java`. They cover the source-derived 9-byte ACK-only frame and a data frame with payload checksum. These tests validate framing primitives only, not the complete link state machine.


## Restored source evidence (artifact retrieved 2026-11-06)

The decompile artifact is now available and was downloaded for inspection:

- Workflow run: [42166126298](https://github.com/qiangge5586-a11y/1/actions/runs/42166126298)
- Artifact: \`diplay-0.3.11-decompiled\`, artifact ID \`13478149398\`
- Artifact SHA-256: \`ff6a124c3e10458361a5f408dff37d97420dbbf05aabd29d1482e31746a7b27a\`
- Original APK SHA-256 verified by workflow: \`22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1\`
- APKTool source: \`decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/\`
- JADX source: \`decompiled/jadx/sources/com/shilapi/xcertplay/transport/Iap2LinkEngine.java\`

### Exact wire format confirmed against source

- Initial detection marker: \`FF 55 02 00 EE 10\`.
- Link frame marker: \`FF 5A\`.
- Header is 9 bytes: bytes 0..1 marker \`FF 5A\`; bytes 2..3 total frame length (u16 BE); byte 4 control flags; byte 5 sequence; byte 6 acknowledgement; byte 7 session ID; byte 8 additive two's-complement checksum over bytes 0..7 (sum including checksum is 0 modulo 256).
- Payload frames append payload then one additive two's-complement checksum byte; total length is payload length + 10. Header-only control frames are 9 bytes.
- Sequence is unsigned 8-bit; next sequence is \`(sequence + 1) & 0xff\`; distance is \`(sequence - previous) & 0xff\`. Initial send sequence is 99.
- Synchronization payload: version 1; maxOutgoing (u8); maxLength (u16 BE); retransmission timeout (u16 BE); ACK timeout (u16 BE); max retransmissions (u8); max acknowledgements (u8); then zero or more 3-byte session descriptors (id, kind, version).
- Control flags: SYN 0x80, ACK 0x40, extended ACK 0x20, RESET 0x10.
- Local session descriptors: control (10,0,configured control version), extended ACK (11,2,1), file transfer (12,1,2).
- Default session IDs: control 10, extended ACK 11, file transfer 12.

### State and retransmission behavior confirmed

- States: IDLE, DETECTING, NEGOTIATING, NORMAL, DEAD.
- \`start()\` emits the detection marker; marker retry deadline is 1,000 ms. A wired initiator enters negotiation immediately; wireless/non-initiating mode waits for the peer marker.
- The wireless worker can call \`initiateSynchronizationIfDetecting()\` after 800 ms of silence; the worker has a separate 12,000 ms silent-peer timeout. These are worker-level timeouts, not the engine's SYN timer.
- SYN resend interval: 500 ms; after more than 20 attempts, engine enters DEAD with an unanswered-SYN diagnostic.
- Default retransmission timeout: 4,000 ms; default max retransmissions: 4; default max ACKs: 3; ACK slack: 10.
- Receive data uses sequence distance and a bounded out-of-order list. Duplicate sequence entries are ignored; too-far-ahead data triggers an ACK or extended ACK listing missing sequence numbers. In-order data is drained from the sorted pending list and dispatched by session ID.
- Extended ACK payload is a byte list of missing sequence numbers. The sender retransmits outstanding packets whose sequence appears in that list.
- Normal ACK updates last acknowledged sequence, removes acknowledged outstanding packets, rearms the earliest pending retransmission deadline, and releases queued packets while the peer outgoing window allows.
- Retransmission chooses the outstanding packet with the earliest deadline, increments its retry count, and either resends or enters DEAD when the configured retry limit is reached.
- Peer maxLength must exceed 10; payload acceptance is bounded by peer maxLength minus 10.
- Wireless link configuration: maxOutgoing=4, acknowledgementTimeoutMillis=500 (default from LwipNcmBridge.WRITE_TIMEOUT_MILLIS), zeroAcknowledgements=false, control-session version 2, queue/out-of-order capacities 64, pending output 1 MiB, pending events 256. Wired uses zeroAcknowledgements=true. The constructor values 494 (wireless) and 486 (wired) are Kotlin default-argument bit masks, not link window or maxOutgoing values.

### Smali method provenance

Line numbers below refer to APKTool output paths inside the retrieved artifact:

- \`Iap2LinkEngine.smali\`: enterNegotiating L632; handleAcknowledgement L677; handleData L963; handleExtendedAcknowledgement L1444; handleSynchronization L1658; localSynchronization L1698; parseAvailable L1851; peerPayloadIsAcceptable L2194; processFrame L2230; retransmitDuePacket L2432; sendAcknowledgement L2873; sendExtendedAcknowledgement L2906; sendPacket L3025; sendSynchronization L3205; writePacket L3257; advanceTime L3414; feed L3785; initiateSynchronizationIfDetecting L3925; start L4291.
- \`Iap2LinkEngine$Companion.smali\`: checksum L217; checksum validation L280; sequence increment L359; header parse L371; sequence distance L502.
- \`Iap2LinkEngine$SynchronizationPayload$Companion.smali\`: sync payload decode L61.
- \`Iap2LinkChannel.smali\`: runPump L1837; wireless fallback and silence handling are inside this method.
- \`Iap2LinkChannel.java\` (JADX): wired/wireless config declarations at lines 61-62.

## Offline codec implementation

\`DiPlay/compat/iap2/Iap2LinkFrameCodec.java\` now implements only source-grounded wire primitives: frame encoding, 9-byte header parsing, additive checksums, synchronization payload encode/decode, and 8-bit sequence arithmetic. It deliberately does not implement the engine state machine, queue/window logic, Bluetooth, or socket I/O.

Golden-vector tests were added in \`Iap2LinkFrameCodecTest.java\` for sync payload layout, ACK-only frame, data frame, header/payload checksums, marker, bounds, and sequence wrap. These are deterministic offline tests, not a live iPhone/vehicle handshake.


## Implemented offline milestones (2026-11-06)

- `DiPlay/compat/iap2/Iap2LinkFrameDecoder.java`: bounded incremental parser mirroring original header/payload checksum handling, bytewise FF 5A resynchronization, partial-frame retention, and 8,192-byte input chunks.
- `DiPlay/compat/iap2/Iap2LinkNegotiationModel.java`: partial deterministic model of marker detection, SYN generation/retry, peer sync, source-accurate ACK readiness gate, invalid maxLength, and EOF. It does not yet model cumulative ACK windows, extended ACK retransmit selection, out-of-order delivery, or normal data retransmission.
- `DiPlay/compat/iap2/Iap2RetransmissionPolicy.java`: isolated source-derived retry-count and extended-ACK missing-sequence selection policy; it is not yet wired into a full packet queue/timer engine.
- `DiPlay/compat/iap2/Iap2ReceiveWindowModel.java`: source-derived receive-side ordering, out-of-order queue, duplicate/stale handling, missing-sequence extended ACK, and delayed/forced ACK policy.
- `DiPlay/compat/iap2/Iap2AckWindowPolicy.java`: source-derived modulo-256 cumulative ACK removal predicate, strict new-send queue boundary, and strict queued-drain boundary.
- `DiPlay/compat/iap2/Iap2OutboundWindowModel.java`: deterministic offline outbound state model composing send sequencing, queueing, cumulative ACK release, unacknowledged packet retention, timeout retransmission and Extended ACK retransmission. Its harness queue limit is defensive and not asserted as original APK behavior.
- Latest verified CI run [#91](https://github.com/qiangge5586-a11y/1/actions/runs/42169773435) passed 7,129 offline checks: 61 handshake lifecycle + 3,380 IAP framing/package + 2,330 CSM + 64 link codec + 1,050 link decoder + 108 negotiation model + 20 retransmission policy + 42 receive-window model + 17 ACK-window policy + 57 outbound-window model.
- The wireless synchronization golden vector advertises the source-confirmed 500 ms ACK timeout (`01 F4`) and its payload checksum is `29`; the previous `BA` documentation byte was corrected.
- No real iPhone, Bluetooth, USB, AirPlay tunnel, or Honda head-unit session was attempted.


## Readiness behavior correction (run #72)

The original `Iap2LinkEngine.processFrame()` handles control bits independently: RESET `0x10` terminates; SYN `0x80` decodes peer limits and emits a local ACK; cumulative ACK/readiness runs only when bit `0x40` is set; Extended ACK runs when bit `0x20` is set and payload is present; data is delivered when `(control & ~0x40) == 0` and payload is present. Therefore SYN-only remains NEGOTIATING; a later ACK-bearing frame opens READY. The same ACK-bearing frame continues through Extended ACK/data handling, so the offline model retains it for post-readiness dispatch while marking its cumulative ACK as already processed. Data-only `0x00` and ACK+data `0x40` are both valid data predicates. The ACK field need not equal 99. Source method: `Iap2LinkEngine.processFrame()`; verified against the recovered Smali artifact from run #7.


## Outbound ACK-window comparison milestone (CI run #83)

`Iap2AckWindowPolicy.java` now captures the original source's distinct strict comparisons: retain an unacknowledged packet only when its modulo-256 distance from the cumulative ACK is greater than zero and at most `peerMaxAcknowledgements + 10`; queue a new packet only when current distance is strictly greater than `peerMaxOutgoing`; drain queued packets only while distance is strictly less than `peerMaxOutgoing`. These predicates are covered by 17 checks. The actual unacknowledged list mutation, queue scheduling, and retransmission deadline engine are still open. See `IAP2_ACK_WINDOW_SOURCE_VERIFICATION.md`.


## Outbound state model milestone (CI run #90)

`Iap2OutboundWindowModel.java` composes source-audited send/queue boundaries, cumulative ACK acceptance/removal, queue drain, per-packet retransmission deadlines, retry exhaustion and Extended ACK sequence selection. `Iap2OutboundWindowModelTest` passed 57 checks, including two outstanding packets with staggered deadlines and correct earliest-deadline selection. The defensive queue-capacity bound belongs to this offline harness, not a claimed original source constant. Remaining validation includes comparing multiple simultaneous due-packet deadline re-arming against original Smali, plus concurrency and transport integration. See `IAP2_OUTBOUND_WINDOW_MODEL.md`.

## Composition model checkpoint (2026-11-06)

Added `compat/iap2/Iap2LinkEngineModel.java` as an offline lifecycle/readiness composition over the existing negotiation, outbound-window, and receive-window models. It rejects data-window calls before READY, delegates ACK and Extended ACK work, advances inbound delayed-ACK and outbound retransmission timers against one supplied clock, and closes on EOF.

- CI run [#95](https://github.com/qiangge5586-a11y/1/actions/runs/42170649686): **7,148 offline checks passed** in run [#98](https://github.com/qiangge5586-a11y/1/actions/runs/42170739023); composition suite: 19 checks. A follow-up lifecycle correction makes outbound retry exhaustion and inbound receive-window terminal failures report CLOSED at the composition boundary.
- The model deliberately does not parse live data packets or serialize outbound data frames.
- Peer maxOutgoing/maxAcknowledgements/timeouts/retry settings remain explicit constructor inputs. The negotiation model does not yet expose the complete peer SYN configuration; automatic configuration handoff is a remaining task.
- Run #94 failed on an initial test-fixture null reference; the fixture was fixed before run #95. Do not treat #94 as a passing run.
- No iPhone/vehicle link, Android runtime, MFi, AirPlay encrypted tunnel, USB, Bluetooth or ROOT write test was performed.

Next: extend negotiation to expose a validated immutable peer-configuration snapshot, then bind that snapshot into the composition model with tests for minimum/maximum peer limits. Keep the data plane abstract until source-grounded packet dispatch and exact bidirectional fixtures are complete.

## Automatic peer-configuration handoff (2026-11-06)

The negotiation model now retains a read-only peer SYN snapshot: maxOutgoing, maxLength, retransmission timeout, ACK timeout, max retransmissions, max acknowledgements, synchronization sequence, and complete session descriptors. The composition's automatic constructor uses this snapshot to instantiate both windows after READY.

- Outbound values: peer maxOutgoing, maxAcknowledgements, retransmission timeout and max retransmissions; initial TX/ACK sequence 99; ACK slack 10; local defensive queue cap 64.
- Inbound values: initial receive/ACK sequence from peer SYN sequence; peer maxOutgoing, maxAcknowledgements and ACK timeout; wireless local maxOutgoingDelta 0; out-of-order cap 64.
- Original source readiness behavior is now represented: only control bit `0x40` invokes cumulative ACK/readiness handling. A SYN-only `0x80` frame records peer limits and emits the local ACK but remains NEGOTIATING; a later ACK-bearing frame opens READY. The ACK number need not equal 99.
- Explicit-injection construction remains for unit tests; automatic construction currently targets the audited wireless configuration only.

Actions run [#105](https://github.com/qiangge5586-a11y/1/actions/runs/42172333952) passed **7,174 offline checks**, including 121 negotiation checks and 32 composition checks. Runs #102 and #104 failed on a boxed numeric assertion and stale readiness expectations respectively; both were corrected before #105.

Remaining: validate the exact source-side peer-limit and queued-payload readiness predicates against additional Smali excerpts, then add malformed/truncated SYN and peer boundary tests. This model still has no live packet writer, USB/Bluetooth/TCP transport, or MY16ADA hardware verification.

## SYN boundary and malformed-input regression checkpoint

Added tests for malformed version/fixed-prefix payloads, peer maxLength minimum 11 and maximum 65,535, u8 window/retry boundaries (0 and 255), u16 timeout boundaries (0 and 65,535), and the source decoder's treatment of trailing incomplete session-descriptor bytes (ignored after complete 3-byte descriptors). Actions run [#107](https://github.com/qiangge5586-a11y/1/actions/runs/42172506528) passed **7,199 offline checks** (146 negotiation, 32 composition). Run #106 exposed only a null-assertion helper defect in the new test and failed; the test was corrected before #107.

## Unified post-SYN frame dispatch checkpoint

The negotiation decoder queues the ACK-bearing frame that completes readiness, marking its cumulative ACK as already processed to prevent duplicate ACK handling. Subsequent frames are queued normally. Dispatch follows the original bit predicates: ACK bit `0x40` -> cumulative ACK; Extended ACK bit `0x20` with payload -> targeted retransmission; `(control & ~0x40) == 0` with payload -> receive-window delivery. This includes data-only `0x00` and ACK+data `0x40`. Header-only ACKs are not delivered as data. Dispatch results are bounded/drainable (256 events); encoded responses are model outputs, not transport writes.

Actions run [#137](https://github.com/qiangge5586-a11y/1/actions/runs/42173808122) passed **7,266 offline checks**, including 93 composition checks. The new composition test covers ACK accumulation to the peer maxAcknowledgements threshold, forced receive-watermark advancement, and encoded ACK-only output. The source batching rule increments for every ACK-bit frame and emits an ACK when the count reaches the peer-advertised maximum.

## Pending-packet readiness payload validation

Smali `handleAcknowledgement()` calls `peerLimitsAreUsable()` and scans the pre-ready queued packet list with `peerPayloadIsAcceptable()` before changing NEGOTIATING to NORMAL. The exact bound is `payload.length <= peer.maxLength - 10`; any queued payload outside the bound terminates the link. The composition now supports `queueBeforeReady()`, validates all pending packets before flushing, then delegates accepted packets to the outbound window. The check is not repeated for post-ready `sendPacket()` calls in the original source, so the offline `send()` path does not add that guard.

Actions run [#147](https://github.com/qiangge5586-a11y/1/actions/runs/42174129287) passed **7,297 offline checks** (158 negotiation, 118 composition). Boundary test: peer maxLength 11 accepts one byte and rejects two bytes in the pre-ready queue.

## Queue ownership and ACK-batch reset checkpoint

The offline composition now verifies pre-ready queue defensive copying and its 64-packet bound; validates every queued payload against peer maxLength minus 10 before flush; and preserves the source distinction that the size predicate is not repeated by post-ready `sendPacket()`. ACK-batch counting follows the Smali `processFrame()`/ `writePacket()` behavior: ACK-bit frames accumulate to peer maxAcknowledgements, while every emitted link frame resets the counter; threshold emits an ACK-only frame and advances the receive watermark.

Actions run [#154](https://github.com/qiangge5586-a11y/1/actions/runs/42174396466) passed **7,379 offline checks** (158 negotiation, 200 composition).


## Pre-ready queue and ACK-batch integration

- The offline composition now has a bounded pre-ready packet queue and flushes it only after the ACK-bearing readiness transition.
- Original `peerPayloadIsAcceptable()` is applied to queued packets before any flush: payload length <= peer maxLength - 10.
- The check is not repeated for post-ready NORMAL sends, matching the source path.
- `processFrame()` ACK batching is modeled with the peer maxAcknowledgements count; emitted frames reset the counter as original `writePacket()` does.
- The ACK counter is incremented before readiness queue flushing, so a queued write can reset it before the final per-frame batch threshold check.

Actions run [#154](https://github.com/qiangge5586-a11y/1/actions/runs/42174396466) passed **7,379 offline checks** (200 composition checks). The queue cap of 64 is explicitly defensive, not source-derived. The model remains disconnected from live transport and hardware.

## Readiness ACK mutation order

The readiness-triggering ACK frame is now modeled in source order: pre-ready payload validation precedes ACK-window mutation; the ACK field is applied before queued packets are flushed; then the same frame continues through Extended ACK/data handling and the end-of-frame ACK-batch threshold. Actions run [#156](https://github.com/qiangge5586-a11y/1/actions/runs/42174566575) passed **7,379 offline checks**.

## Extended ACK response golden fields

Verified `sendExtendedAcknowledgement()` writes control `0x20`, current send sequence, current in-order receive ACK, session ID 0, and raw missing-sequence bytes. The session descriptor ID 11 is not used as the Extended ACK frame's session field. A new integration test decodes the response generated by an inbound gap. Actions run [#159](https://github.com/qiangge5586-a11y/1/actions/runs/42174678655) passed **7,384 offline checks** (205 composition).


## Retransmission deadline preservation tests

The outbound model regression suite now verifies that cumulative ACK removal leaves the original deadlines of later unacknowledged packets armed, and that an Extended ACK missing-sequence request increments and re-arms only the requested packet from the request timestamp. Advancing the deterministic clock then retransmits the earliest remaining packet while retaining the next packet's deadline. CI run [#160](https://github.com/qiangge5586-a11y/1/actions/runs/42174934442) passed 7,393 offline checks. These are deterministic Java model checks, not a live transport test.


## CSM input boundary source correction

The recovered source call path is `Iap2CsmChannel.recvControl()` -> `Iap2LinkChannel.recvControl()` -> `Iap2CsmFramer.offer()`. The 8,192-byte bound applies to raw reads in `Iap2LinkChannel.runPump()`, not to the CSM framer's input method. The original outbound `splitForLink()` caps link chunks at 65,525 bytes; the offline decoder now accepts up to this size per call and rejects 65,526. CI run [#161](https://github.com/qiangge5586-a11y/1/actions/runs/42175067632) passed 7,393 offline checks.

## Encoded link-frame response regression checkpoint

The composition emits encoded link-frame bytes to a bounded drain-only queue: data sends/retransmits, newly released packets after cumulative ACK, immediate normal ACK, and Extended ACK. The queue is capped at 256; no transport write is implemented. Actions run [#207](https://github.com/qiangge5586-a11y/1/actions/runs/42181647104) passed **2,323,738 offline checks** (238 Link Engine checks). Run #206 failed on an incorrect readiness expectation in the test; the peer SYN-only expectation was reverted to the ACK-bit readiness behavior represented by the current source-audited model.


## Follow-up evidence — 2026-11-06 / CI #210

| Behavior | Evidence | Result | Scope |
|---|---|---|---|
| Outbound retransmission wire serialization | `retransmissionWireFramePreservesOriginalPacket()` | PASS | Offline model + frame decoder round-trip; sequence/session/payload preserved |
| Extended ACK response serialization | `inboundGapEmitsExtendedAckWithSessionZero()` | PASS | Offline model + frame decoder round-trip at negotiated gap threshold |
| Full iAP2 offline regression suite | GitHub Actions Run #210 | PASS | 16 Java 7-targeted suites; not a physical iPhone or MY16ADA handshake |

Run: https://github.com/qiangge5586-a11y/1/actions/runs/42183299341
Commit: `db67b97002bf5ad9797ead2397b08d80418d1b66`
