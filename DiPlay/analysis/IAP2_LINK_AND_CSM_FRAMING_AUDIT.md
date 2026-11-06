# iAP2 link-layer and CSM framing audit

Date: 2026-11-06
Evidence: DiPlay 0.3.11 JADX sources plus Smali verification of `Iap2LinkChannel.runPump()`

## Protocol layering

The wireless control path is now traced across four layers:

1. AirPlay DataStream SETUP type 130 negotiates an IAP data-stream TCP listener and supplies the port to the iPhone.
2. The IAP TCP carrier decrypts AirPlay IAP frames and extracts `comm` package bodies.
3. `Iap2Session.openTunnel()` wraps the resulting byte stream in `Iap2CsmChannel` and `Iap2LinkChannel`.
4. `Iap2LinkEngine` negotiates the iAP2 link layer; once writable, CSM frames are split into peer-supported link payload chunks. The CSM layer then handles 0x4040 frames and dispatches message IDs/payloads to the wireless control client.

Do not mix the outer AirPlay ChaCha frame, IAP tunnel package, iAP2 link packet, and CSM frame. Each has its own header, integrity/sequence rules and buffer.

## iAP2 link engine findings

Source class: `transport/Iap2LinkEngine.java`. Constants and state were checked against implementation; the worker method `Iap2LinkChannel.runPump()` was checked in Smali because JADX failed to decompile it.

- Link marker bytes: `FF 55 02 00 EE 10`.
- Link packet header: 9 bytes; checksum: 1 byte.
- Maximum wire frame: 65,535 bytes; maximum payload: 65,525 bytes.
- Initial send sequence: 99.
- Control bits: synchronize `0x80`, acknowledgement `0x40`, extended acknowledgement `0x20`, reset `0x10`.
- Control session ID: 10; extended-ack session ID: 11; file-transfer session ID: 12.
- Engine states: IDLE, DETECTING, NEGOTIATING, NORMAL, DEAD.
- Synchronization version: 1; synchronization fixed payload: 10 bytes; session descriptor: 3 bytes.
- Synchronization resend interval: 500 ms; max synchronization attempts: 20.
- Marker resend interval: 1,000 ms.
- Default retransmission timeout: 4,000 ms; default maximum retransmissions: 4; default max acknowledgements: 3; ACK slack: 10.
- Wireless link config is instantiated with control-session version 2 and config maxOutgoing 4; wired config differs in zeroAcknowledgements flag and maxOutgoing value (wireless 494 vs wired 486 in the final config slot). These config values should be preserved, not copied into generic assumptions.

The engine has explicit handling for synchronization, normal/extended acknowledgements, out-of-order packets, retransmission deadlines, receive buffering, and peer-advertised limits. It is not merely a byte stream.

## Smali-confirmed worker behavior

`Iap2LinkChannel.runPump()` could not be reliably reconstructed by JADX (type inference overflow), so its critical loop was read from Smali:

- Creates `Iap2LinkEngine(linkConfig)`, starts it with the configured `initiateNegotiation` value.
- Drains queued outbound commands into the engine.
- For non-initiating wireless mode, if no bytes have arrived for 800 ms, calls `initiateSynchronizationIfDetecting()` once and reports "wireless iAP2 accessory sync fallback triggered" if it succeeds.
- If non-initiating wireless mode remains not ready and silent for 12,000 ms, terminates with "Bluetooth iAP2 silent peer timeout after 12000ms".
- Waits for the next engine deadline, capped at 100 ms and floored at 1 ms, then calls underlying `recv(8192, waitMillis)`.
- A zero-length/EOF result before link writable is terminal; otherwise it feeds EOF to the engine.
- Received bytes are passed to `engine.feed(bytes, nowMillis)`.
- Engine output is flushed to the underlying byte stream; engine events are drained into control/session queues.
- The worker reports periodic handshake progress with configured 10,000 ms interval.
- The worker owns the engine state; CSM uses bounded queues and calls into the link channel.

Important distinction: the 800 ms fallback and 12 s silent peer timeout apply only to the non-initiating wireless link branch. Do not apply these values to AirPlay SETUP, TCP peer accept, MFi or CSM application-level response timeouts.

## CSM framing findings

Source classes: `iap2/wire/Iap2CsmFramer.java`, `Iap2Frame.java`, `transport/Iap2CsmChannel.java`.

- CSM start marker: `0x4040`.
- Header length: 6 bytes.
- Header fields: start marker (u16 BE), total frame length (u16 BE), message ID (u16 BE), then payload.
- Minimum frame: 6 bytes; maximum frame: 65,535 bytes; maximum body: 65,529 bytes.
- Parser receive buffer: fixed 65,535 bytes.
- It searches for the 0x4040 marker, discarding one byte at a time while out of sync.
- If declared length is below 6, it discards one byte and resynchronizes.
- If the complete declared frame has not arrived, it retains bytes until the next input.
- Complete frames are delivered as `Iap2Frame(messageId,payload)`.
- Link chunk size is peer-advertised via synchronization; CSM sends split complete frames into chunks of at most that size, constrained to 1..65,525 bytes.
- `Iap2CsmChannel` default send timeout is 5 seconds; pending received frames are bounded to 64 frames / 1 MiB; link-level pending command/control queues are also bounded (64 entries / 1 MiB each).

This gives the real handshake gate: link must become ready and publish a valid peer maximum control payload before CSM `send()` is allowed. A successful TCP peer connect or successful IAP decrypt alone does not satisfy that gate.

## Engineering priorities from this audit

1. Add offline link-engine tests using the original implementation's wire fixtures or Smali-derived golden vectors. Do not reimplement link ACK/synchronization from assumptions.
2. Add CSM framer tests for byte-by-byte fragmentation, multiple coalesced frames, invalid marker, invalid short length, max-size frame, and resync.
3. Add end-to-end offline pipeline tests: encrypted-frame structure fixture -> plaintext IAP package -> IAP comm bytes -> CSM frame parser. Crypto itself must use known test vectors and must not be weakened.
4. Add phase timestamps for AirPlay SETUP, IAP TCP peer, first decrypted IAP package, link synchronization, link ready, CSM ready, iAP2 identification, MFi, and control active.
5. Preserve distinct timeout scopes: 15 s IAP peer wait; 800 ms wireless synchronization fallback; 12 s silent peer timeout; engine sync/retransmit timers; CSM 5 s send timeout; and application-specific iAP2 client deadlines.
6. Verify `Iap2LinkEngine` and CSM edge cases against Smali because the APK-wide JADX output has 104 errors.

## Verification status

- Offline handshake lifecycle test: 61 checks passed.
- AirPlay IAP encrypted-frame/package structural tests: 27 checks passed.
- CSM frame decoder tests: 2,330 checks passed, including every two-part split boundary, byte-at-a-time delivery, deterministic randomized fragmentation, max-size frames, and resynchronization diagnostics.
- Latest combined CI run #31: 5,771 checks passed (61 handshake + 3,380 IAP framing/package + 2,330 CSM): https://github.com/qiangge5586-a11y/1/actions/runs/42163360812
- No cryptographic implementation was changed.
- No original DiPlay transport code was modified or wired to these new offline decoders.
- No live Bluetooth, Wi-Fi, AirPlay, iAP2 or vehicle session was attempted.
