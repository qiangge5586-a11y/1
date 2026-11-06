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
- The engine becomes NORMAL/writable only after it has received peer synchronization, peer maxLength is usable, and any already queued packets fit the peer's advertised payload bound. The transition is performed while handling the peer ACK after synchronization.

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
01 04 FF FF 0F A0 01 F4 04 03 0A 00 02 0B 02 01 0C 01 02 BA
```

The offline test `Iap2LinkFrameCodecTest` now asserts this source-derived vector.

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
2. State-transition tests for negotiation -> NORMAL/writable, including too-small peer maxLength and queued oversized packet failure.
3. Cumulative ACK window tests, including wrap-around and stale/future ACKs.
4. Out-of-order buffering, duplicate suppression, missing-sequence extended ACK and ordered delivery tests.
5. Extended ACK retransmit selection and retry-exhaustion tests.
6. Timer tests for sync retry boundary, delayed ACK, retransmit deadline ordering, and EOF.
7. End-to-end original engine comparison, if a harness can instantiate the original implementation without Android dependencies.

This codec/test layer is offline only. It is not a Bluetooth session, real iPhone handshake, or MY16ADA vehicle test. No ROOT, hotspot, USB, Bluetooth, or live AirPlay action is performed.


## Configuration mask correction

The wireless synthetic constructor mask 494 includes the default-argument bit for acknowledgementTimeoutMillis. The default resolves to LwipNcmBridge.WRITE_TIMEOUT_MILLIS = 500 ms. Therefore the wireless local synchronization advertises ACK timeout 500 ms, not 0 ms; the payload contains `01 F4` at offsets 6..7. The canonical offline implementation and tests are `Iap2LinkFrameCodec.java` and `Iap2LinkFrameCodecTest.java`.
