# iAP2 receive-window source verification

Date: 2026-11-06  
Original APK SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`  
Source artifact: [decompile workflow run #7](https://github.com/qiangge5586-a11y/1/actions/runs/42166504575)  
Artifact ID: `13478968581`  
Artifact ZIP digest: `sha256:c6a4a6647d5b556c54eb7c2a611699c6fb2166e2625b67c2802bac15afa2a5ce`

## Exact Smali source locations

Path root inside the artifact:
`decompiled/iap2-smali/smali_classes4/com/shilapi/xcertplay/transport/`

| Class / method | Smali line range | Behavior transcribed |
|---|---:|---|
| `Iap2LinkEngine.handleData(Packet,long)` | 963–1443 | sequence distance, stale/out-of-window ACK, duplicate suppression, out-of-order bound, missing list, ordered delivery, delayed/forced ACK |
| `Iap2LinkEngine.sendExtendedAcknowledgement(List)` | 2906–3024 | control 0x20 and raw missing sequence payload |
| `Iap2LinkEngine.sendAcknowledgement()` | 2873–2890 | emits a normal ACK frame; does not itself update `lastAcknowledgedReceived` |
| `Iap2LinkEngine.advanceTime(long)` | 3414–3784 | delayed ACK deadline handling and timer dispatch |
| `Iap2LinkEngine$Companion.sequenceDistance(int,Integer)` | 502–526 | modulo-256 sequence distance |
| `Iap2LinkEngine$Companion.nextSequence(int)` | 359–370 | modulo-256 sequence increment |

## Source-derived receive behavior

1. Compute `distance = (packet.sequence - lastReceivedInOrder) & 0xff`.
2. If distance is zero or greater than `peerMaxOutgoing + 10`, emit an ACK immediately and return. This path does not update `lastAcknowledgedReceived` or clear an existing delayed-ACK deadline.
3. If the sequence already exists in the out-of-order list, ignore the duplicate without emitting another ACK.
4. If the out-of-order list is already at `maximumOutOfOrderPackets`, terminate with `iAP2 out-of-order packet limit exceeded`.
5. Add the packet. If distance is greater than one and at least peer `maxOutgoing`, construct a byte list containing every missing sequence between the last in-order sequence and the received packet, clear the delayed-ACK deadline, and emit an extended ACK. This is a list of unsigned sequence bytes, not a bitmap.
6. Otherwise sort buffered packets by circular distance from the current in-order sequence and repeatedly deliver the next contiguous sequence. Session 10 becomes a control event; other session IDs become session events.
7. If peer `maxAcknowledgements` is zero, do not schedule a normal ACK.
8. Otherwise compute `windowBeforeForcedAck = max(peerMaxOutgoing - localMaxOutgoingDelta, 1)`. If the number of newly received in-order packets since the last ACK reaches this threshold, update `lastAcknowledgedReceived` and ACK immediately. Otherwise set the delayed-ACK deadline to `now + peerAcknowledgementTimeoutMillis`.
9. When the delayed-ACK deadline expires, `advanceTime()` clears it, updates `lastAcknowledgedReceived`, and emits the ACK.

## Offline implementation

`DiPlay/compat/iap2/Iap2ReceiveWindowModel.java` implements this receive-side behavior without sockets, Bluetooth, Android, crypto, or ROOT access. Its regression tests cover:
- delayed ACK timing and expiry;
- out-of-order buffering and in-order drain;
- duplicate buffered packet suppression;
- extended ACK missing list with exact raw sequence bytes;
- 8-bit wraparound ordering;
- stale/out-of-window immediate ACK behavior without mutating the ACK watermark or timer;
- out-of-order capacity failure;
- forced ACK threshold;
- zero peer ACK limit.

CI [run #80](https://github.com/qiangge5586-a11y/1/actions/runs/42169125240) passed 42 receive-window checks and 7,055 total offline checks.

## Not yet implemented

This helper is not the complete original engine. Still open:
- outbound cumulative ACK window and exact removal boundary;
- queued outbound packet scheduling and writable transitions;
- full unacknowledged packet queue with earliest-deadline retransmission;
- integrating extended-ACK requests with per-packet retransmission counters;
- end-to-end comparison against the original APK implementation;
- live iPhone and MY16ADA hardware validation.

No transport or vehicle test was performed.
