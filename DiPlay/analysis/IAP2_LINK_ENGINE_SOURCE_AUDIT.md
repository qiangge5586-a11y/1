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


## Retransmission policy offline milestone (2026-11-06)

The new `Iap2RetransmissionPolicy` helper isolates two source-confirmed rules from `Iap2LinkEngine` without claiming to implement the complete engine:

- Peer `maxRetransmissions=0` disables retention/retransmission.
- Otherwise the packet retry counter is incremented first; when the incremented count equals the peer limit, the link terminates. With peer limit 4, retransmission counts 1, 2 and 3 are allowed; count 4 terminates. This means three retransmissions after the original send, not four.
- Explicit missing-sequence requests from extended ACK use the same retry-counter policy.
- Extended-ACK payload membership is treated as a list of unsigned sequence bytes, not a bitmap.

A separate negotiation regression was also fixed: if the peer's valid synchronization arrives after an ACK of the local SYN, the model now re-evaluates the readiness gate on the synchronization event. CI had exposed that ordering bug; the correction is covered by the ACK-before-peer-SYN test.

CI run [#64](https://github.com/qiangge5586-a11y/1/actions/runs/42168651726) passed **7,004 offline checks**: 61 wireless handshake, 3,380 AirPlay IAP framing/package, 2,330 CSM, 59 link codec, 1,050 incremental link decoder, 104 negotiation model, and 20 retransmission-policy checks.

Scope remains deliberately limited: the helper does not yet own a pending-packet queue, cumulative ACK window, reorder buffer, timer scheduler, or transport. Those are still separate implementation stages and must be compared against the original Smali before integration. No live iPhone, MY16ADA, Bluetooth, USB, AirPlay or ROOT operation was performed.


## Retransmission counter representation cross-check (2026-11-06)

The original Smali artifact's `Iap2LinkEngine$Packet` declares `retransmissions:I` (32-bit signed integer), and `retransmitDuePacket(J)` performs `getRetransmissions()`, `add-int/2addr`, then compares the incremented integer for exact equality with peer `maxRetransmissions`. The peer limit itself is decoded from a u8 synchronization field. The normal reachable lifecycle increments from zero and terminates exactly when the counter reaches that peer limit (0 disables retention), so values above the negotiated u8 limit are not reached in a valid packet lifecycle. Do not reinterpret the internal counter field as a wire u8.


## Terminal-state cleanup parity (2026-11-06)

The original `Iap2LinkEngine.die(reason)` Smali path cancels marker/SYN/ACK deadlines, transitions to DEAD, marks the link non-writable, resets the pending output buffer, clears pending events and enqueues a terminal Dead event. The offline composition previously exposed already-queued wire frames and dispatch events after EOF or a terminal window failure. It now closes the negotiation model as well and clears pending wire frames, dispatch events, pre-ready packets and ACK-batch state. The negotiation model clears pending encoded output, post-ready frames, partial startup-marker bytes and decoder pending bytes on its terminal transition. The offline model retains the terminal reason but does not yet define a separate Dead-event type.

Regression coverage includes EOF before/after readiness, stale output/event suppression after EOF, and terminal retransmission failure clearing pending output/events. CI run [#203](https://github.com/qiangge5586-a11y/1/actions/runs/42180479594) passed **2,323,717 Java 7 offline checks**, including 217 link-engine composition checks and 184 negotiation checks. No live Bluetooth/USB/vehicle test was performed.


## Pending-output byte budget parity (2026-11-06)

A further source check of `Iap2LinkEngine.appendOutput([B)` confirms the original enforces `maximumPendingOutputBytes` by comparing the incoming encoded bytes against the remaining capacity of its `ByteArrayOutputStream`; the wireless config sets this to **1,048,576 bytes**. It does not impose a 256-frame output-count limit (the separate 256 limit is for pending events). The offline engine model now tracks aggregate encoded-frame bytes, accepts output up to the exact byte budget, and transitions terminal/clears pending output when the next frame exceeds it.

Regression test queues sixteen maximum-size 65,535-byte wire frames (1,048,560 bytes), then verifies a further 30-byte frame exceeds the remaining 16-byte budget and clears the output queue. CI run [#205](https://github.com/qiangge5586-a11y/1/actions/runs/42180659719) passed **2,323,738 Java 7 offline checks**, including 238 link-engine composition checks. No live transport or vehicle test was performed.


## Extended ACK retransmission deadline parity (2026-11-06)

A direct comparison with the original `Iap2LinkEngine.handleExtendedAcknowledgement()` and `retransmitDuePacket()` confirms two distinct deadline rules:

- Extended ACK-triggered retransmission increments the packet retry counter and sends the same sequence/session/payload, but preserves that packet's existing `deadlineMillis`. It also clears the send-ACK deadline and rearms the receive-ACK deadline from the preserved packet deadline.
- Timer-triggered retransmission changes the selected earliest-due packet deadline to `nowMillis + peerRetransmissionTimeoutMillis`; after sending, the receive-ACK timer is set to the next remaining packet deadline, or the retried packet's new deadline when no other packet remains.

The offline outbound model now keeps these paths separate. Regression expectation for an Extended ACK at t=20 against a packet originally due at t=101 is that its deadline remains t=101, not t=120. This correction is covered by `Iap2OutboundWindowModelTest.extendedAckRetransmitsOnlyRequestedSequences()`.


## Startup marker versus frame sync bytes (clarification, 2026-11-06)

The original source contains two different byte sequences with different roles; they must not be conflated:

- Startup marker used by the detecting state: `FF 55 02 00 EE 10` (six bytes, retransmitted on the marker timer).
- Start bytes of each encoded iAP2 link frame: `FF 5A` (the frame header's two-byte sync word).

Both values are directly present in the archived original `Iap2LinkEngine` source/Smali: `IAP2_MARKER = {-1, 85, 2, 0, -18, 16}`, while `LINK_START_HIGH = -1` and `LINK_START_LOW = 90`. The model's `IAP2_MARKER` and frame codec constants intentionally represent these separate protocol layers. Earlier shorthand describing `FF 5A` as the startup marker was imprecise; `FF 5A` is the per-frame sync word.

## Follow-up source parity audit: cumulative ACK, receive window, and lifecycle

Audited against the archived JADX source for `com.shilapi.xcertplay.transport.Iap2LinkEngine` (2026-11-06):

- Sequence arithmetic is unsigned 8-bit modular distance: `(sequence - previous) & 0xff`; next sequence is `(sequence + 1) & 0xff`. The offline receive/outbound models use the same arithmetic.
- Cumulative ACK handling stores the received ACK watermark directly, then removes outstanding packets from the head until the first packet whose modular distance remains within `peerMaxAcknowledgements + 10`. It rearms the receive-ACK deadline from that first remaining packet's original deadline, then drains queued packets while the sent-to-acknowledged distance is strictly less than peer maxOutgoing.
- Receive handling ignores duplicate last-in-order and packets farther than `peerMaxOutgoing + 10` after sending an immediate ACK; suppresses duplicate out-of-order sequence numbers; bounds out-of-order storage by configured maximum; sorts by modular distance; delivers contiguous packets; and emits Extended ACK only when a gap distance is at least peer maxOutgoing.
- Delayed ACK uses `max(peerMaxOutgoing - maxOutgoingDelta, 1)`; when that threshold is reached it advances lastAcknowledgedReceived and ACKs immediately, otherwise it schedules `now + peerAcknowledgementTimeoutMillis`. When peer maxAcknowledgements is zero, data delivery does not schedule delayed ACK.
- Terminal `die(reason)` cancels all four timers, marks DEAD, sets writable false, clears pending wire output and all queued events, then emits only one Dead event. The offline engine adapter intentionally clears its pending wire frames/events but does not expose the same event type.

### Remaining parity checks before production wiring

- The offline `Iap2ReceiveWindowModel` and `Iap2OutboundWindowModel` encode the core sequence/window rules, but they are still separate deterministic models; they are not a drop-in replacement for the original engine's event queue, synchronization, or timer ownership.
- Need dedicated differential tests for cumulative ACK edge values across wraparound, ACK values outside the nominal outstanding span, delayed-ACK rearming after duplicate/out-of-window packets, and the exact out-of-order limit boundary.
- The RFCOMM connector currently applies the supplied timeout independently to each of its three fallback attempts. Therefore its worst-case wall time can exceed the nominal timeout (up to roughly three attempts plus fallback delay). This is a concrete integration risk; decide whether the timeout is per-attempt or a single overall connection deadline before wiring it into a user-facing connect flow.
- `BluetoothRfcommByteStream` uses a 1 MiB bounded receive ring and a dedicated blocking reader. Closing the socket is relied upon to release that reader; this must be verified on Android API 17/vendor Bluetooth stack. No head-unit test has yet established this behavior.

No production behavior was changed by this audit. The ROOT hotspot configuration module remains untouched.

## Retransmission timer ownership correction

A deeper comparison of `nextDeadlineMillis()`, `advanceTime()`, `handleAcknowledgement()`, `handleExtendedAcknowledgement()` and `retransmitDuePacket()` found that the engine owns one explicit `receiveAcknowledgementDeadlineMillis`. It is not equivalent to the minimum deadline across every unacknowledged packet.

- Every successfully transmitted retained packet assigns the timer to that packet's deadline; multiple sends therefore leave the last transmitted packet's deadline armed.
- Cumulative ACK handling rearms from the first outstanding packet that remains inside the ACK-retention window, or clears the timer if none remains. If queued packets are drained afterward, their sends can replace that timer.
- Extended ACK handling iterates the outstanding list; each requested packet replaces the timer with its preserved packet deadline. The last matching packet in outstanding-list order determines the timer.
- When the timer fires, the engine selects the earliest-deadline outstanding packet, retransmits only that one, and rearms from the earliest deadline among the other outstanding packets (or the newly advanced deadline if no other packet remains). A subsequent already-due timer is handled on a later engine advance call.

The offline `Iap2OutboundWindowModel` previously exposed the minimum packet deadline directly. That was an observable source-parity mismatch because the pump could wake and retransmit earlier than the original engine. The model now stores the source timer explicitly and its tests cover staggered sends, cumulative-ACK rearming, sequential due retries and multi-entry Extended ACK ordering. CI must pass on the resulting commit before this correction is considered verified.

This is a source-derived model correction only. It does not establish production engine integration or device interoperability.

### Verification for retransmission timer correction

GitHub Actions iAP2 run [#275](https://github.com/qiangge5586-a11y/1/actions/runs/42193537235) completed successfully on commit `53d4f139`. The Java 7 offline suite passed, including:
- `Iap2OutboundWindowModelTest: PASS (78 checks)`
- `Iap2LinkEngineModelTest: PASS (264 checks)`
- `Iap2ReceiveWindowModelTest: PASS (1,135,658 checks)`
- `Iap2AckWindowPolicyTest: PASS (1,179,665 checks)`
- `RfcommConnectDeadlineTest: PASS (8 checks)`

The run verifies the deterministic offline model and its timer tests. It does not verify a production link engine, RFCOMM connection on API 17, or an iPhone/vehicle handshake.
