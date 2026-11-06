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
- Latest combined CI run #35: 5,830 checks passed (61 handshake + 3,380 IAP framing/package + 2,330 CSM + 59 link-frame codec): https://github.com/qiangge5586-a11y/1/actions/runs/42167147358
- No cryptographic implementation was changed.
- No original DiPlay transport code was modified or wired to these new offline decoders.
- No live Bluetooth, Wi-Fi, AirPlay, iAP2 or vehicle session was attempted.


## Reproducibility note (2026-11-06)

The current repository tree does not preserve the APKTool Smali output or JADX source for `Iap2LinkEngine` / `Iap2LinkChannel`. The decompile workflow is configured to retain its artifact for 30 days, but no identifiable decompile run or artifact ID was found in the latest 100 workflow runs inspected for this continuation. The detailed link-layer values above are therefore prior audit findings, not raw source excerpts available for independent re-verification from the current checkout.

Before implementing synchronization, sequence/ACK, extended ACK, reordering, retransmission, peer-limit, ready-gate, or EOF behavior, restore the original Smali evidence and derive exact golden byte fixtures. See [IAP2_LINK_ENGINE_EVIDENCE_MATRIX.md](IAP2_LINK_ENGINE_EVIDENCE_MATRIX.md) for the source-to-test checklist and acceptance criteria. No guessed marker or ACK behavior should be implemented.


## Restored Smali artifact and packet primitive verification (2026-11-06)

The decompile artifact was located after expanding the workflow-run search beyond the first 200 records: run `42142476134` succeeded and produced artifact `13468029869` (`diplay-0.3.11-decompiled`). ZIP SHA-256: `fc047244952803306aa690e35c59fa31cbe3817eb5f10e5e6001e96bd8cfc8f5`. It includes APKTool Smali for the original link engine and its nested packet/header/synchronization classes. The earlier note saying no artifact was found is superseded by this recovery.

The original `writePacket()` and companion helpers confirm: detection preamble `FF 55 02 00 EE 10`; normal packet marker `FF 5A`; 9-byte header with BE total length, control, sequence, acknowledgement, session ID, and additive two's-complement header checksum; optional payload followed by its own additive checksum; u8 sequence wrap and modular distance. `SynchronizationPayload.encode()` confirms version, outgoing window, BE u16 max length and timeouts, retry/ack counts, and 3-byte session descriptors. Offline implementation is in `Iap2LinkFrameCodec.java`, with source-derived ACK-only and data-frame golden vectors in `Iap2LinkFrameCodecTest.java`.

This restores reproducibility for packet primitives, but not full state-machine conformance. ACK window advancement, extended-ACK missing-sequence list semantics, out-of-order handling, retransmission deadlines, and transition to NORMAL still require dedicated golden-vector and state-transition tests before any transport integration.


## Link-frame codec CI verification

The Java 7 offline workflow now compiles and runs `Iap2LinkFrameCodecTest`. CI run (`42164770173`, commit `df94e5f74d94bc8c98a75d9429a83fbedf3c040d`) succeeded. Results: handshake lifecycle 61 checks; AirPlay IAP frame/package 3,380; CSM decoder 2,330; link-frame codec 27. Total: 5,798 offline checks. This validates framing primitives only; link state transitions and a real iPhone/vehicle handshake remain unverified.


## Source-grounded link engine details

The original APKTool Smali was retrieved from successful decompile artifact run #7 (Actions run `42166504575`, artifact ID `13478968581`). The exact synchronization payload and link-frame behavior, ACK semantics, out-of-order receive handling, extended ACK sequence list, retransmission retry boundary, and wireless worker timers are documented in [IAP2_LINK_ENGINE_SOURCE_FINDINGS.md](IAP2_LINK_ENGINE_SOURCE_FINDINGS.md).

A source-derived wireless synchronization golden vector is now tested in `Iap2LinkWireCodecTest`. This closes the previous evidence gap for the local sync bytes, but it does not yet implement or validate the complete link state machine.


## Original Smali source recovery and corrections (2026-11-06)

The decompile artifact is now available from workflow run [#6](https://github.com/qiangge5586-a11y/1/actions/runs/42166126298), artifact ID `13478149398`. The original `Iap2LinkEngine`, companion, `Iap2LinkChannel`, and `Iap2LinkConfig` Smali files were recovered and checked. See [IAP2_LINK_ENGINE_SOURCE_AUDIT.md](IAP2_LINK_ENGINE_SOURCE_AUDIT.md) for exact wire fields, checksum rules, synchronization payload layout, ACK/reorder/retry behavior, and method provenance.

Corrections to the earlier audit:
- The startup marker `FF 55 02 00 EE 10` is now confirmed from the original Smali, not merely a candidate.
- Packet headers begin with a separate `FF 5A` marker.
- The wireless and wired synthetic constructor masks `494` and `486` are Kotlin default-argument masks, not maxOutgoing values. Both configs set actual maxOutgoing to 4. Wireless has zeroAcknowledgements=false; wired has zeroAcknowledgements=true.
- Wireless ACK timeout resolves to 500 ms from the config default. The wireless local sync payload advertises maxOutgoing=4, maxLength=65535, retransmit=4000 ms, ACK timeout=500 ms, max retransmissions=4, max acknowledgements=3, and session descriptors (10,0,2), (11,2,1), (12,1,2).

The compatibility implementation remains offline-only. The codec golden vectors are source-derived; full link state-machine parity and real device interoperability are not yet claimed.


## CSM offer() input boundary correction (2026-11-06)

Recovered Smali call-site verification: `Iap2CsmChannel` obtains control payloads from `Iap2LinkChannel.recvControl()` and passes them to `Iap2CsmFramer.offer()`. The 8,192-byte limit belongs to the lower-level `Iap2LinkChannel.runPump()` stream read, before link-layer packet reassembly. It must not be imposed on the CSM offer input. The original `splitForLink()` caps each outbound link chunk at 65,525 bytes; the offline CSM decoder now uses that source-derived value as its per-call defensive bound and tests 65,525 accepted / 65,526 rejected.
