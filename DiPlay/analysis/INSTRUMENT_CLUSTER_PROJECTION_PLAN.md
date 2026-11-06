# Honda instrument-cluster projection plan

## Requirement record

Add a persistent project requirement: implement navigation / driving-information projection to the instrument cluster for the user's 2019 Honda Civic (factory center screen). This is an additional feature track; it does not replace the existing iAP2 tunnel/handshake priority, Bluetooth recovery work, or the ROOT hotspot safety work.

## Current implementation in this commit

- Adds `ClusterProjectionController` under `DiPlay/compat/cluster`.
- Uses Android API 17 `DisplayManager` and `Presentation` to enumerate system-exposed presentation displays and render a navigation-style sample page on the first available external display.
- Adds explicit scan, start-demo, and stop controls to the DiPlay compatibility dashboard.
- Dismisses the presentation when the Activity is destroyed.
- Keeps the module isolated from CAN, OBD writes, ROOT, Wi-Fi, and vehicle configuration.

## Important boundary

This is an external-display output prototype, not proof of projection onto the factory instrument cluster. It is not full-screen mirroring of another app and currently uses static demo content. A successful build only proves compilation.

The 2019 Civic instrument cluster is a separate vehicle display subsystem unless the exact trim/market firmware exposes a documented video input or a supported data interface. Android's `Presentation` API can only target displays that Android enumerates as presentation displays. If the cluster is not exposed that way, this route cannot reach it.

Do not inject undocumented CAN frames, spoof vehicle speed, alter safety warnings, or write instrument-cluster configuration. Any future vehicle-bus integration must be read-only first, use a documented Honda interface, and keep speed/warning signals under OEM control.

## Execution sequence

1. **API-17 external display prototype** — completed in source; CI build pending. Confirm the module compiles against minSdk 17 and uses only APIs available on API 17.
2. **Head-unit capability check** — on the MY16ADA unit, tap “检测可投屏显示屏” and record the presentation-display count and display names. This is a read-only Android display enumeration.
3. **Physical output validation** — if a genuine secondary display is enumerated, connect only through the supported factory/external display path and verify the sample page. No assumption that the cluster is an HDMI/secondary display.
4. **OEM cluster interface research** — identify exact 2019 Civic market/trim cluster hardware and supported Honda documentation; distinguish video input, navigation metadata, and CAN/vehicle-bus interfaces.
5. **Live navigation provider** — connect only a documented, permission-appropriate navigation state provider; replace demo text after its lifecycle, update rate, and stale-data handling are tested.
6. **Vehicle validation** — verify visibility, latency, orientation, disconnect recovery, and safe fallback while stationary. Do not test while driving.

## Acceptance criteria

- Build compiles with minSdk 17 / Java 7.
- No external display found is reported honestly without implying cluster incompatibility.
- External display loss closes or safely dismisses the presentation.
- No ROOT command, CAN transmit, OBD command, Wi-Fi change, or vehicle configuration write occurs.
- Demo content is always labelled as demo until a live provider is verified.
- Real instrument-cluster projection is not marked complete until verified on the exact vehicle.
