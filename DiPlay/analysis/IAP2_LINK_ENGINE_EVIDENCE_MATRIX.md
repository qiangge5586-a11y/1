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
- `DiPlay/compat/iap2/Iap2LinkNegotiationModel.java`: partial deterministic model of marker detection, SYN generation/retry, peer sync, ACK readiness gate, invalid maxLength, and EOF. It does not yet model cumulative ACK windows, extended ACK retransmit selection, out-of-order delivery, or normal data retransmission.
- Latest CI run [#58](https://github.com/qiangge5586-a11y/1/actions/runs/42168368167) passed 6,980 offline checks: 61 handshake lifecycle + 3,380 IAP framing/package + 2,330 CSM + 59 link codec + 1,050 link decoder + 100 negotiation model.
- The wireless synchronization golden vector was corrected to advertise the source-confirmed 500 ms ACK timeout (`01 F4`); CI run #58 passed after the correction.
- No real iPhone, Bluetooth, USB, AirPlay tunnel, or Honda head-unit session was attempted.
