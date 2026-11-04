# Foreground-only steering key mapping

## Original APK behavior observed

The original remapping screen exposes ten configurable key slots. Each slot can be assigned an installed app package or one of the built-in special actions. Captured steering-wheel input is collected through vendor logcat tags and privileged/Xposed callbacks, then mapped to app launches, media actions, meter, voice, road-information and other functions.

## Rebuild behavior

- Provides ten mapping slots.
- The user enables foreground mapping, taps Capture for a slot and presses a key delivered to this Activity by Android.
- The key code is saved in app-private preferences. Assigning a key already used by another slot clears the duplicate.
- System navigation, power and volume keys are rejected during capture.
- Mapped actions only navigate within this app: show meter editor, navigation app list, voice-assistant settings or road-information demo.
- Other key events pass through unchanged. The mapping feature is disabled by default and does not run in the background.

## Explicit limitations

- This does not read CAN, vendor logcat, private broadcasts, Xposed hooks or InputManager injection.
- A factory head unit may consume steering-wheel events before they reach third-party Activities. A captured Android key code does not prove the physical steering-wheel button is supported.
- No media key, volume, vehicle or external-app command is sent by the mapping handler.
- Test only while parked. Do not use the mapping as a driving-critical control.

## Validation

Unit tests cover ten slots, duplicate reassignment, invalid slots and rejection of system navigation/power/volume key codes. Actual steering-wheel event delivery requires physical head-unit testing.
