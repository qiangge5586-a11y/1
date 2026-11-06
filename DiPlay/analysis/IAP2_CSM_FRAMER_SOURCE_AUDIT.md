# Original iAP2 CSM Framer Source Audit

Date: 2026-11-06

## Recovered source

The source was checked from GitHub Actions artifact `diplay-0.3.11-decompiled`:

- Workflow run: [#6](https://github.com/qiangge5586-a11y/1/actions/runs/42166126298)
- Artifact ID: `13478149398`
- Artifact SHA-256: `ff6a124c3e10458361a5f408dff37d97420dbbf05aabd29d1482e31746a7b27a`
- APKTool paths:
  - `decompiled/apktool/smali_classes3/com/shilapi/xcertplay/iap2/wire/Iap2CsmFramer.smali`
  - `decompiled/apktool/smali_classes3/com/shilapi/xcertplay/iap2/wire/Iap2CsmFramer$ReceiveBuffer.smali`
  - `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2CsmChannel.smali`
  - `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/Iap2LinkChannel.smali`
- JADX reconstruction: `decompiled/jadx/sources/com/shilapi/xcertplay/iap2/wire/Iap2CsmFramer.java`

## Verified call path

1. `Iap2CsmChannel.recv()` calls `Iap2LinkChannel.recvControl(timeout)`.
2. When a control payload is returned, `Iap2CsmChannel` passes that byte array to its `framer.offer(payload)`.
3. `Iap2CsmFramer.offer()` drains any complete frames, appends as many bytes as fit in its 65,535-byte receive buffer, drains again, and repeats until the entire supplied array is consumed.
4. `Iap2CsmChannel.send()` obtains `peerMaxControlPayloadBytes()` and calls `Iap2CsmFramer.splitForLink(frame, peerMaxControlPayloadBytes)` before sending each link chunk.

This is the decisive boundary: CSM `offer()` receives already reassembled link-control payload chunks. It does not receive the raw byte stream chunks read by `Iap2LinkChannel.runPump()`. Therefore the lower-level `recv(8192, waitMillis)` read size is not the CSM framer's input chunk limit.

## Verified CSM wire format and buffering

- Start marker: `0x4040`, two bytes big-endian.
- Header: 6 bytes total.
- Total frame length: u16 big-endian at offset 2; includes the 6-byte header.
- Message ID: u16 big-endian at offset 4.
- Payload: bytes from offset 6 to total length.
- Valid total frame length: 6..65,535.
- Receive buffer: fixed 65,535 bytes, with head/tail indices and compaction.
- Parser behavior: discard one byte when the marker is absent; when fewer than six bytes remain, wait; if candidate length is below six, discard one byte; if the full candidate is not yet present, wait; otherwise emit frame and discard its full length.
- The original parser's `offer()` loops across arbitrarily sized caller arrays by appending only the available buffer capacity and draining between appends.

## Link chunk size and offline model boundary

The original `Iap2CsmFramer` defines `MAX_LINK_CHUNK_BYTES = 65525`. `splitForLink()` accepts chunk sizes 1..65,525 and divides an encoded CSM frame into link-control payload chunks of at most that size.

The offline `Iap2CsmFrameDecoder` now mirrors the original receive-buffer loop: accept the caller's entire byte array, drain pending frames, append only available capacity to a fixed 65,535-byte pending buffer, and repeat until all input is consumed. This avoids confusing raw 8,192-byte stream reads with reassembled link-control payloads and supports arbitrarily coalesced input arrays without unbounded pending storage.

## CI

Actions run [#163](https://github.com/qiangge5586-a11y/1/actions/runs/42175105245) passed **7,393 offline checks**, including 2,330 CSM decoder checks. Java 7 offline verification only; no live Bluetooth, USB, TCP, AirPlay, iPhone, MFi or MY16ADA session is claimed.


## Offline parity follow-up

The compatibility decoder's `feed()` now uses the same bounded append/drain pattern as the source `offer()`. A regression test supplies two complete 40,006-byte CSM frames in one 80,012-byte input array; both frames are emitted and pending storage returns to zero. This validates caller-array coalescing across the fixed receive-buffer capacity.


## Latest implementation verification

Actions run [#166](https://github.com/qiangge5586-a11y/1/actions/runs/42175369108) passed **7,397 offline checks**, including 2,334 CSM decoder checks. The coalesced-input test exercises the original append/drain behavior with an 80,012-byte caller array containing two complete CSM frames. This is source-informed offline parity testing, not hardware interoperability validation.


## CSM channel queue and send backpressure (verified Smali)

The same recovered `Iap2CsmChannel.smali` confirms the channel-level behavior surrounding the framer:

- `enqueue(frame)` counts the full encoded CSM frame size, including its six-byte header.
- Receive queue admission requires both fewer than 64 queued frames and enough remaining capacity under 1,048,576 queued bytes.
- If either bound would be exceeded, the channel fails closed with `IOException("CSM received-frame queue limit exceeded")`.
- `takeReceived()` removes the oldest frame and subtracts its full encoded size from `receivedBytes`.
- `send(frame, timeout)` obtains `peerMaxControlPayloadBytes()`, calls `splitForLink()`, and submits each chunk through `sendControlAwaitCapacity` under one deadline.
- If the complete CSM frame cannot be queued before that deadline, the channel fails closed with `IOException("iAP2 could not queue the complete CSM frame before its timeout")`.

These queue limits are source-derived original behavior, distinct from the offline decoder's fixed receive-buffer limit. The next implementation gate is to model/test queue admission, byte accounting, timeout and fail-closed semantics separately from link-layer sequence/ACK state.


## Receive-queue offline model

Added `Iap2CsmReceiveQueueModel` as a separate Java 7 offline model of the original `Iap2CsmChannel.enqueue()` / `takeReceived()` accounting:

- FIFO admission is limited to 64 queued frames.
- Aggregate encoded bytes (six-byte CSM header plus payload) are limited to 1,048,576.
- The enqueue that exceeds either limit fails closed with the original queue-limit reason; already queued frames remain pollable, and polling decrements the exact encoded-byte count.
- Tests cover FIFO order, 64-frame cap, exact 1 MiB boundary, overflow, payload defensive copy and post-close rejection.

Actions run [#169](https://github.com/qiangge5586-a11y/1/actions/runs/42175597475) passed **7,705 offline checks**, including 308 receive-queue checks. This model is not yet wired into a live CSM channel.


## CSM outbound link-chunk plan model (2026-11-06)

Added `Iap2CsmSendPlan` as an offline mirror of the source `splitForLink()` chunking contract. It partitions an already encoded CSM frame into independent chunks no larger than the peer-advertised control-payload maximum, constrained to 1..65,525 bytes. Tests cover a 1,000-byte frame with a 127-byte peer limit, exact and partial final chunks, one-byte and maximum peer limits, 65,535-byte aggregate input, invalid peer limits, empty input and defensive copies.

Actions run [#176](https://github.com/qiangge5586-a11y/1/actions/runs/42176152126) passed **8,002 Java 7 offline checks**, including 297 send-plan checks. This model only validates chunk planning. It does not simulate `sendControlAwaitCapacity()` blocking, queue pressure, deadline expiration, channel close or live transport writes; those require a separate source-derived model before integration.


## Link-layer command queue and shared CSM send deadline (2026-11-06)

The recovered Iap2LinkChannel source confirms a second, separate outbound queue behind CSM chunking:

- Pending link commands are capped at 64 entries and 1,048,576 aggregate payload bytes.
- Each control payload is independently limited to 65,525 bytes.
- sendControlAwaitCapacity waits until the channel is closing/terminated or both queue bounds allow the payload. A zero timeout still succeeds immediately if capacity is already available; otherwise it returns false.
- The pump removes a command from the pending queue and decrements byte accounting before passing it to Iap2LinkEngine.sendSession(). Capacity therefore represents pending commands awaiting pump admission, not unacknowledged link frames.
- Closing/termination wakes waiters. A terminal failure is thrown before the method returns false for a closed channel.
- Iap2CsmChannel.send() computes one deadline for the whole CSM frame and passes the remaining rounded-up milliseconds to every chunk wait. It does not restart the timeout for each chunk. Timeout is constrained to 0..300,000 ms; default send timeout is 5,000 ms.
- Remaining time is rounded up to milliseconds and capped at 300,000 ms. If a chunk cannot be queued before the shared deadline, CSM fails closed.

Added two Java 7 offline parity models:
- Iap2LinkCommandQueueModel: FIFO pending queue, 64-entry/1 MiB bounds, 65,525-byte per-command bound, byte-accounting release at pump removal, close/terminate rejection, and defensive copies.
- Iap2CsmSendDeadline: deterministic shared-deadline arithmetic and ceil-to-millisecond remaining budget.

Their tests cover exact capacity boundaries, queue-full behavior, release/re-admission, FIFO, close/termination, invalid payload lengths, zero timeout, deadline expiry, rounding and shared budget decay. These models do not perform blocking waits, link-engine admission, ACK/retransmission, or transport I/O; those remain distinct layers.


### CI verification for queue/deadline models

Actions run [#183](https://github.com/qiangge5586-a11y/1/actions/runs/42177750683) passed **8,145 Java 7 offline checks**. The new suites passed with 130 command-queue checks and 13 shared-deadline checks. An earlier run #182 failed because the new source files were not included in the javac input list; the workflow was corrected and run #183 compiled and passed all suites.


## CSM chunk admission under link-queue backpressure (2026-11-06)

Added Iap2CsmSendAdmissionModel to connect the source-audited chunk plan, link pending-command queue and one shared send deadline. It is a deterministic step model, not a blocking implementation:

- Attempts one chunk at a time using the queue's atomic capacity-check-and-enqueue operation.
- Returns WAITING with the remaining shared deadline when capacity is unavailable.
- Allows capacity to become available after the link pump removes a queued command, then resumes the same chunk without resetting the deadline.
- Checks immediate capacity before checking an expired deadline, preserving zero-timeout immediate admission.
- Returns CLOSED or TERMINAL_FAILURE when the queue closes or the pump terminates; terminal failure text is retained.
- If the deadline expires after partial chunk admission, records fail-closed state and does not restart or duplicate previously queued chunks.

The regression suite covers complete multi-chunk admission, queue-full wait and resume, partial admission followed by timeout, zero-timeout success/failure, close and terminal failure, and empty frames.

Actions run [#188](https://github.com/qiangge5586-a11y/1/actions/runs/42178672950) passed **8,400 Java 7 offline checks**, including 255 checks in the new admission suite. No blocking wait, live link pump, ACK delivery, USB/Bluetooth transport or vehicle/iPhone handshake is implemented or claimed by this model.


## Fixed-capacity link receive-buffer parsing (2026-11-06)

Refined Iap2LinkFrameDecoder to use a fixed 65,535-byte receive array and an append/drain/compact loop instead of allocating a concatenated pending-plus-input array on every read. The raw input read bound remains 8,192 bytes, matching the runPump read size. The parser drains complete frames before appending more bytes, retains incomplete candidates across reads, and compacts only the unconsumed suffix. If a full buffer cannot be consumed, it resets and fails rather than growing storage beyond the protocol maximum.

Added a boundary regression that feeds a maximum-size 65,535-byte link frame immediately followed by a header-only control frame, in 8,192-byte read chunks. Both frames must be emitted in order, the full payload must remain byte-identical, and pending bytes must return to zero.

Actions run [#191](https://github.com/qiangge5586-a11y/1/actions/runs/42178901878) passed **8,415 Java 7 offline checks**, including 1,065 link-frame decoder checks. This verifies bounded offline parser behavior only; it does not establish a live iAP2 session.


## Startup marker fragmentation and readiness-frame handoff (2026-11-06)

Extended the link-negotiation regression suite without changing the source-derived handshake state machine:

- The six-byte startup marker is now tested arriving one byte at a time. The responder remains in DETECTING until the final marker byte, then enters NEGOTIATING and emits its SYN.
- The ACK-bearing frame that completes negotiation is verified to be retained for the normal link engine, rather than being consumed only as a readiness signal.
- A combined ACK + Extended ACK frame is retained intact, including sequence, cumulative acknowledgement, control flags and payload, with its readiness-transition marker set.

Actions run [#192](https://github.com/qiangge5586-a11y/1/actions/runs/42179152487) passed **8,435 Java 7 offline checks**; negotiation suite increased from 158 to 178 checks. This validates split-read and state-handoff behavior in the offline model only, not a physical accessory/iPhone session.


## Exhaustive modulo-256 ACK and send-window verification (2026-11-06)

Expanded Iap2AckWindowPolicyTest with exhaustive finite-domain checks:

- For all 65,536 previous/candidate sequence pairs and peer ACK windows 0, 3, 245 and 255, compare cumulative ACK acceptance and outstanding-packet retention against the exact modulo-256 distance predicate, including the fixed +10 ACK slack and saturation at 255.
- For all 65,536 sent/previous-ACK pairs and maxOutgoing values 0, 1, 4, 127 and 255, verify the source's strict `>` queue admission and strict `<` queued-drain boundaries.

Actions run [#193](https://github.com/qiangge5586-a11y/1/actions/runs/42179259276) passed **1,188,083 Java 7 offline checks**, including 1,179,665 ACK/window policy checks. This is exhaustive validation of the model predicates over their 8-bit input domain; it does not prove live accessory interoperability.


## Link-readiness and peer-limit send gate (2026-11-06)

Added `Iap2CsmLinkGateModel` as an offline composition boundary around the existing CSM chunk-admission model. It refuses to create an admission plan while the link is negotiating, waits separately when the CSM peer control-payload limit is unknown, validates the known limit against 1..65,525, and reports a closed link without creating admission. This encodes the audited rule that CSM sending follows link readiness and uses the peer-advertised CSM chunk size. It does not perform CSM handshake, blocking waits, or transport writes.

Actions run [#214](https://github.com/qiangge5586-a11y/1/actions/runs/42183487239) passed **2,323,780 Java 7 offline checks** across 17 suites, including 16 new gate checks. This is an offline composition test, not a live CSM session.
