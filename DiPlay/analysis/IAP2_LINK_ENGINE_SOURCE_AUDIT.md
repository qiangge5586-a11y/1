# iAP2 Link Engine — Original APK Smali Source Audit

Date: 2026-11-06
Target APK: `diplay0.3.11.apk`
Recorded APK SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`
Decompile workflow run: [#6](https://github.com/qiangge5586-a11y/1/actions/runs/42166126298)
Artifact: `diplay-0.3.11-decompiled` (artifact ID `13478149398`, SHA-256 digest `ff6a124c3e10458361a5f408dff37d97420dbbf05aabd29d1482e31746a7b27a`)
Evidence paths inside artifact:
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkEngine.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkEngine$Companion.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkChannel.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkConfig.smali`
- `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkEngine$SynchronizationPayload.smali`

This document records static source behavior. It does not claim a live iPhone or MY16ADA handshake.

## 1. Two distinct markers

The source contains two markers with different roles:

- Startup marker: `FF 55 02 00 EE 10` (6 bytes), stored in `Iap2LinkEngine.IAP2_MARKER`. `Iap2LinkEngine.start()` writes it; the DETECTING parser requires an exact match before entering negotiation.
- Packet header marker: `FF 5A` (2 bytes), checked by `Iap2LinkEngine$Companion.parseHeader()`.

Do not substitute one for the other. Both values are present in Smali and in the JADX reconstruction.

## 2. Link packet wire format

The original packet header is exactly 9 bytes:

| Offset | Width | Field | Encoding |
|---:|---:|---|---|
| 0 | 1 | start high | `FF` |
| 1 | 1 | start low | `5A` |
| 2 | 2 | total frame length | unsigned big-endian |
| 4 | 1 | control flags | bit field |
| 5 | 1 | send sequence | unsigned |
| 6 | 1 | cumulative acknowledgement | unsigned |
| 7 | 1 | session ID | unsigned |
| 8 | 1 | header checksum | 8-bit additive two's-complement |

When payload is present, the frame is `9-byte header + payload + 1-byte payload checksum`. Total length is `payload.length + 10`. Header-only control packets are 9 bytes. Maximum wire frame is 65,535 bytes; maximum payload is 65,525 bytes.

Checksum algorithm from `Iap2LinkEngine$Companion.checksum()`: sum unsigned bytes over the requested range, then append `(-sum) & 0xff`. Validation sums the complete checksum-bearing region modulo 256 and requires zero. Header and payload are checked independently.

Source method locations (Smali method names):
- `Iap2LinkEngine$Companion.parseHeader([B)`
- `Iap2LinkEngine$Companion.checksum([BI)`
- `Iap2LinkEngine$Companion.checksumValid([B)`
- `Iap2LinkEngine.writePacket([BIII)`
- `Iap2LinkEngine.parseAvailable(J)`

## 3. Control flags and sessions

- `0x80`: synchronization
- `0x40`: acknowledgement; data packets also carry this bit
- `0x20`: extended acknowledgement
- `0x10`: reset

Session IDs:
- 10: control
- 11: extended-ack session descriptor (kind 2, version 1)
- 12: file-transfer session descriptor (kind 1, version 2)

The synchronization payload is version 1 and has a 10-byte fixed prefix:
1. version u8
2. maxOutgoing u8
3. maxLength u16 BE
4. retransmissionTimeoutMillis u16 BE
5. acknowledgementTimeoutMillis u16 BE
6. maxRetransmissions u8
7. maxAcknowledgements u8

It is followed by zero or more 3-byte session descriptors: id, kind, version. The decoder accepts at least 10 bytes and version 1; it consumes complete descriptors and ignores a trailing one or two bytes, matching the original implementation.

## 4. Wireless link configuration (important correction)

The original `Iap2LinkChannel.WIRELESS_LINK_CONFIG` is effectively:
- maxOutgoing = 4
- maxOutgoingDelta = 0
- acknowledgementTimeoutMillis = 500 (the `Iap2LinkConfig` default, sourced from `LwipNcmBridge.WRITE_TIMEOUT_MILLIS`)
- zeroAcknowledgements = false
- controlSessionVersion = 2
- maximumQueuedPackets = 64
- maximumOutOfOrderPackets = 64
- maximumPendingOutputBytes = 1,048,576
- maximumPendingEvents = 256

The Kotlin synthetic-constructor masks shown in the static initializer are 494 (wireless) and 486 (wired). **494/486 are default-argument bit masks, not a negotiated maxOutgoing value or a link window size.** The actual maxOutgoing value is 4 in both configs. Wired differs by setting zeroAcknowledgements=true; wireless uses false.

For wireless local synchronization, the engine advertises:
- maxOutgoing 4
- maxLength 65,535
- retransmission timeout 4,000 ms
- acknowledgement timeout 500 ms
- maxRetransmissions 4
- maxAcknowledgements 3
- sessions `(10,0,2)`, `(11,2,1)`, `(12,1,2)`

The corresponding 19-byte synchronization payload is:
`01 04 FF FF 0F A0 01 F4 04 03 0A 00 02 0B 02 01 0C 01 02`.

For wired mode, `zeroAcknowledgements=true` causes the local sync retransmission timeout, ACK timeout, max retransmissions, and max acknowledgements to be zero. Preserve this distinction; do not normalize both transports to the wireless profile.

## 5. Sequence, ACK and retransmission behavior

- Initial send sequence is 99.
- Sequence increment is `(sequence + 1) & 0xff`.
- Circular distance is `(sequence - previous) & 0xff`.
- Outgoing data uses control `0x40`, session ID, and the current in-order receive sequence in the ACK field.
- Incoming `0x40` packets first process their acknowledgement field, then process payload as data when payload exists.
- The peer ACK advances `lastSentAcknowledged`; unacknowledged packets at or before the accepted cumulative ACK are removed. A packet still ahead of the ACK by no more than `maxAcknowledgements + 10` keeps its retransmission deadline armed.
- The sender queues when the link is not NORMAL or the outstanding sequence distance exceeds peer maxOutgoing. The queue is bounded by config.maximumQueuedPackets.
- In-order receive advances `lastReceivedInOrder` and emits control/session events. Duplicate or too-old/out-of-window packets trigger an ACK; duplicate sequence already in the reorder list is ignored.
- Out-of-order packets are buffered up to config.maximumOutOfOrderPackets. When a sufficiently distant gap is detected (distance >= peer maxOutgoing), the engine emits an extended ACK whose payload is the list of missing sequence numbers as raw u8 bytes, control `0x20`, session 0.
- On receiving extended ACK, the engine retransmits only unacknowledged packets whose sequence numbers appear in the missing list.
- Normal ACKs are emitted after the configured ACK timer or when the receive window threshold is reached. The engine also counts inbound ACK-bearing frames and sends an ACK when accumulated acknowledgements reach peer maxAcknowledgements.
- Retransmission uses the peer-advertised timeout. Retry counters are incremented on timeout or explicit extended-ACK request; the source transitions DEAD when the incremented count equals peer maxRetransmissions.
- Peer payload acceptance requires peer maxLength > 10 and payload length <= maxLength - 10.

Source methods:
- `handleSynchronization`
- `handleAcknowledgement`
- `handleExtendedAcknowledgement`
- `handleData`
- `sendPacket`
- `retransmitDuePacket`
- `sendAcknowledgement`
- `sendExtendedAcknowledgement`
- `writePacket`

## 6. Negotiation and worker timing

- `start()` enters DETECTING and writes the six-byte startup marker.
- While still DETECTING, the marker is resent every 1,000 ms.
- A matching startup marker moves the engine to NEGOTIATING and sends the local synchronization packet.
- Synchronization is retried every 500 ms; after the attempt counter exceeds 20, the engine terminates with a peer-not-responding error.
- In non-initiating wireless mode, `Iap2LinkChannel.runPump()` triggers `initiateSynchronizationIfDetecting()` after 800 ms of silence, once only.
- If non-initiating wireless mode is still not ready after 12,000 ms without received bytes, the channel terminates with the silent-peer timeout.
- The worker reads at most 8,192 bytes per receive call, polls with a maximum 100 ms wait (minimum 1 ms), feeds the engine, flushes output and drains events.
- CSM readiness is not identical to marker receipt or peer TCP connect: the link must be NORMAL, and the peer max control payload must be usable.

## 7. Existing offline implementation status

- `Iap2LinkFrameCodec.java` transcribes packet header, checksum, synchronization payload, sequence increment and sequence distance.
- `Iap2LinkFrameCodecTest.java` contains source-derived golden vectors for synchronization, ACK-only and data frames, plus checksum, bounds and wrap tests.
- These are wire-codec tests, not a full reimplementation of the state machine.
- The original engine remains separate from the compatibility app. No real Bluetooth, TCP, USB, AirPlay or ROOT operation is performed.

## 8. Next engineering steps

1. DONE: `Iap2LinkFrameDecoder` mirrors the original 9-byte header / payload checksum parser, bytewise resynchronization, partial-frame retention and 8,192-byte receive chunk bound. Its tests cover fragmentation, coalescing, bad checksums, resynchronization and maximum-size frames.
2. PARTIAL: `Iap2LinkNegotiationModel` now models startup-marker detection, wireless sync fallback entry, synchronization emission/retry, peer synchronization, ACK readiness gate, invalid peer limits and EOF. It is not yet the complete engine: cumulative ACK windows, extended ACK retransmit selection, out-of-order receive, queue/window release and packet retransmission remain to be modeled.
3. DONE FOR CURRENT LAYERS: deterministic tests cover sequence wrap, invalid checksum, malformed header, sync retry exhaustion, peer maxLength readiness gate and EOF. Full queue/window and retransmit state tests remain open.
4. Keep integration disabled until full model parity; hardware validation remains a separate explicitly authorized stage.


## Latest offline verification (2026-11-06)

CI run [#52](https://github.com/qiangge5586-a11y/1/actions/runs/42167556922) passed 6,980 checks:
- 61 wireless handshake lifecycle checks
- 3,380 AirPlay IAP frame/package checks
- 2,330 CSM frame checks
- 59 link-frame codec checks
- 1,050 incremental link-frame decoder checks
- 100 link negotiation model checks

The model is intentionally partial. It verifies the marker/SYN/ACK startup sequence and readiness gate, not full cumulative ACK, extended ACK, reorder, retransmission, or hardware interoperability.
