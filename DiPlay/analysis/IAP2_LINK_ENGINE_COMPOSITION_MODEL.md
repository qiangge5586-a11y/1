# iAP2 Link Engine Composition Model

## Purpose and boundary

`Iap2LinkEngineModel` is an offline composition harness that joins the audited negotiation, outbound-window, receive-window, and link-frame codec models behind one readiness gate. It parses post-SYN frames, applies source-derived ACK / Extended ACK / data control-bit dispatch, and queues encoded link frames as deterministic outputs. It is not the original `Iap2LinkEngine` and never writes to a live Bluetooth/USB/TCP stream.

The automatic constructor consumes the immutable peer SYN configuration snapshot and creates the wireless outbound and inbound window models. A second constructor supports focused test injection.

## Lifecycle

- `IDLE`: not started.
- `NEGOTIATING`: startup marker / synchronization negotiation is in progress.
- `READY`: the negotiation model accepted the peer synchronization and subsequent ACK gate.
- `CLOSED`: EOF or negotiation failure closed the composition.

Data-window methods reject calls before `READY`. Once ready, outbound send/ACK/Extended-ACK and inbound receive operations are delegated to their existing deterministic models. `advanceTimers(now)` advances both independent timer models against the same supplied monotonic clock.

## Important limits

- Peer SYN fields are decoded by the negotiation model and exposed through an immutable snapshot.
- Post-SYN frame dispatch follows the recovered `processFrame()` masks: ACK `0x40`, Extended ACK `0x20`, data when `(control & ~0x40) == 0` with payload.
- Outbound and inbound windows remain deterministic offline models; encoded output is returned by `drainWireFrames()` and is not sent to a socket.
- Wireless local constants and defensive queue caps are not generalized to wired mode.
- No hardware, iPhone, AirPlay, MFi authentication, Android 4.2.2, or ROOT operation is exercised.

## Verification

The CI test covers readiness gating, ACK-driven outbound queue release, receive ordering / Extended ACK reporting, shared-clock timer progression, and EOF closure before and after readiness. The existing model suites remain separately authoritative for their detailed source-derived edge cases.

## Peer SYN parameter handoff (2026-11-06)

The negotiation model now exposes an immutable `PeerConfiguration` snapshot after decoding a valid peer SYN. It preserves maxOutgoing, maxLength, retransmission timeout, acknowledgement timeout, maxRetransmissions, maxAcknowledgements, the peer SYN sequence, and each complete 3-byte session descriptor. Descriptor access returns a copy so callers cannot mutate the negotiated snapshot.

The new `Iap2LinkEngineModel(negotiation)` constructor automatically creates its outbound and inbound window models when the link becomes READY:

- Outbound: initial send sequence and cumulative-ACK baseline 99; peer maxOutgoing/maxAcknowledgements/retransmission timeout/max retransmissions from SYN; source ACK slack 10; local defensive queue cap 64.
- Inbound: receive and acknowledged baseline from the peer SYN sequence; peer maxOutgoing/maxAcknowledgements/ACK timeout from SYN; wireless local maxOutgoingDelta 0; out-of-order capacity 64.

The explicit-injection constructor remains available for focused unit tests. The automatic constructor uses the source-audited wireless local constants; it is not yet a general wired-link configuration factory.

## Readiness transition correction

The original `processFrame()` handles SYN (`0x80`) and cumulative ACK (`0x40`) independently. A SYN-only frame records peer limits and emits the local ACK but does not make the link READY. The readiness gate runs only when the ACK bit is present. When an ACK-bearing frame completes readiness, the same frame continues through Extended ACK and data handling in the original method; the offline negotiation model therefore preserves that frame for composition dispatch.

## Verification

Actions run [#105](https://github.com/qiangge5586-a11y/1/actions/runs/42172333952) passed **7,174 offline checks**. The new peer-parameter composition test verifies the negotiated 1500 ms retransmission timeout, 250 ms delayed-ACK timeout, sequence baseline, and peer window values are actually used by both window models. The 32-check composition suite passed.

Earlier run #102 failed because Java boxed an `int` and `long` into unequal wrapper types despite equal printed values; run #104 exposed stale test expectations after correcting readiness to match Smali. Both were fixed before #105. No real transport or vehicle test was performed.

## Peer SYN malformed-input and boundary checkpoint

Negotiation tests now cover invalid version, too-short fixed synchronization payloads, the smallest accepted peer maxLength (11), zero-valued peer windows/timeouts/retry limits, maximum u8/u16 values, and the source-compatible behavior of ignoring a trailing incomplete 3-byte session descriptor. Actions run [#107](https://github.com/qiangge5586-a11y/1/actions/runs/42172506528) passed **7,199 offline checks**; negotiation suite: 146 checks; composition suite: 32 checks. Run #106 failed because a null/null assertion called an equals helper that dereferenced null; it was replaced with an explicit null predicate before #107.

## Post-SYN frame dispatch

The negotiation decoder retains the ACK-bearing frame that transitions the link from NEGOTIATING to NORMAL, plus subsequent complete frames in the same input chunk. A SYN-only frame does not enter the post-ready queue. The composition drains retained frames in wire order and applies the source-audited dispatch rules:

- Cumulative ACK processing runs only when `control & 0x40` is non-zero.
- Extended ACK processing runs independently when `control & 0x20` is non-zero and payload is present; its payload is a raw sequence-byte list.
- Data processing runs when `(control & ~0x40) == 0` and payload is present. Both `0x00` data-only and `0x40` data-plus-ACK frames enter the receive window.
- Header-only ACK frames do not become empty data packets.
- The ACK-bearing frame that completes readiness is retained and dispatched after the negotiation transition, matching the original method's continued processing of the same frame.

Results are exposed as bounded, drainable `DispatchEvent` objects. Encoded ACK/data responses are queued separately and can be inspected through `drainWireFrames()`; they are not written to a transport. The pending event and wire-frame caps are defensive offline-harness limits (256), not claims about original APK constants.

Actions run [#133](https://github.com/qiangge5586-a11y/1/actions/runs/42173519353) passed **7,257 offline checks**, including 84 composition checks and 152 negotiation checks. Regression coverage includes SYN-only staying NEGOTIATING, a later ACK completing READY, ACK+data in the readiness-triggering frame, Extended ACK not mutating the cumulative ACK window, data-only delivery, combined ACK/data delivery, and immediate/delayed ACK timing. Intermediate runs #109–#132 exposed source-assumption and test-fixture errors; those were corrected before #133.


## Source-exact control-bit dispatch checkpoint (2026-11-06)

Recovered Smali confirms the independent control-bit paths in `processFrame()`: reset `0x10`; SYN `0x80`; cumulative ACK/readiness `0x40`; Extended ACK `0x20`; data when all bits except `0x40` are clear and payload is present. A SYN-only frame records peer limits and sends the local ACK but remains NEGOTIATING. An ACK-bearing frame alone runs the readiness gate. The offline model now preserves that ACK-bearing frame for subsequent ACK, Extended ACK and data dispatch.

This is source-derived offline verification only. It does not exercise Bluetooth, USB, TCP, AirPlay encryption, MFi authentication, Android 4.2.2 runtime, or a real iPhone/Honda session.
