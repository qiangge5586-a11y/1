# Manual device validation checklist

This checklist is required before claiming compatibility with any vehicle head unit. GitHub Actions validates compilation and unit tests only; it does not run the APK on a physical device.

## 1. Install on a spare Android device first

- Use a spare Android phone or bench head unit, not the vehicle's production head unit.
- Install the debug APK through Android's package installer and review the requested permissions.
- Confirm the app launches and the default capability report shows manufacturer/model, Android version/API, ABI and display profile. Enable detailed identifiers only when needed for a trusted diagnostic recipient.
- Export the compatibility report and keep a copy with the device test notes.

## 2. Exercise local features

- Select and preview a local video; close the preview and verify playback stops.
- Select and preview a local audio file; stop it manually and background the app to verify playback and audio focus are released.
- Import a valid vehicle snapshot JSON, then test invalid fields, fractional values, out-of-range values and a file larger than 64 KiB.
- Advance and reset the demo navigation route; confirm the UI labels it as demo data.
- Enable the foreground key monitor and press ordinary hardware keys; confirm events are displayed and normal key behavior remains intact.
- Test cache cleanup only after noting that temporary meter-preview share links may expire.

## 3. Factory head-unit compatibility gate

Do not install or sideload this APK onto the vehicle's factory screen unless the screen demonstrably runs a compatible Android OS and exposes the standard Android package installer. If it is a proprietary Honda infotainment platform, this APK cannot run directly on it. Do not enable ADB, root the unit, replace system files, install Xposed, or use undocumented OEM services to force compatibility.

## 4. Vehicle integration gate

Before enabling live telemetry or navigation state:

- Record exact head-unit manufacturer/model, Android build fingerprint, API level, ABI and screen profile.
- Identify a documented, authorized read-only interface for the exact hardware/firmware.
- Validate the interface on bench hardware while stationary.
- Confirm units, scaling, update frequency, stale-data behavior and disconnect handling.
- Keep mock mode available and do not send commands to vehicle systems.

## 5. Current validation status

No physical Android head unit, factory Civic screen, CAN/OBD adapter or live navigation bridge has been tested in this project. Mark these checks pending until real device evidence is available.
