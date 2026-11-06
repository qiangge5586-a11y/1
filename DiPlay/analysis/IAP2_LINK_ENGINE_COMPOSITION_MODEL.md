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
