# iAP2 negotiation source verification — run 8

Date: 2026-11-06
Original APK SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`

## Recovered source artifact

- Workflow: [decompile run #8](https://github.com/qiangge5586-a11y/1/actions/runs/42166522659)
- Artifact: `diplay-0.3.11-decompiled`, ID `13479216859`
- Artifact ZIP SHA-256: `232b6cc66c22cd4f2815dc75a6978a46398e3cac8a46f6bd2cd621b3237299dc`
- APKTool Smali root: `decompiled/apktool/smali_classes4/com/shilapi/xcertplay/transport/`
- The artifact was downloaded and its ZIP contents inspected. The focused bundle includes `Iap2LinkEngine.smali`, `Iap2LinkChannel.smali`, `Iap2LinkEngine$Companion.smali`, `Iap2LinkEngine$SynchronizationPayload$Companion.smali`, and the related Header/Packet/ReceiveBuffer classes.

## Confirmed method locations

Line numbers refer to the APKTool Smali file inside this artifact:

| Method | Smali line |
|---|---:|
| `enterNegotiating` | 632 |
| `handleAcknowledgement` | 677 |
| `handleData` | 963 |
| `handleExtendedAcknowledgement` | 1444 |
| `handleSynchronization` | 1658 |
| `localSynchronization` | 1698 |
| `parseAvailable` | 1851 |
| `peerLimitsAreUsable` | 2169 |
| `peerPayloadIsAcceptable` | 2194 |
| `processFrame` | 2230 |
| `retransmitDuePacket` | 2432 |
| `sendAcknowledgement` | 2873 |
| `sendExtendedAcknowledgement` | 2906 |
| `sendPacket` | 3025 |
| `sendSynchronization` | 3205 |
| `writePacket` | 3257 |
| `advanceTime` | 3414 |
| `feed` | 3785 |
| `feedEof` | 3913 |
| `initiateSynchronizationIfDetecting` | 3925 |
| `nextDeadlineMillis` | 3970 |
| `start` | 4291 |

## Important readiness-gate correction

The current offline `Iap2LinkNegotiationModel` must mirror the following original Smali behavior:

1. `processFrame` calls `handleSynchronization` for a decodable SYN frame, then independently processes the ACK bit if present.
2. `handleSynchronization` stores the peer synchronization payload, marks peer sync received, records the peer sequence as `lastReceivedInOrder`, and sends an ACK.
3. When `handleAcknowledgement` is called while state is NEGOTIATING, it checks:
   - peer synchronization has already been received;
   - peer `maxLength` is greater than 10;
   - every already queued packet payload fits the peer maximum (payload length <= maxLength - 10).
4. If any of those checks fail, the engine enters DEAD with `peer iAP2 maxLength cannot carry pending control packets`.
5. If those checks pass, the engine enters NORMAL, clears the SYN deadline, and publishes writable.
6. The readiness gate does **not** compare the received ACK number with local SYN sequence 99. The ACK number is then consumed by the separate cumulative-ACK queue/window logic.

Therefore, the prior offline-model assumptions “only ACK=99 opens readiness”, “ACK before peer sync is accepted until later peer sync”, and “maxLength <= 10 merely leaves the model negotiating” are not source-equivalent. Tests must assert:
- an ACK with any u8 acknowledgement value after a valid peer SYN can open NORMAL (assuming no queued oversized payload);
- an ACK before peer SYN is terminal;
- an ACK after peer SYN with maxLength <= 10 is terminal.

The current simplified model does not yet model queued packets, cumulative ACK windows, delayed ACK deadlines, extended ACK retransmit selection, or out-of-order data. Do not imply that it does.

## Synchronization frame checksum correction

The wireless sync payload is:

```
01 04 FF FF 0F A0 01 F4 04 03 0A 00 02 0B 02 01 0C 01 02
```

Its payload checksum is `29` (the unsigned-byte sum including checksum is zero modulo 256). Thus the complete 29-byte synchronization frame is:

```
FF 5A 00 1D 80 63 00 00 A7
01 04 FF FF 0F A0 01 F4 04 03 0A 00 02 0B 02 01 0C 01 02 29
```

Use `29`, not `BA`, as the trailing payload checksum in documentation and fixtures.

## Current CI state at audit time

- iAP2 workflow run #58: [42168368167](https://github.com/qiangge5586-a11y/1/actions/runs/42168368167), success.
- The run compiled Java 7 and passed the offline test workflow.
- Offline tests are not a real iPhone, Bluetooth, USB, encrypted tunnel, or MY16ADA head-unit handshake.

## Safe next implementation boundary

The next full model increment should port the source-backed cumulative ACK window and queue/retransmission behavior from `handleAcknowledgement`, `sendPacket`, `retransmitDuePacket`, and `advanceTime`. Preserve exact retry semantics: with peer maxRetransmissions=4, the original transmission is followed by at most three retransmissions; the fourth due timeout enters DEAD. Extended ACK must retransmit only outstanding sequence numbers explicitly listed in its payload. Keep all work offline until separately authorized hardware validation.


## Readiness correction implementation and validation

The offline negotiation model was corrected to match the exact `processFrame` bit test: only frames with control bit `0x40` enter `handleAcknowledgement`; a SYN-only `0x80` frame does not. Within that ACK handler, the source readiness gate checks peer synchronization and usable peer limits but does not require ACK number 99. The tests now distinguish the peer SYN from a later ACK, accept ACK numbers 98 and 100 for the readiness transition, and verify terminal failure for ACK-before-SYN and unusable peer maxLength.

- Model correction commit: `473ab3df9596e160fdf576553978b63a8e050a10`
- Regression test commit: `f8485d186831828178dbb5192e719cccd2ca8ab6`
- Final validated iAP2 workflow: [run #75](https://github.com/qiangge5586-a11y/1/actions/runs/42168967851), commit `f00961b912f6fb652dc5e05c1b2ec756f315e5ab`, success.
- Checks: 61 handshake lifecycle + 3,380 encrypted IAP frame/package + 2,330 CSM + 64 link codec + 1,050 link decoder + 108 negotiation model + 20 retransmission policy = **7,013 offline checks**.

This is a Java 7 offline model validation only. It does not yet provide a full source-equivalent link engine: cumulative ACK queue/window processing, out-of-order packet delivery, delayed ACK scheduling, extended ACK selective retransmission, and retransmission timer integration remain separate work items.


## Control-bit re-audit correction (2026-11-06)

A line-by-line re-read of the recovered `Iap2LinkEngine.smali` `processFrame()` (source line 357 onward) supersedes the earlier composition note that described readiness as triggered by every non-zero control byte.

The actual source tests the bits independently:

- Reset: `control & 0x10` terminates the link.
- Synchronization: `control & 0x80` decodes the SYN payload and calls `handleSynchronization()`.
- Cumulative ACK / readiness: `control & 0x40` only. A SYN-only `0x80` frame records peer limits and emits the local ACK, but remains NEGOTIATING. An ACK-bearing frame (commonly a later `0x40` frame) runs the readiness gate.
- Extended ACK: `control & 0x20` with a non-null payload calls `handleExtendedAcknowledgement()`.
- Data: `(control & ~0x40) == 0` and non-null payload calls `handleData()`. Thus both `0x00` data-only and `0x40` data-plus-cumulative-ACK frames carry data. The latter runs both ACK and data handling.

The offline negotiation model now stays NEGOTIATING after a SYN-only frame and queues the ACK-bearing frame that completes readiness for composition dispatch. The composition model applies cumulative ACK only when bit `0x40` is set, handles Extended ACK independently, and accepts both `0x00` and `0x40` data frames.

This correction is based on the original Smali, not a protocol convention. Regression tests cover SYN-only versus ACK readiness, Extended ACK not being treated as cumulative ACK, data-only frames, and combined ACK/data frames.


## Control-bit correction regression result

Actions run [#133](https://github.com/qiangge5586-a11y/1/actions/runs/42173519353) passed **7,257 offline checks**, including 152 negotiation checks and 84 link-engine composition checks. The tests now explicitly verify that SYN-only `0x80` remains NEGOTIATING, ACK bit `0x40` is required for readiness, invalid or too-small peer SYN data is not treated as an ACK event, and the ACK-bearing frame that completes readiness is passed into the post-ready dispatcher. The composition tests cover `0x00` data-only and `0x40` data-plus-ACK frames, plus Extended ACK separation and ACK timer behavior.

This CI result is Java 7 offline-model verification only; it is not a live iPhone or MY16ADA handshake.
