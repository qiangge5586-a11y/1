# Configurable Steering-Key Mapping Simulator (Phase 14)

Date: 2026-11-04  
Repository: `qiangge5586-a11y/1`

## Implemented

The earlier fixed sample buttons have been replaced with four configurable simulated steering keys.

Each simulated key now has:
- A selectable action: no action, volume up/down, next/previous track, play/pause, show meter, voice assistant, or open navigation.
- A per-key mapping stored in app-private `SharedPreferences`.
- A simulation button that displays the selected mapping.

## Explicit boundary

This is a configuration and UI simulator only. It does not capture steering-wheel CAN messages, Android key events from other apps, vendor broadcasts, or head-unit input. It does not issue media, navigation or vehicle commands. A real adapter must wait until the target Civic head unit model and its documented input API are identified.

## Verification

The source is committed. The CI workflow runs unit tests and assembles a debug APK on Android source pushes. Latest run result and generated artifact still need independent confirmation.
