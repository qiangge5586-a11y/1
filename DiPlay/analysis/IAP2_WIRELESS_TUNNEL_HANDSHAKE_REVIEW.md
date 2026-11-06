# iAP2 wireless tunnel establishment and handshake review

Date: 2026-11-06
Target: DiPlay 0.3.11 on Honda MY16ADA / Android 4.2.2 / API 17

## Scope and evidence boundary

This review is based on the repository's JADX/Smali-derived protocol map. The original JADX output reported 104 decompilation errors. The protocol map confirms call sites and message identifiers, but does not prove every wire-level ordering, retry timer, tunnel lifecycle callback, or behavior on a real iPhone. This document deliberately separates confirmed behavior from proposed engineering safeguards.

Confirmed high-level path:

1. RFCOMM iAP2 bootstrap stream is opened using the iPhone service UUID `00000000-deca-fade-deca-deafdecacafe`.
2. The bootstrap session performs iAP2 identification and MFi authentication.
3. The wireless control client subscribes to relevant control notifications and handles:
   - `0x4300` CarPlay Availability
   - `0x4301` CarPlay StartSession
   - `0x4E0D` Wireless CarPlay Update
   - `0x4E0E` Device Transport Identifier Notification
   - `0x5702` Request Accessory Wi-Fi Configuration
   - `0x5703` Accessory Wi-Fi Configuration
4. The source map records StartSession resend at 4-second intervals, up to 6 attempts, and RFCOMM connect wait defaulting to 10 seconds. Controller-level wireless attempts can reach 4.
5. A type-130 tunnel stream is passed to `Iap2Session.openTunnel(stream, "wireless-tunnel", ...)`; the tunnel control client then performs its own control/authentication work.
6. AirPlay media/network traffic is a separate Wi-Fi IP data plane, not the iAP2 control tunnel.

## Main reliability risks to address

### 1. Two sessions must not share one ambiguous lifecycle

The RFCOMM bootstrap and type-130 tunnel are separate streams. Their state, errors, close events and retry counters must be tracked separately. A successful RFCOMM connect is not proof of tunnel establishment; a received tunnel stream is not proof of tunnel authentication or active control.

### 2. Stale callbacks after reconnect

A previous RFCOMM reader or tunnel callback can arrive after a retry has already created a new session. Without a monotonically increasing session generation/token, an old callback can mutate the new session state. The offline tracker added in this change assigns a generation to each session and rejects callbacks with an older token.

### 3. Duplicate protocol notifications

Availability, Wi-Fi configuration and transport-identifier notifications may be repeated. The adapter should make state updates idempotent and avoid creating a second tunnel or starting duplicate worker threads for the same generation. The offline tracker reports duplicate milestones separately from invalid ordering.

### 4. Do not over-constrain unverified message ordering

The source map confirms message IDs and major call sites but does not fully prove a single mandatory order among Wi-Fi configuration, transport notification and type-130 tunnel delivery for every iPhone. The tracker therefore records these handoff milestones and exposes missing milestones diagnostically; it does not require all of them before accepting a tunnel stream. The confirmed hard gates in the offline model are:
- identification requires RFCOMM connected;
- bootstrap MFi authentication requires identification;
- tunnel stream reception requires authenticated bootstrap;
- tunnel open requires a received tunnel stream;
- tunnel authentication requires tunnel open;
- active tunnel control requires tunnel authentication.

This is a software lifecycle model, not an assertion about the byte-level iAP2 protocol.

### 5. Timeout ownership and retry amplification

Existing source-derived values:
- RFCOMM socket wait: 10 seconds by default.
- RFCOMM connection strategy: secure, insecure, then hidden-API fallback.
- controller wireless attempt count: up to 4.
- StartSession resend: 4 seconds, up to 6 attempts.

These independent retry loops can multiply total connection time and obscure the first failure. Proposed next step is to introduce one per-generation deadline budget with phase-specific diagnostic timestamps, while retaining protocol-required retransmission behavior. Do not simply shorten all timeouts: API 17 Bluetooth stack latency and iPhone response latency have not been measured on MY16ADA.

Recommended timeout model for future adapter integration:
- RFCOMM connect deadline: keep existing 10-second baseline until measured.
- Identification/MFi deadline: separate from socket connect; measure from first protocol byte.
- StartSession retry budget: preserve current 4-second / 6-attempt behavior until verified against packet traces.
- Type-130 stream arrival deadline: independent timer, not charged to RFCOMM socket connect.
- Tunnel authentication deadline: independent from tunnel stream arrival.
- Whole-session deadline: upper bound to prevent infinite retry loops; report which phase consumed it.

No new timeout values are hardcoded by this offline model.

### 6. Failure and cleanup ordering

On any terminal failure, the future adapter should:
1. mark the generation failed before closing streams;
2. stop accepting new callbacks for that generation;
3. close the tunnel stream/control client;
4. close the RFCOMM bootstrap stream/control client;
5. join reader/worker threads with bounded waits;
6. clear per-session retry counters and message state;
7. only then start a new generation.

Do not kill global Bluetooth services or change system hotspot configuration as part of handshake cleanup.

## Offline implementation added

- `DiPlay/compat/iap2/Iap2WirelessHandshake.java`
  - Java 7, no Android APIs, sockets, ROOT or packet I/O.
  - Tracks bootstrap, handoff and tunnel milestones independently.
  - Session generation token rejects stale callbacks after reconnect.
  - Duplicate events are idempotently reported.
  - Exposes a missing-handoff-milestone diagnostic string.
  - Does not enforce an unverified order between Wi-Fi handoff notifications and type-130 tunnel arrival.
- `DiPlay/compat/iap2/test/Iap2WirelessHandshakeTest.java`
  - Happy path, duplicate messages, invalid ordering, early tunnel arrival, stale callback rejection, and terminal failure/reset.
- `.github/workflows/diplay-iap2-handshake.yml`
  - Compiles with Java 7 source/target and runs offline tests.

This is a semantic session-state guard only. It is not yet connected to original DiPlay's iAP2 packet parser, Bluetooth socket, type-130 stream source, or CarPlay controller. It does not claim that iAP2 messages have been encoded or decoded.

## Next engineering gates

1. Recover the exact type-130 stream creation/dispatch path from Smali and verify its lifecycle against JADX output.
2. Trace iAP2 packet sequence/acknowledgement, message fragmentation/reassembly and channel close behavior; do not infer these from message IDs alone.
3. Add deterministic fixtures from legally captured protocol logs with sensitive pairing material removed.
4. Add a transport adapter interface that reports bootstrap and tunnel events with generation tokens.
5. Add fault-injection tests: delayed MFi response, duplicate availability, duplicate type-130 delivery, EOF during tunnel open, reconnect while callbacks are queued, and close races.
6. Only after offline tests pass, test on an API 17 bench device with a known-compatible iPhone; real vehicle testing remains a separate gate.

## Explicitly not done

- No real iAP2 RFCOMM connection or tunnel was opened.
- No iPhone pairing, MFi authentication, Wi-Fi handoff or AirPlay session was performed.
- No ROOT command or hotspot configuration write was executed.
- The diagnostic APK has not been changed to include or activate this tracker.
