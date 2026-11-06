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

The offline `Iap2CsmFrameDecoder` uses 65,525 as a defensive maximum for one `feed()` call. This is a harness guard aligned with the maximum source link chunk, not a claim that the original `offer()` rejects a larger input array. The previous 8,192-byte cap was incorrect because it confused raw stream reads with reassembled link-control payloads; it has been removed.

## CI

Actions run [#163](https://github.com/qiangge5586-a11y/1/actions/runs/42175105245) passed **7,393 offline checks**, including 2,330 CSM decoder checks. Java 7 offline verification only; no live Bluetooth, USB, TCP, AirPlay, iPhone, MFi or MY16ADA session is claimed.
