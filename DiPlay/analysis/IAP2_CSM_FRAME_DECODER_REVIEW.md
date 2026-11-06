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
- each feed chunk is capped at 8,192 bytes, matching the original `Iap2LinkChannel.runPump()` receive call; oversized input resets pending state and fails closed

It is deliberately separate from the AirPlay encrypted IAP TCP frame parser and the 32-byte AirPlay IAP package parser. These three layers must not be conflated.

## Review observations

1. **Incremental behavior:** the decoder handles split headers, split payloads and multiple coalesced frames. Deterministic randomized fragmentation coverage has been added.
2. **Resynchronization:** invalid bytes and too-short candidate lengths are skipped one byte at a time. A plausible but incomplete 0x4040 candidate is retained until more bytes arrive; this is necessary for fragmented input but means false sync inside corrupted data can temporarily delay resynchronization.
3. **Memory bound:** each input chunk is limited to 8,192 bytes, matching the source `recv(8192, waitMillis)` call; pending data is limited to one maximum CSM frame. An oversized chunk clears pending state and fails closed. The decoder should be reset on stream-generation changes.
4. **Protocol evidence:** the current decoder is an offline compatibility model. Before wiring it into the original session path, verify the exact framing implementation in Smali and confirm that this 0x4040 format is the framing used at the specific CSM call site. Do not infer that every iAP2 transport layer uses this same frame header.
5. **Semantics:** the decoder returns message ID and payload only. It does not implement sequence numbers, ACKs, retransmission, checksums, channel negotiation, message fragmentation semantics, or MFi authentication.
6. **Failure behavior:** the current parser silently discards garbage/short-length candidates instead of returning a structured resync diagnostic. A production adapter should expose discarded-byte counts and distinguish malformed stream from normal partial input.

## Tests

- Existing deterministic tests cover fragmented/coalesced frames, garbage resynchronization, invalid short lengths and incomplete tail retention.
- Randomized test covers 100 frames with varying payload sizes, IDs and chunk boundaries.
- Boundary tests reject 8,193-byte input chunks and decode a maximum 65,535-byte CSM frame in bounded 8 KiB reads.
- CI workflow: `.github/workflows/diplay-iap2-handshake.yml`.

## Next gate

Inspect the exact original Smali call path from `Iap2Session.openTunnel` and `Iap2CsmChannel` to the frame reader/writer. Confirm sync, endian, length semantics, checksum/sequence fields, ACK behavior and whether this decoder belongs in that path. Only then should a transport adapter be implemented.
