# iAP2 Link Engine — source-grounded findings

Date: 2026-11-06
Original APK: `diplay0.3.11.apk`
APK SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`
Source artifact: GitHub Actions run [#7](https://github.com/qiangge5586-a11y/1/actions/runs/42166504575)
Artifact: `diplay-0.3.11-decompiled`, ID `13478968581`, expires 2026-12-06
Inspected Smali paths:
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkEngine.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkEngine$Companion.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkEngine$SynchronizationPayload$Companion.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkChannel.smali`
- Related `Header`, `Packet`, `ReceiveBuffer`, `SessionDescriptor`, and `SynchronizationPayload` Smali classes.

This note records behavior read from the original APK's Smali. JADX's decompilation of `Iap2LinkChannel.runPump()` fails with a type-inference overflow, so the worker-loop facts below come from Smali, not the incomplete JADX stub.

## 1. Link startup and framing

### Startup marker

`Iap2LinkEngine.IAP2_MARKER` is exactly:

```
FF 55 02 00 EE 10
```

`start(wiredInitiator, nowMillis)` writes this marker first. The engine enters DETECTING. When the marker is recognized on receive, the receiver discards the six bytes and enters NEGOTIATING. A wired initiator enters NEGOTIATING immediately after sending the marker. The wireless channel uses a non-initiating configuration and has a delayed sync fallback (see section 7).

### Link packet frame

A link packet uses a 9-byte header:
- bytes 0..1: `FF 5A`
- bytes 2..3: total frame length, unsigned big-endian
- byte 4: control flags
- byte 5: sequence number
- byte 6: acknowledgement number
- byte 7: session ID
- byte 8: header checksum

A payload-bearing frame appends payload bytes and one trailing payload checksum byte. The total length is payload length + 10. A control-only frame has length 9 and no payload checksum.

Both checksums are the 8-bit two's-complement of the unsigned-byte sum over the covered region. For a valid region including its checksum, the unsigned-byte sum modulo 256 is zero. Header checksum covers bytes 0..7; payload checksum covers payload bytes only (the checksum byte is appended after them).

Length is decoded big-endian. The receive parser scans for `FF 5A`, discarding one byte at a time until aligned. Header parsing requires exactly 9 bytes, correct marker, and valid header checksum. The engine caps frame size at 65,535 bytes and payload at 65,525 bytes.

## 2. Header controls and sessions

Control bits:
- `0x80`: synchronization
- `0x40`: acknowledgement (also the control bit used on data packets)
- `0x20`: extended acknowledgement
- `0x10`: reset

Session IDs:
- `10`: control
- `11`: extended acknowledgement
- `12`: file transfer

A reset bit causes terminal failure. A synchronization bit decodes the synchronization payload. An acknowledgement bit processes the header acknowledgement field. An extended-ack bit processes its payload as a list of missing sequence numbers. A data packet is dispatched when the control flags satisfy the original data-frame predicate (ACK bit set and no other control bits).

## 3. Sequence and ACK behavior

- Initial local send sequence is 99 (`0x63`).
- Sequence increment is `(sequence + 1) & 0xFF`; wrap from 255 to 0 is intentional.
- Sequence distance is `(sequence - previous) & 0xFF`.
- The synchronization packet uses the current sequence (99); the first normal data packet increments the sequence before sending.
- Outgoing packet headers carry `lastReceivedInOrder` in the acknowledgement field.
- An ACK-only packet uses the current send sequence and the current in-order receive acknowledgement.
- Normal ACK processing updates `lastSentAcknowledged`, removes acknowledged entries from the unacknowledged queue using the circular sequence-distance rule and peer ACK window plus slack 10, then drains queued packets while the negotiated outgoing window permits.
- The engine becomes NORMAL/writable only after peer synchronization is present, peer maxLength is usable, and an ACK-bearing frame (`control & 0x40`) invokes the readiness handler. A SYN-only `0x80` frame records peer limits and sends a local ACK but does not itself open READY. The ACK field value does not have to equal local SYN sequence 99.

## 4. Synchronization payload

The synchronization payload has 10 fixed bytes followed by zero or more 3-byte session descriptors:
1. version (1 byte; source emits version 1)
2. maxOutgoing (1 byte)
3. maxLength (u16 BE)
4. retransmissionTimeoutMillis (u16 BE)
5. acknowledgementTimeoutMillis (u16 BE)
6. maxRetransmissions (1 byte)
7. maxAcknowledgements (1 byte)
8. each session descriptor: id, kind, version (one byte each)

The decoder requires at least 10 bytes and version 1. It reads complete 3-byte descriptors; a trailing incomplete descriptor is ignored by the original decoder.

### Wireless local configuration

`Iap2LinkChannel.WIRELESS_LINK_CONFIG` is instantiated with:
- maxOutgoing = 4
- maxOutgoingDelta = 0
- acknowledgementTimeoutMillis = 500 (defaulted from LwipNcmBridge.WRITE_TIMEOUT_MILLIS)
- zeroAcknowledgements = false
- controlSessionVersion = 2
- default queue/buffer limits (64 queued packets, 64 out-of-order packets, 1 MiB pending output, 256 pending events)

The engine's local synchronization payload for this wireless configuration is exactly:
- maxOutgoing 4
- maxLength 65,535
- retransmission timeout 4,000 ms
- acknowledgement timeout 500 ms
- max retransmissions 4
- max acknowledgements 3
- descriptors: `0A 00 02`, `0B 02 01`, `0C 01 02`

Golden payload:

```
01 04 FF FF 0F A0 01 F4 04 03 0A 00 02 0B 02 01 0C 01 02
```

Golden synchronization frame (sequence 99, acknowledgement 0, session 0):

```
FF 5A 00 1D 80 63 00 00 A7
01 04 FF FF 0F A0 01 F4 04 03 0A 00 02 0B 02 01 0C 01 02 29
```

The offline test `Iap2LinkFrameCodecTest` asserts the source-derived payload and checksum (`0x29`). The prior `BA` byte in this note was a transcription error; the additive two's-complement checksum of the 19-byte payload is `0x29`.

## 5. Out-of-order receive and extended ACK

For an incoming data packet:
- Compute circular distance from its sequence to `lastReceivedInOrder`.
- Distance 0 or greater than peer maxOutgoing + 10 is treated as duplicate/stale/out-of-window; the engine sends an ACK and returns.
- Duplicate sequence already present in the out-of-order list is ignored.
- Exceeding configured out-of-order buffer capacity is terminal.
- A gap smaller than peer maxOutgoing is buffered while waiting for the missing predecessor.
- When the gap reaches or exceeds peer maxOutgoing, the engine constructs an explicit list of missing sequence numbers and emits an extended ACK.
- When the next expected sequence arrives, buffered packets are sorted by circular distance and all contiguous packets are delivered in order.
- Control session 10 becomes a Control event; other session IDs become Session events.

Extended ACK payload bytes are individual missing sequence numbers (unsigned 8-bit values), not a bitmap. On receipt, the engine retransmits only unacknowledged packets whose sequence appears in that list.

## 6. Retransmission behavior

- A newly sent packet receives a deadline of peer retransmission timeout + current time.
- If peer maxRetransmissions is zero, the engine does not retain the packet for retransmission and advances lastSentAcknowledged immediately.
- Otherwise the packet is retained in the unacknowledged list and the earliest packet deadline drives the timer.
- At a due deadline, the engine increments that packet's retransmission count.
- If the incremented count equals the peer's maxRetransmissions, the engine dies with an unacknowledged-packet failure; otherwise it retransmits the exact same sequence/session/payload and rearms the deadline.
- Thus with maxRetransmissions=4, the original transmission is followed by at most three retransmissions before the fourth timeout causes terminal failure. This is the exact source behavior; do not reinterpret the field as "four retries after the initial send".

Synchronization is sent immediately on entering NEGOTIATING. Each unanswered 500 ms deadline increments syncAttempts; if the count is greater than 20 the engine dies, otherwise it sends another synchronization packet and rearms 500 ms. The initial send occurs before the first timeout counter increment.

## 7. Wireless worker timing and readiness

Smali-confirmed `Iap2LinkChannel.runPump()` behavior:
- Creates one `Iap2LinkEngine(linkConfig)`, calls `start(initiateNegotiation, now)`, and keeps engine ownership on the worker thread.
- Drains queued commands, advances engine deadlines, flushes engine output, drains events, then receives at most 8,192 bytes.
- Receive wait is the next engine deadline clamped to 1..100 ms.
- For non-initiating wireless mode, if no bytes have been received after 800 ms, it calls `initiateSynchronizationIfDetecting()` once.
- If the non-initiating wireless link is still not ready after 12,000 ms of silence, it fails with `Bluetooth iAP2 silent peer timeout after 12000ms`.
- The 800 ms fallback and 12 s silence timeout are specific to this non-initiating wireless worker branch.
- EOF is passed to `engine.feedEof()`; the engine transitions to DEAD.
- Handshake progress reporting is rate-limited by the configured interval (10 seconds in the traced path).

The CSM send gate is later than TCP connect or IAP decrypt: the link must publish writable/readiness and the peer maximum control payload must be available and valid before the CSM channel can send.

## 8. Offline test status and remaining work

The new exact wireless synchronization golden vector is asserted by `Iap2LinkFrameCodecTest`. The existing `Iap2LinkFrameCodecTest` already covers ACK-only and data frame vectors, checksum rejection, frame bounds, and 8-bit sequence wrap.

Still required before a complete link-engine model is considered source-equivalent:
1. Golden vectors for incoming synchronization and peer ACK sequence.
2. State-transition tests for negotiation -> NORMAL/writable, including the peer-SYN-triggered ACK processing, arbitrary ACK-field values, too-small peer maxLength, and queued oversized packet failure.
3. Cumulative ACK window tests, including wrap-around and stale/future ACKs.
4. DONE AS AN ISOLATED MODEL: receive-side out-of-order buffering, duplicate suppression, missing-sequence extended ACK generation, ordered delivery, delayed ACK and forced ACK threshold. See `IAP2_RECEIVE_WINDOW_SOURCE_VERIFICATION.md`.
5. PARTIAL: extended-ACK retransmit selection and retry-exhaustion policy are tested in `Iap2RetransmissionPolicyTest`, but are not yet integrated with a live unacknowledged-packet queue.
6. OPEN: outbound cumulative ACK window and queue release, full packet retransmission deadline ordering, and exact ACK removal boundary.
7. OPEN: end-to-end original engine comparison, if a harness can instantiate the original implementation without Android dependencies.

This codec/test layer is offline only. It is not a Bluetooth session, real iPhone handshake, or MY16ADA vehicle test. No ROOT, hotspot, USB, Bluetooth, or live AirPlay action is performed.


## Configuration mask correction

The wireless synthetic constructor mask 494 includes the default-argument bit for acknowledgementTimeoutMillis. The default resolves to LwipNcmBridge.WRITE_TIMEOUT_MILLIS = 500 ms. Therefore the wireless local synchronization advertises ACK timeout 500 ms, not 0 ms; the payload contains `01 F4` at offsets 6..7. The canonical offline implementation and tests are `Iap2LinkFrameCodec.java` and `Iap2LinkFrameCodecTest.java`.


## Source verification refresh (2026-11-06, decompile run #8)

- Latest recovered artifact: [run #8](https://github.com/qiangge5586-a11y/1/actions/runs/42166522659), artifact ID `13479216859`.
- Downloaded ZIP SHA-256: `232b6cc66c22cd4f2815dc75a6978a46398e3cac8a46f6bd2cd621b3237299dc`.
- Confirmed exact Smali method offsets are indexed in [IAP2_NEGOTIATION_MODEL_SOURCE_VERIFICATION.md](IAP2_NEGOTIATION_MODEL_SOURCE_VERIFICATION.md).
- Checksum correction: the complete 29-byte wireless SYN frame ends in payload checksum `29`; any earlier `BA` transcription is incorrect.
- Readiness correction: original `handleAcknowledgement` does not require ACK number 99. While NEGOTIATING, any ACK triggers the peer-sync / peer maxLength / queued-payload readiness checks. ACK before peer sync or with unusable peer maxLength is terminal. The offline model and tests were corrected in commits `74d9d017` and `75b329da` respectively.


## Receive-window model milestone (CI run #80)

`Iap2ReceiveWindowModel.java` now mirrors the source `handleData()` receive path for out-of-order packets, duplicate suppression, extended-ACK missing-sequence lists, in-order event delivery, delayed ACKs and forced ACK threshold. It intentionally remains separate from the incomplete outbound queue/retransmission engine. The 42 new receive-window checks passed in [CI run #80](https://github.com/qiangge5586-a11y/1/actions/runs/42169125240); total offline checks: 7,055.


## Outbound ACK-window policy milestone (CI run #83)

`Iap2AckWindowPolicy.java` isolates the original modulo-256 ACK/window predicates from `handleAcknowledgement()` and `sendPacket()`: outstanding packet retention uses `distance > 0 && distance <= peerMaxAcknowledgements + 10`; new packet queueing uses `distance > peerMaxOutgoing`; queued drain uses `distance < peerMaxOutgoing`. The strict boundaries are intentionally separate. All 17 ACK-window policy checks passed in [CI run #83](https://github.com/qiangge5586-a11y/1/actions/runs/42169304682), bringing the offline suite to 7,072 checks. This remains a policy helper, not the complete outbound packet queue or retransmission engine. See `IAP2_ACK_WINDOW_SOURCE_VERIFICATION.md`.


## Outbound state model milestone (CI run #90)

`Iap2OutboundWindowModel.java` now composes the source-derived send window, cumulative ACK packet retention/removal, queued packet drain, per-packet retransmission deadline, same-sequence retransmission and Extended ACK requested-sequence retry behavior. The 57-check regression suite passed in [CI run #91](https://github.com/qiangge5586-a11y/1/actions/runs/42169773435), total 7,129 offline checks. A two-packet staggered-deadline test confirms that retransmitting the earliest due packet leaves the next packet's earlier deadline armed, and that the next call selects that packet. The model remains transport-free; source-golden verification of multiple simultaneous due-packet deadline re-arming and production link-pump integration remain open. See `IAP2_OUTBOUND_WINDOW_MODEL.md`.

## Offline composition checkpoint (2026-11-06)

The repository now includes `compat/iap2/Iap2LinkEngineModel.java`, an orchestration harness over the independently tested negotiation, outbound-window, and receive-window models. It supplies a single READY gate, delegates send/ACK/Extended-ACK and receive operations, advances both timer domains at one deterministic timestamp, and models EOF closure. Its test suite passed 19 checks in Actions run [#98](https://github.com/qiangge5586-a11y/1/actions/runs/42170739023); the complete workflow passed 7,148 offline checks. A follow-up verifies that an outbound retry-limit failure propagates to the composed CLOSED state.

This composition is intentionally incomplete: peer window limits are constructor inputs rather than being extracted and validated from the peer SYN by the composition layer; there is no unified live frame dispatch, socket/USB/Bluetooth transport, or output writer. It is not evidence of an actual iPhone or Honda head-unit session. The initial composition CI run #94 failed due to a null test fixture; the fixture was corrected and #95 passed.

## Peer SYN configuration handoff and readiness correction (2026-11-06)

The offline negotiation model now exports an immutable peer configuration snapshot with all six fixed SYN fields, the peer synchronization sequence, and complete session descriptors. The composition model can now build both windows from that snapshot instead of relying on caller-supplied window values. Wireless defaults applied locally are documented in `IAP2_LINK_ENGINE_COMPOSITION_MODEL.md`; they include ACK slack 10, maxOutgoingDelta 0, and defensive queue/buffer caps of 64.

A source-consistency correction was also made: the original `processFrame()` calls its ACK/readiness handler for every non-zero control byte, including the SYN control frame. Therefore the peer SYN can complete READY immediately after its limits are recorded and the local ACK is queued; the model no longer requires a separate ACK-only frame for that transition.

Verification: Actions run [#105](https://github.com/qiangge5586-a11y/1/actions/runs/42172333952) passed **7,174 offline checks**, including 121 negotiation checks and 32 composition checks. This remains an offline model, not a live iPhone/vehicle handshake.

## SYN boundary regression tests

The peer synchronization snapshot now has regression coverage for malformed version/fixed payloads, minimum usable maxLength 11, maximum u16/u8 values, zero-valued window/timer/retry fields, and trailing incomplete session descriptor handling. Actions run [#107](https://github.com/qiangge5586-a11y/1/actions/runs/42172506528) passed **7,199 offline checks**. Run #106 failed on a null-assertion test helper defect, corrected before the successful run.

## Post-readiness frame dispatch checkpoint

The offline negotiation parser now retains frames parsed after the peer SYN completes readiness. The composition dispatches cumulative ACK fields for non-zero control frames, raw Extended ACK missing-sequence lists, and data frames only when the control byte is exactly 0x40 and a payload exists. Header-only ACKs are kept separate from data. Dispatch results are returned as bounded events; they are not serialized or transmitted.

Actions run [#113](https://github.com/qiangge5586-a11y/1/actions/runs/42172814303) passed **7,214 offline checks**, including 47 composition checks. This validates offline dispatch semantics only, not a live link pump or vehicle session.
