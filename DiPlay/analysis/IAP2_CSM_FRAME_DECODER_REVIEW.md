# iAP2 CSM framing decoder review

Date: 2026-11-06

## Current offline implementation

`Iap2CsmFrameDecoder` is a bounded incremental parser for the repository's modeled CSM frame format:

- 2-byte sync marker: `0x4040` (big-endian)
- 2-byte total frame length (big-endian)
- 2-byte message ID (big-endian)
- payload follows at byte offset 6
- incomplete frames are retained between calls
- garbage and candidate headers with lengths below 6 are skipped during resynchronization
- maximum total frame length is 65,535 bytes
- each feed chunk is capped at 65,525 bytes, matching the original CSM `splitForLink()` maximum link chunk; `Iap2CsmChannel.recv()` passes reassembled link-control payloads to `Iap2CsmFramer.offer()`, not raw 8,192-byte socket reads

It is deliberately separate from the AirPlay encrypted IAP TCP frame parser and the 32-byte AirPlay IAP package parser. These three layers must not be conflated.

## Review observations

1. **Incremental behavior:** the decoder handles split headers, split payloads and multiple coalesced frames. Deterministic randomized fragmentation coverage has been added.
2. **Resynchronization:** invalid bytes and too-short candidate lengths are skipped one byte at a time. A plausible but incomplete 0x4040 candidate is retained until more bytes arrive; this is necessary for fragmented input but means false sync inside corrupted data can temporarily delay resynchronization.
3. **Memory bound:** each input chunk is limited to 65,525 bytes, matching `Iap2CsmFramer.MAX_LINK_CHUNK_BYTES`; the earlier 8,192-byte limit incorrectly applied the lower-level stream read size to already reassembled link-control payloads. Pending data remains limited to one maximum CSM frame. An oversized chunk clears pending state and fails closed. The decoder should be reset on stream-generation changes.
4. **Protocol evidence:** the current decoder is an offline compatibility model. Before wiring it into the original session path, verify the exact framing implementation in Smali and confirm that this 0x4040 format is the framing used at the specific CSM call site. Do not infer that every iAP2 transport layer uses this same frame header.
5. **Diagnostics:** the decoder now exposes cumulative `getDiscardedBytes()` and `getInvalidLengthCandidates()` counters so an adapter can distinguish ordinary fragmentation from resynchronization work. `reset()` clears both counters and pending bytes at a session-generation boundary.
6. **Semantics:** the decoder returns message ID and payload only. It does not implement sequence numbers, ACKs, retransmission, checksums, channel negotiation, message fragmentation semantics, or MFi authentication.
7. **Failure behavior:** the current parser silently discards garbage/short-length candidates instead of returning a structured resync diagnostic. A production adapter should expose discarded-byte counts and distinguish malformed stream from normal partial input.

## Tests

- Existing deterministic tests cover fragmented/coalesced frames, every possible two-part split boundary, byte-at-a-time delivery, garbage resynchronization, invalid short lengths and incomplete tail retention.
- Randomized test covers 100 frames with varying payload sizes, IDs and chunk boundaries.
- Resynchronization diagnostics are checked against a malformed short-length candidate followed by a valid frame; reset clears diagnostics.
- Boundary tests accept a 65,525-byte link-control chunk, reject 65,526 bytes, and decode a maximum 65,535-byte CSM frame in bounded chunks.
- CI workflow: `.github/workflows/diplay-iap2-handshake.yml`.

## Next gate

Inspect the exact original Smali call path from `Iap2Session.openTunnel` and `Iap2CsmChannel` to the frame reader/writer. Confirm sync, endian, length semantics, checksum/sequence fields, ACK behavior and whether this decoder belongs in that path. Only then should a transport adapter be implemented.


## Source call-path correction (2026-11-06)

The recovered original Smali confirms the CSM input path: `Iap2CsmChannel` calls `Iap2LinkChannel.recvControl()`, then passes the returned control payload to `Iap2CsmFramer.offer()`. The original framer's `offer()` drains a 65,535-byte receive buffer while copying arbitrarily sized input chunks into available capacity. Outbound `splitForLink()` explicitly caps each link chunk at 65,525 bytes. Therefore 8,192 is only the raw stream read size in `Iap2LinkChannel.runPump()`; it is not the CSM framer's input limit.

The offline decoder's defensive per-call cap is corrected to 65,525 bytes, derived from the original link-chunk maximum. This preserves bounded input while accepting the largest valid link-control chunk. It is a harness guard rather than a claim that the original `offer()` rejects larger caller arrays.


## CI result after correcting the input-layer boundary

Actions run [#163](https://github.com/qiangge5586-a11y/1/actions/runs/42175105245) passed on commit `94df7289bd468f97b1e9d43ff43cc4c3c222df14`. The complete offline suite passed **7,393 checks**, including 2,330 CSM decoder checks. This confirms the revised 65,525-byte per-call guard and existing maximum 65,535-byte frame coverage in the Java 7 offline harness. It does not validate a live iPhone or vehicle transport.
