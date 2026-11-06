# iAP2 Link Engine Evidence Matrix

Date: 2026-11-06
Target: DiPlay 0.3.11 original APK, package `com.shihab.diplay`
Purpose: turn the existing link-layer audit into a reproducible source-to-test plan before implementing any link protocol behavior.

## Evidence availability

- The repository contains the APK and the decompile workflow definition.
- The decompile workflow is configured to upload `diplay-0.3.11-decompiled` for 30 days.
- The current `main` tree does not contain APKTool Smali output or JADX source for `Iap2LinkEngine` / `Iap2LinkChannel`.
- The latest 100 repository workflow runs inspected on 2026-11-06 did not include a run identifiable as the decompile workflow. No artifact ID was available to retrieve.
- Therefore the detailed values in `IAP2_LINK_AND_CSM_FRAMING_AUDIT.md` remain prior audit findings, but this checkout does not currently carry the raw Smali lines needed for independent line-by-line reproduction.

Do not silently promote an audit note into a golden wire fixture. Before protocol implementation, retrieve the decompile artifact or rerun the decompile workflow and preserve the relevant Smali excerpts (with APK SHA-256 and class/method provenance).

## Source extraction checklist

| Area | Required source evidence | Required extraction | Status |
|---|---|---|---|
| Engine construction | `Iap2LinkChannel.runPump()` | constructor args, initial state, initiate flag | Prior Smali audit; raw excerpt not in repo |
| Synchronization | `Iap2LinkEngine` sync methods | exact marker/header bytes, version, descriptor layout, checksum, endian rules | Prior audit notes; golden bytes absent |
| Sequence numbers | packet encode/decode and engine state updates | initial TX/RX sequence, wrap behavior, increment conditions | Not independently reproducible from current tree |
| Normal ACK | ACK encode/decode and receive path | ACK number semantics, cumulative/selective behavior, validation | Not independently reproducible from current tree |
| Extended ACK | extended-ACK encode/decode | session ID, payload layout, bitmap/range semantics | Not independently reproducible from current tree |
| Reordering | receive window / pending packet map | duplicate handling, out-of-window behavior, delivery order | Not independently reproducible from current tree |
| Retransmission | deadline scheduler / retry path | timer start/reset, retry count, terminal transition | Prior audit notes; exact transition traces absent |
| Peer limits | synchronization negotiation | advertised payload parsing, bounds, update timing | Prior audit notes; exact wire fixture absent |
| Ready gate | engine state/event emission and CSM send | exact condition for link writable and peer max payload valid | Prior audit notes; raw excerpt absent |
| EOF / close | `runPump()`, engine EOF and close paths | EOF before/after writable, queue drain, close idempotence | Prior Smali audit; raw excerpt not in repo |

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

The link-layer implementation may begin only when:
- raw source evidence for synchronization, sequence/ACK, extended ACK, reordering, retransmission, peer limits, ready gate, and EOF is available;
- every implemented wire field has at least one source-grounded fixture;
- the test suite checks both expected positive transitions and malformed/timeout terminal cases;
- CI compiles with the project's Java 7 compatibility target.
