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
