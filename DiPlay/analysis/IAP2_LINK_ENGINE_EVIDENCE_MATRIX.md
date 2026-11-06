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
