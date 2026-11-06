# DiPlay 0.3.11 — Honda head-unit adaptation

## Original APK

- Repository path: `diplay0.3.11.apk` (repository root)
- SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`
- JADX produced Java-like output with 104 reported decompilation errors; APKTool decoded resources, Manifest and Smali. Do not treat JADX output as lossless source.

## Verified Honda head-unit baseline

Source: Library document `思域报告(1).pdf`.

- ROOT: obtained; report records `su -c id` returning UID 0.
- Android 4.2.2 / API 17; build `1.F1A4.65`.
- Fingerprint: `Honda/Andromeda/vcm30t30:4.2.2/1.F1A4.65/138:user/release-keys`.
- Device: `MY16ADA`; manufacturer Honda / MITSUBISHI ELECTRIC.
- Board/platform: Andromeda / `vcm30t30`.
- CPU: ARMv7, 4 cores, 900 MHz; ABI `armeabi-v7a, armeabi`.
- Display: 800 × 480, 160 dpi, landscape.
- RAM: 0.94 GB total; 0.27 GB available at report time.
- Wi-Fi: reported available. AP mode, interface, driver and hostapd availability remain unverified.

## Current isolated compatibility app

A dedicated project now exists at `DiPlay/android/`, separate from the root `android/` HondaHackRebuild project.

- Application ID: `com.shilapi.xcertplay.compat` (does not replace the original DiPlay package).
- minSdk 17, targetSdk 28, Java 7 source compatibility.
- Chinese landscape diagnostic dashboard with report baseline clearly distinguished from live measurements.
- USB Host read-only interface/endpoint descriptor enumeration with offline CDC-NCM shape matching; API 17 alternate-setting metadata is marked unknown.
- Bluetooth adapter read-only status check.
- Offline Java 7 NTB16 codec and incremental stream decoder, with 40 synthetic tests total (no USB I/O).
- Hotspot safety transaction models and simulation tests are included; no real ROOT executor or system-write path is enabled.

Latest successful APK build containing USB and Bluetooth read-only modules, the offline NTB16 codec, the incremental NTB16 stream decoder, the pure-Java CDC-NCM interface selector, API-17-safe passive USB interface/endpoint snapshots, selected endpoint address/max-packet metadata, and the corrected Chinese Bluetooth status display: [GitHub Actions run #73](https://github.com/qiangge5586-a11y/1/actions/runs/42156700384).

Artifact: `diplay-honda-compat-debug`, ID `13475042380`, archive size 36,332 bytes; archive digest `sha256:2822fff66d27da8b0252016cd7791bc636392da54c71a24af184094361c6bbda` (expires 2026-11-20). Downloaded archive inspection confirmed it contains `app-debug.apk` (40,043 bytes); APK SHA-256: `eb08ffd825cd62626737fd3f1e412d549a13836979e20909bbf35074bd3c4e93`.

The matching descriptor/NCM test workflow [run #91](https://github.com/qiangge5586-a11y/1/actions/runs/42156700292) passed: 14 NTB16 codec cases, 11 stream-decoder cases, 9 CDC-NCM interface-selector cases, and 6 USB-to-NCM snapshot matcher cases (40 tests total across NCM/USB). The matcher preserves endpoint addresses and maximum packet sizes for future transfer planning.
The build verifies compilation, APK packaging, application ID, minSdk 17 and targetSdk 28. It is not a full DiPlay 0.3.11 recreation and has not yet been installed or tested on the Honda head unit.



## Previous CI build record (2026-11-06, run #25)

The latest Android workflow completed successfully, including Gradle assembly, AAPT metadata verification and artifact upload:
https://github.com/qiangge5586-a11y/1/actions/runs/42149522090

Artifact: `diplay-honda-compat-debug` (artifact ID `13471009807`, 26,585 bytes; expires 2026-11-20). Archive digest: `sha256:035090779625eff20ee4cbc8e01eeda8d2cf0e568f71e4cbddf6e8b1464908bb`.

## iAP2 wireless tunnel handshake work (2026-11-06)

An offline Java 7 handshake lifecycle tracker and test workflow have been added. A deeper source trace distinguishes AirPlay type-130 DataStream SETUP, its encrypted IAP TCP carrier, and the iAP2 tunnel session carried inside it; see the type-130/IAP source trace. Offline frame, package and CSM decoders with tests have also been added. The link-layer audit documents synchronization, sequence/ACK, retransmission and CSM readiness gates. It separates RFCOMM bootstrap milestones from the later type-130 tunnel stream and tunnel authentication, rejects stale callbacks using a per-session generation token, treats duplicate events idempotently, and reports missing handoff milestones without imposing an unverified ordering between Wi-Fi configuration notifications and tunnel arrival. This is a semantic state model only: it is not connected to the original packet parser or Bluetooth/tunnel I/O, and no real iPhone or vehicle handshake has been performed. The latest iAP2 CI run [#35](https://github.com/qiangge5586-a11y/1/actions/runs/42167147358) passed 5,830 offline checks: 61 handshake lifecycle, 3,380 encrypted IAP frame/package, 2,330 CSM frame, and 59 link-frame codec checks. The original APK's Smali was recovered from the decompile workflow artifact; source-grounded findings now document the wireless synchronization payload, exact 29-byte sync frame, frame checksums, ACK/sequence behavior, extended ACK missing-sequence list, out-of-order handling, retransmission boundary, and wireless fallback timing. This still does not model the complete link state machine or prove a real iPhone/vehicle handshake. See the [handshake review](analysis/IAP2_WIRELESS_TUNNEL_HANDSHAKE_REVIEW.md) and [CSM frame decoder review](analysis/IAP2_CSM_FRAME_DECODER_REVIEW.md).

## GitHub Actions and project records

- [DiPlay Android build workflow](../.github/workflows/diplay-android-build.yml)
- [Chinese static analysis report](analysis/DiPlay-0.3.11-静态分析报告.md)
- [API 17 compatibility audit](analysis/API17_COMPATIBILITY_AUDIT.md)
- [Read-only Honda probe](tools/honda-readonly-probe.sh)
- [Native ELF audit script](tools/analyze-native-libs.sh)
- [ROOT hotspot safety redesign](analysis/ROOT_HOTSPOT_SAFETY_REDESIGN.md)
- [Automated hotspot safety scanner](tools/audit-hotspot-safety.py)
- [Bluetooth passive status probe](compat/bluetooth/README.md)
- [USB read-only scanner](compat/usb/README.md)
- [Offline NCM/NTB16 codec](compat/ncm/README.md)
- [Protocol path map](analysis/DiPlay_PROTOCOL_PATH_MAP.md)
- [iAP2 wireless tunnel and handshake review](analysis/IAP2_WIRELESS_TUNNEL_HANDSHAKE_REVIEW.md)
- [AirPlay type-130 SETUP and encrypted IAP TCP tunnel source trace](analysis/AIRPLAY_TYPE130_IAP_TUNNEL_SOURCE_TRACE.md)
- [iAP2 link-layer and CSM framing audit](analysis/IAP2_LINK_AND_CSM_FRAMING_AUDIT.md)
- [Source-grounded iAP2 link engine findings](analysis/IAP2_LINK_ENGINE_SOURCE_FINDINGS.md)
- [iAP2 link evidence matrix](analysis/IAP2_LINK_ENGINE_EVIDENCE_MATRIX.md)
- [iAP2 negotiation model source verification](analysis/IAP2_NEGOTIATION_MODEL_SOURCE_VERIFICATION.md)
- [iAP2 CSM frame decoder review](analysis/IAP2_CSM_FRAME_DECODER_REVIEW.md)
- [Offline iAP2 wireless handshake state tracker](compat/iap2/Iap2WirelessHandshake.java)
- [iAP2 handshake self-test workflow](../.github/workflows/diplay-iap2-handshake.yml)
- [Original NCM/NTB16 codec comparison](analysis/NCM_NTB16_CODEC_COMPARISON.md)
- [CDC-NCM interface selection policy review](analysis/NCM_INTERFACE_SELECTION_POLICY.md)
- [API 17 legacy USB claim, raw usbfs and VPN/lwIP review](analysis/API17_LEGACY_USB_NCM_AND_VPN_REVIEW.md)

## Safety and remaining gates

- Keep the ROOT hotspot backend as a project goal; do not remove it.
- The current app does not call `su`, start/stop hostapd or dnsmasq, or modify Wi-Fi/system configuration.
- ROOT hotspot changes must remain transactional: read state, create and verify backup, apply only after explicit user authorization, and rollback on failure.
- Do not assume `wlan0`, `nl80211`, `hostapd`, or `dnsmasq` exist until the actual Honda firmware is probed.
- USB and Bluetooth checks are passive only; neither has been verified on the MY16ADA hardware.
- Eight of nine original native libraries carry an Android NDK API 19 platform note. API 17 loading must be tested; do not alter ELF notes to disguise incompatibility.
- Remaining work: API 17 runtime test, native library loading checks, Bluetooth/USB hardware verification, original DiPlay protocol path porting, CarPlay/NCM integration and vehicle testing.

A successful CI build is not proof of runtime or vehicle compatibility. Do not treat this prototype as a vehicle-ready replacement for the original app.


### iAP2 link-frame offline codec

The source-audited link packet primitive is implemented in `compat/iap2/Iap2LinkFrameCodec.java`. It encodes/validates the 9-byte `FF 5A` header, additive header/payload checksums, u8 sequence wrap, and modular sequence distance. Golden vectors are in `compat/iap2/test/Iap2LinkFrameCodecTest.java`. CI run `42167147358` passed 59 link-frame codec checks and 5,830 total offline checks, including the exact source-derived wireless synchronization frame. This is an offline codec test only; it does not establish a live iPhone or vehicle handshake.


### iAP2 link-layer wire codec (source-audited)

- Restored the original DiPlay 0.3.11 decompile artifact from Actions run [42166126298](https://github.com/qiangge5586-a11y/1/actions/runs/42166126298), artifact ID `13478149398`; original APK SHA-256 was verified by the workflow.
- Added `compat/iap2/Iap2LinkFrameCodec.java` with the original link marker/header layout, additive checksums, synchronization-payload layout, and 8-bit sequence arithmetic. It is offline-only and does not open transports.
- Added golden-vector tests for the synchronization payload, ACK-only frame, data frame, checksums, bounds, and sequence wrap.
- Latest CI run [#46](https://github.com/qiangge5586-a11y/1/actions/runs/42167041306) passed: 61 handshake + 3,380 IAP framing/package + 2,330 CSM + 49 link codec checks = **5,820 offline checks**.
- This does not yet implement the full link state machine or establish an actual iPhone/vehicle session. See `analysis/IAP2_LINK_ENGINE_EVIDENCE_MATRIX.md` for the source map and remaining engine-state work.


### iAP2 source recovery and current link-layer milestone (2026-11-06)

The original DiPlay 0.3.11 decompile artifact was recovered from [Actions run #6](https://github.com/qiangge5586-a11y/1/actions/runs/42166126298), and the original Smali for `Iap2LinkEngine`, `Iap2LinkChannel`, and related classes was inspected. The source audit now distinguishes the six-byte startup marker `FF 55 02 00 EE 10` from the packet marker `FF 5A`, confirms the 9-byte header and independent additive checksums, and corrects the wireless configuration: maxOutgoing=4, ACK timeout=500 ms, retransmit timeout=4,000 ms, max retries=4, max ACKs=3. The values 494/486 in the Kotlin constructor are default-argument masks, not link-window sizes.

New offline components:
- `compat/iap2/Iap2LinkFrameCodec.java`: source-derived packet and synchronization-payload codec.
- `compat/iap2/Iap2LinkFrameDecoder.java`: bounded incremental link-frame decoder.
- `compat/iap2/Iap2LinkNegotiationModel.java`: partial startup/SYN/ACK negotiation model and peer readiness gate.
- `compat/iap2/Iap2ReceiveWindowModel.java`: source-derived receive ordering, out-of-order buffering, extended-ACK missing sequence list, duplicate handling, and delayed/forced ACK scheduling.
- `compat/iap2/Iap2AckWindowPolicy.java`: exact modulo-256 cumulative-ACK removal and strict send/queue-drain window predicates.
- `compat/iap2/Iap2OutboundWindowModel.java`: offline composition of send sequencing, outbound queueing, cumulative ACK release, per-packet deadlines and timeout/Extended-ACK retransmission.
- `analysis/IAP2_LINK_ENGINE_SOURCE_AUDIT.md`: original Smali source audit.
- `analysis/IAP2_LINK_ENGINE_EVIDENCE_MATRIX.md`: source-to-test checklist and remaining gaps.

Latest CI [run #75](https://github.com/qiangge5586-a11y/1/actions/runs/42168967851) passed **7,013 offline checks** (61 handshake lifecycle, 3,380 IAP framing/package, 2,330 CSM, 64 link codec, 1,050 link decoder, 108 negotiation model, 20 retransmission policy). The readiness model follows the original `processFrame()` bit test: only a frame carrying ACK bit `0x40` invokes ACK processing; a SYN-only `0x80` frame does not. Within the ACK handler, the ACK number need not equal 99, but peer synchronization must already exist and peer maxLength must be greater than 10; otherwise the engine terminates. The retransmission policy helper encodes the source behavior that maxRetries=4 permits three retransmissions and terminates on the fourth increment; extended-ACK missing-sequence requests use the same per-packet counter. Outbound queue, ACK release and retransmission behavior now have a deterministic offline state model. Remaining work is source-golden comparison of deadline re-arming/multiple due packets, concurrency-safe integration with the real link pump, and transport/hardware validation. This is not proof of a real iPhone or MY16ADA vehicle handshake; no live Bluetooth, USB, AirPlay, or ROOT action was performed.


### iAP2 negotiation readiness correction (2026-11-06)

A fresh inspection of the recovered run #8 Smali corrected an offline-model readiness assumption: the original engine does not require ACK number 99 to transition out of NEGOTIATING. Only frames with the ACK control bit (`0x40`) invoke the gate; a SYN-only frame does not. Peer synchronization must already exist and peer maxLength must be greater than 10, otherwise the engine terminates. The simplified offline model and regression tests were aligned with that behavior. See `analysis/IAP2_NEGOTIATION_MODEL_SOURCE_VERIFICATION.md` for artifact provenance, Smali method locations, and the corrected 29-byte synchronization frame checksum (`29`). This remains offline-only; cumulative ACK windows and retransmission are not yet fully modeled.

### iAP2 link-engine composition model (2026-11-06)

Added an offline orchestration boundary in `compat/iap2/Iap2LinkEngineModel.java`. It gates data-window operations until the negotiation model reports READY, delegates outbound send/cumulative ACK/Extended ACK and inbound receive behavior to the already source-audited models, advances both timer models against one deterministic clock, and closes on EOF. The peer window parameters are still supplied by the caller: the current negotiation model does not yet expose all peer-advertised limits, so this composition layer does not claim automatic SYN-to-window wiring or a complete original-engine port.

CI run [#95](https://github.com/qiangge5586-a11y/1/actions/runs/42170649686) passed **7,145 offline checks**, including the new 16-check composition suite. The preceding run #94 failed because the first test fixture used an uninitialized reference; the fixture was corrected and run #95 passed. This is Java 7 offline verification only. No iPhone, Bluetooth, AirPlay tunnel, MFi authentication, Android 4.2.2 runtime, or Honda MY16ADA hardware test has been performed. ROOT hotspot writes remain disabled.

See [composition model notes](analysis/IAP2_LINK_ENGINE_COMPOSITION_MODEL.md), [source findings](analysis/IAP2_LINK_ENGINE_SOURCE_FINDINGS.md), and [evidence matrix](analysis/IAP2_LINK_ENGINE_EVIDENCE_MATRIX.md).
