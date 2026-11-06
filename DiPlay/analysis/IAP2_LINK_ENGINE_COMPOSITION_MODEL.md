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

The negotiation decoder now retains every complete frame that arrives after the peer SYN has moved the link to NORMAL, including frames later in the same input chunk. The composition drains those frames in wire order and applies the source-audited dispatch rules:

- Every non-zero control frame advances the cumulative-ACK handler using its header acknowledgement field.
- Extended ACK frames pass their raw payload byte list to the outbound retransmission model.
- Data frames are recognized only when control is exactly ACK (0x40) and payload is present; they are passed to the receive-window model with sequence/session/payload.
- Header-only ACK frames do not become empty data packets.

Results are exposed as bounded, drainable `DispatchEvent` objects. This layer does not serialize ACK/Extended ACK responses or write bytes to a transport; callers receive model actions and must not mistake them for transmitted frames. The pending dispatch-event cap is 256.

Actions run [#113](https://github.com/qiangge5586-a11y/1/actions/runs/42172814303) passed **7,214 offline checks**, including 47 composition checks. The integration test covers SYN and data arriving in the same read, ordered data delivery, raw Extended ACK retransmission selection, and ACK-only dispatch. Runs #109–#112 exposed implementation/test wiring defects while this dispatcher was being introduced; those were fixed before #113.


## Source-exact control-bit dispatch checkpoint (2026-11-06)

Recovered Smali confirms the independent control-bit paths in `processFrame()`: reset `0x10`; SYN `0x80`; cumulative ACK/readiness `0x40`; Extended ACK `0x20`; data when all bits except `0x40` are clear and payload is present. This means `0x00` data-only and `0x40` data-plus-ACK both enter the receive window. The ACK-bearing frame that changes NEGOTIATING to NORMAL is also processed for its payload by the original method, so the offline model retains it for post-ready dispatch.

Regression tests were added for SYN-only staying NEGOTIATING, a later ACK completing READY, Extended ACK not mutating the cumulative ACK window, data-only delivery, and combined ACK/data delivery. The next CI run is the acceptance gate for this correction.
