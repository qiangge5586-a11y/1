# iAP2 outbound window and retransmission model

Date: 2026-11-06  
Source: DiPlay 0.3.11 Smali, artifact [decompile run #7](https://github.com/qiangge5586-a11y/1/actions/runs/42166504575), artifact ID `13478968581`.  
Detailed source line map: `IAP2_ACK_WINDOW_SOURCE_VERIFICATION.md`.

## Implemented offline model

`DiPlay/compat/iap2/Iap2OutboundWindowModel.java` composes the independently source-verified policies into a deterministic state model:

- outbound sequence allocation and modulo-256 wrap;
- strict `distance > peerMaxOutgoing` queue gate;
- strict `distance < peerMaxOutgoing` queued-send drain gate after cumulative ACK;
- cumulative ACK acceptance and removal of acknowledged packets;
- retransmission deadline assignment and earliest-deadline lookup;
- retransmission of the same sequence, session ID and payload;
- increment-before-equality retry termination rule;
- Extended ACK raw sequence list selection and per-packet retry handling;
- no retention when peer max retransmissions is zero.

The queue capacity parameter is a defensive bound of this offline harness, not a claim about the original APK's exact queue capacity. No Android or transport operations are included.

## Regression tests

`Iap2OutboundWindowModelTest.java` covers:
- initial send and retention deadline;
- max retransmissions zero;
- queue at the strict window boundary and drain after cumulative ACK;
- cumulative ACK removing only covered packets;
- out-of-window ACK ignored without mutation;
- timeout retransmitting identical packet identity/payload;
- retry exhaustion at the exact peer limit;
- Extended ACK retransmitting only listed outstanding sequences;
- 8-bit sequence wrap;
- local harness queue-capacity failure;
- earliest deadline tracking.

## Known limits

This remains a deterministic compatibility model, not the production link engine. It does not encode or write frames to Bluetooth/TCP, schedule Android timers, handle concurrent sender/receiver threads, or validate a real iPhone session. Source behavior around retransmission deadline re-arming and multiple simultaneously due packets still needs golden-vector comparison against the original engine. The helper deliberately exposes state/events for deterministic tests rather than claiming a complete transport integration.

No live iPhone, MY16ADA head unit, USB, AirPlay, Bluetooth or ROOT test was performed.
