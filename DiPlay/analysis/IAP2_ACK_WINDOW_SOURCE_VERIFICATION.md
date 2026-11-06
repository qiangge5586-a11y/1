# iAP2 outbound ACK-window source verification

Date: 2026-11-06  
Original APK SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`  
Smali artifact: decompile workflow run [#7](https://github.com/qiangge5586-a11y/1/actions/runs/42166504575), artifact ID `13478968581`.

## Source method locations

Path root: `decompiled/iap2-smali/smali_classes4/com/shilapi/xcertplay/transport/`

| Method | Smali lines | Relevant source rule |
|---|---:|---|
| `Iap2LinkEngine.handleAcknowledgement(int,long)` | 677–962 | cumulative ACK removal, retransmission deadline re-arm, queued packet drain |
| `Iap2LinkEngine.sendPacket(Packet,long)` | 3025–3204 | send sequence increment, outgoing window gate, initial deadline and unacknowledged retention |
| `Iap2LinkEngine.handleExtendedAcknowledgement(byte[])` | 1444–1657 | raw missing-sequence membership, explicit retransmission and retry limit |
| `Iap2LinkEngine.retransmitDuePacket(long)` | 2432–2872 | earliest deadline selection, second deadline, retry increment and terminal boundary |
| `Iap2LinkEngine.sendData(int,int,byte[])` | 2891–2905 | retransmitted packet preserves sequence/session/payload |

## Cumulative ACK and window predicates

The source uses modulo-256 distance:

`distance = (sequence - previous) & 0xff`

For each oldest unacknowledged packet, `handleAcknowledgement()` computes its distance from the newly accepted cumulative ACK:
- keep the packet and retain its retransmission deadline only when `distance > 0 && distance <= peerMaxAcknowledgements + 10`;
- otherwise remove it from the unacknowledged list;
- after processing, drain queued packets while `distance(sentSequence, lastSentAcknowledged) < peerMaxOutgoing`.

The new-packet path in `sendPacket()` uses a distinct strict comparison:
- queue only when `distance(sentSequence, lastSentAcknowledged) > peerMaxOutgoing`;
- otherwise increment the sequence first and transmit.

Do not collapse these strict comparisons into one generic `<= window` rule. The send path and queue-drain path have different source boundaries.

## Retransmission source behavior

- New packet deadline is `now + peer retransmissionTimeoutMillis`.
- If peer max retransmissions is zero, the packet is not retained in the unacknowledged list and the sent watermark advances immediately.
- Otherwise the packet is retained with its sequence, session, payload, retry counter and deadline.
- The timeout path selects the packet with the earliest deadline and computes the next-earliest deadline excluding that packet.
- It moves the selected packet deadline to `now + timeout`, increments retry count, and terminates when the incremented count equals peer max retransmissions. Otherwise it retransmits the same sequence/session/payload and arms the next deadline to the minimum of the new deadline and the second-earliest packet deadline.
- Extended ACK payload is a raw byte list. Only unacknowledged packets whose sequence appears in that list are retransmitted. Each requested packet uses the same retry counter and terminal boundary.

## Offline implementation status

`DiPlay/compat/iap2/Iap2AckWindowPolicy.java` implements the source comparison predicates and modulo-256 distance only. Its tests cover:
- cumulative ACK keep/remove boundary at `maxAcknowledgements + 10`;
- wraparound across 255 -> 0;
- strict new-send queue comparison;
- strict queued-drain comparison;
- null ACK watermark behavior.

It is not a full outbound queue or retransmission engine. The isolated retransmission helper remains in `Iap2RetransmissionPolicy.java`; integration with actual packet/deadline state is still open.

No live Bluetooth, TCP, AirPlay, USB, ROOT or vehicle test was performed.
