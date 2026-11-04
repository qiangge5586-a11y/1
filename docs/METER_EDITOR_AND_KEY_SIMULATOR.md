# Meter Editor and Key-Mapping Simulator (Phase 12)

Date: 2026-11-04  
Repository: `qiangge5586-a11y/1`

## Delivered in this phase

### Offline meter editor
- Loads the bundled `meter_presets.json` preset through `MeterPresetRepository`.
- Renders the preset's telemetry template using mock speed/RPM/fuel/coolant values.
- Provides a speed slider (0–240 km/h) for demonstration only.
- Provides a text-size slider (18–56 sp).
- Cycles among three built-in background themes.
- Cycles among three text colors.
- Persists speed, text size, background and text color using app-private `SharedPreferences`.
- Restores those preferences on next launch.

### Steering key mapping simulator
- Adds sample actions for volume+, next track, meter visibility and voice assistant.
- Stores the last simulated action in app-private preferences.
- Explicitly states that it does not intercept physical steering-wheel buttons or send commands to the vehicle.

## Verification and limitations

- The feature code and documentation are committed.
- The existing CI workflow now runs meter parser unit tests before building the debug APK.
- The Actions result for the latest editor commits has not yet been independently retrieved; this phase is not marked build-verified.
- The current editor is a first interactive prototype. It does not yet implement arbitrary multi-widget positioning, font families, strokes, shadows, background image selection, multiple preset management or export/import.
- All vehicle signals remain mock values. No CAN/OBD/head-unit access is implemented.

## Compatibility direction

The user's target vehicle is a 2019 Civic with the factory center display. Do not assume the original APK's Mitsubishi/Clarion vendor intents are supported by this vehicle. Before any integration, identify the exact head-unit model, firmware and documented public interface. The current app remains a regular Android app and does not require root or Xposed.

## Next engineering tasks

1. Confirm GitHub Actions unit-test and APK build status.
2. Add editable layout coordinates and a visual preview canvas.
3. Add preset save/rename/duplicate/import/export.
4. Continue readable decompilation of original high-value classes using JADX. Official JADX documentation notes that it supports APK/DEX-to-Java decompilation and resource decoding, but may not decompile every method perfectly: https://github.com/skylot/jadx
5. Start a device compatibility matrix for the Civic factory head unit, using only non-invasive, documented interfaces.
