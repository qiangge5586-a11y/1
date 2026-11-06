# iAP2 Link Engine Composition Model

## Purpose and boundary

`Iap2LinkEngineModel` is an offline orchestration harness that composes the already audited negotiation, outbound-window, and receive-window models behind a single readiness gate. It is not a replacement for the original `Iap2LinkEngine`, does not open Bluetooth/USB/TCP streams, and does not encode/decode live data-plane packets.

The caller supplies the peer-window parameters when constructing the outbound and receive models. This deliberate boundary avoids pretending that the current negotiation model exposes every negotiated peer field: it currently records peer maximum frame length and readiness, but not the complete peer window configuration.

## Lifecycle

- `IDLE`: not started.
- `NEGOTIATING`: startup marker / synchronization negotiation is in progress.
- `READY`: the negotiation model accepted the peer synchronization and subsequent ACK gate.
- `CLOSED`: EOF or negotiation failure closed the composition.

Data-window methods reject calls before `READY`. Once ready, outbound send/ACK/Extended-ACK and inbound receive operations are delegated to their existing deterministic models. `advanceTimers(now)` advances both independent timer models against the same supplied monotonic clock.

## Important limits

- Negotiation frame handling is delegated to `Iap2LinkNegotiationModel`; the harness does not add new wire-format assumptions.
- Outbound and inbound windows are composed, not merged into one packet parser or transport pump.
- Peer window settings are explicit constructor inputs, not automatically extracted from the SYN payload yet.
- Timer results are model events; the caller must decide how a real transport would serialize and write them.
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

The source findings state that the original `processFrame()` invokes `handleAcknowledgement()` for every non-zero control byte, including the peer SYN frame. The negotiation model now follows that behavior: after recording a valid peer SYN and queuing the local ACK, the non-zero SYN control frame can complete the READY transition if peer maxLength is usable (>10). A separate ACK-only frame is not required by this source path. Tests were updated to assert this behavior.

## Verification

Actions run [#105](https://github.com/qiangge5586-a11y/1/actions/runs/42172333952) passed **7,174 offline checks**. The new peer-parameter composition test verifies the negotiated 1500 ms retransmission timeout, 250 ms delayed-ACK timeout, sequence baseline, and peer window values are actually used by both window models. The 32-check composition suite passed.

Earlier run #102 failed because Java boxed an `int` and `long` into unequal wrapper types despite equal printed values; run #104 exposed stale test expectations after correcting readiness to match Smali. Both were fixed before #105. No real transport or vehicle test was performed.
