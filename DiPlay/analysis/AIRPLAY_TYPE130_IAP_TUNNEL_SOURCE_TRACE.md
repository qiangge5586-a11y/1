# AirPlay type-130 DataStream and encrypted IAP tunnel: source-level trace

Date: 2026-11-06
APK: DiPlay 0.3.11, SHA-256 `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`

## Critical terminology correction

The original implementation has three distinct layers:

1. **AirPlay DataStream SETUP negotiation**: `CarPlayMediaEngine.STREAM_TYPE_DATA = 130`. It is an AirPlay data-stream type, not itself an iAP2 packet or an iAP2 session.
2. **Encrypted IAP TCP tunnel**: the AirPlay SETUP handler recognizes `clientTypeUUID = E9459FD0-BCAD-4C45-820F-1E72447EF2F2`, derives a 32-byte read key from the AirPlay shared secret and SETUP seed, creates `IapTunnel`, binds a TCP listener, and returns a SETUP response containing `type=130`, `streamID=1`, and `dataPort`.
3. **iAP2 control session over the IAP tunnel byte stream**: the accepted TCP connection is decrypted and parsed; `AirPlayIapTunnelStream` exposes the recovered IAP bytes through `BlockingDuplexByteStream`. The registered handler calls `CarPlayController.startWirelessTunnelControl()`, which passes that byte stream to `Iap2Session.openTunnel(stream, "wireless-tunnel", ...)`. The iAP2 identification/MFi/control exchange then runs over this stream.

So the accurate relationship is: **AirPlay type-130 SETUP creates the encrypted IAP TCP carrier; the resulting IAP byte stream carries the separate iAP2 tunnel control session.** Do not label type 130 alone as the iAP2 tunnel handshake.

## Confirmed source path

### A. SETUP request to TCP listener

In `CarPlayMediaEngine.onDataStream(session, stream)`:

- Reads `clientTypeUUID`, uppercases it, and checks against `IAP_DATASTREAM_UUID`.
- Requires `session.getSharedSecret()` and a parseable unsigned decimal `seed`.
- Reads `streamConnectionID` for diagnostics and later response correlation (optional in the decompiled path).
- Derives the IAP tunnel read key:
  - HKDF-SHA512 input key material: AirPlay session shared secret.
  - Salt: ASCII bytes of `"DataStream-Salt" + seed`.
  - Info: ASCII bytes of `"DataStream-Output-Encryption-Key"`.
  - Output length: 32 bytes.
- Chooses the AirPlay session local address; if unavailable, falls back to wildcard address based on remote address family.
- Constructs `IapTunnel(key, localAddress)` and `AirPlayIapTunnelStream(session, tunnel)`.
- If an IAP tunnel handler is registered, calls `bridge.listen()`, logs bound address/port and stores a pending bridge for that AirPlay session.
- Returns SETUP response fields `type=130`, `streamID=1L`, `dataPort=boundPort`, and echoes normalized `streamConnectionID` if supplied.
- The bridge is indexed in `streams` under (session, type 130).

### B. TCP peer accept and encrypted frame processing

In `IapTunnel.listen()`:

- Binds a TCP ServerSocket on the selected local address.
- Attempts a second listener on the opposite IP family using the same port; failure is logged and the first listener remains.
- Starts daemon accept threads.
- `accept()` accepts a peer, enables `SO_LINGER(true, 0)`, stores the socket, resets the receive counter to zero, counts down peer-connected latch and calls listener `onOpen`.
- The source has a 15,000 ms peer-connect wait used by `AirPlayIapTunnelStream.send()`; this is separate from RFCOMM connect and iAP2 control deadlines.

In `IapTunnel.run()` and `decryptFrames()`:

- Reads TCP in chunks of 16,384 bytes.
- Accumulates incomplete encrypted frame bytes across socket reads.
- Frame prefix: 2-byte unsigned little-endian ciphertext length.
- Sealed payload length: ciphertext length + 16-byte Poly1305 authentication tag.
- Prefix bytes are passed as AEAD associated data.
- ChaCha20-Poly1305 key: the derived 32-byte read key.
- Nonce: 12 bytes; bytes 0..3 are zero and the 64-bit receive counter is encoded little-endian into bytes 4..11.
- Counter starts at zero for each accepted socket and increments once per successfully decrypted frame.
- Authentication/decryption is delegated to `AirPlayCrypto.chachaOpen`, with native/platform/BouncyCastle implementation fallback.

### C. Decrypted package parsing and IAP payload delivery

In `IapTunnel.parsePackages()`:

- Package header minimum: 32 bytes.
- Package total size: unsigned 32-bit big-endian at offset 0.
- Accepted size range: 32 through 4,194,304 bytes.
- Message type: unsigned 32-bit big-endian at offset 16.
- `0x636F6D6D` (`comm`) messages forward bytes [offset+32, offset+size) to `Listener.onIap()`.
- Unrecognized message types are skipped after consuming their complete package.
- Incomplete trailing package bytes are retained for the next decrypted input chunk.

In `AirPlayIapTunnelStream`:

- `onIap(bytes)` queues decrypted IAP bytes for the blocking duplex reader.
- Queue cap is 1,048,576 pending bytes; exceeding it marks a terminal IOException and closes the tunnel.
- `recv(maxBytes, timeout)` drains queued bytes and preserves partial chunk remainder.
- `send(data)` waits up to 15 seconds for peer TCP connection, then invokes AirPlay event-channel command with a 10-second timeout.
- Peer close/failure marks terminal failure and closes the parent AirPlay session.

### D. Handoff into iAP2 tunnel control

- `CarPlayController` registers `CarPlayMediaEngine.setIapTunnelHandler(...)` with a handler that calls `startWirelessTunnelControl(stream)`.
- That method checks current controller is open, transport is wireless, runtime identification and endpoint are present, and MFi client exists.
- It calls `Iap2Session.openTunnel(stream, "wireless-tunnel", ...)`, stores `wirelessTunnelChannel`, then runs `Iap2WirelessControlClient.run(...)` on the tunnel executor.
- The client performs its own iAP2 identification/MFi/control exchange. This is the point where the iAP2 session actually begins; it is downstream of AirPlay SETUP and TCP IAP tunnel establishment.

## Important review findings / risks

1. **Two-stage readiness must be visible separately.** AirPlay SETUP accepted, TCP listener bound, TCP peer connected, first authenticated frame received, first IAP comm package delivered, iAP2 identification complete, tunnel MFi authenticated and control active are distinct milestones. A SETUP response alone is not tunnel-ready.
2. **Receive counter is connection-scoped.** Source resets it on each accepted TCP peer. Reusing a counter across a replacement peer would break AEAD nonce alignment; resetting it on the same peer would also break alignment.
3. **Frame parser requires a hard bound.** The 16-bit length prefix limits a single frame. The offline frame accumulator added here rejects configured oversize values and retains partial frames.
4. **Package parser fail-closed policy must be explicit.** Original parser only loops while size is plausible and complete; if a complete 32-byte header declares size <32 or >4MiB, it returns the unchanged buffer as an incomplete tail. That can retain malformed plaintext indefinitely as more data arrives. The offline package parser added here rejects invalid size values instead of silently retaining them. This is a safer candidate policy, but it is not a byte-for-byte clone of original behavior and must be checked against captured valid traffic before integration.
5. **Queue backpressure is bounded but coarse.** The bridge caps pending IAP bytes at 1MiB and closes the tunnel on overflow. The caller should surface this as a distinct diagnostic rather than generic EOF.
6. **Potential accept-loop lifecycle deserves Smali verification.** JADX output shows accept loop plus per-socket run and connection counter reset. Because JADX reported 104 errors globally, inspect exact Smali around accept/run before changing multi-peer behavior.
7. **Send/receive crypto direction must not be conflated.** The inspected constructor derives the read key with `DataStream-Output-Encryption-Key`; outbound IAP send is routed back through the AirPlay session event command. Do not assume the same key/nonce direction for a future independent sender.
8. **No iAP2 packet framing was inferred from the outer AirPlay encrypted frame.** After decrypt, the `comm` package body is fed to `Iap2Session`; iAP2 frame sequencing/ACK/fragmentation is a separate layer to audit next.

## Offline code added

- `IapTunnelFrameDecoder.java`: bounded stream accumulator for the 2-byte LE frame prefix and sealed payload (ciphertext + tag). It does not perform cryptography or manage counters.
- `IapTunnelPackageDecoder.java`: bounded plaintext package parser for 32-byte package headers and `comm` type.
- `IapTunnelFramingTest.java`: fragmentation, coalescing, invalid size, and unknown package type tests.
- Workflow `.github/workflows/diplay-iap2-handshake.yml` now runs these tests alongside handshake lifecycle tests.

These are independent offline parsers only. They are not wired into original DiPlay, do not open sockets, and do not implement or bypass encryption/authentication.

## Next steps

1. Extract and inspect Smali for `CarPlayMediaEngine.onDataStream`, `IapTunnel.accept/run/decryptFrames/parsePackages`, and `AirPlayIapTunnelStream.offer/recv/send` to validate Kotlin/JADX control flow.
2. Add randomized fragmentation and malformed-input tests, including many concatenated packages and partial tail retention.
3. Audit the exact iAP2 framing/sequence/ACK layer after `onIap` enters `Iap2Session.openTunnel`.
4. Review the AirPlay event-channel outbound command and encryption direction separately.
5. Keep API 17 socket, crypto-provider and thread behavior as a separate runtime gate. No live vehicle connection has been attempted.
