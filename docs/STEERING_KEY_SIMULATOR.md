# Steering Key Mapping Simulator (Phase 13)

Date: 2026-11-04

## Implemented

Added an offline steering-key mapping simulator to the main screen:
- Sample actions: Volume +, Next track, Show meter, Voice assistant.
- Tapping an action displays the simulated mapping result.
- The last selected demo action is stored in app-private `SharedPreferences`.
- The old static steering-key placeholder was removed to avoid duplicate UI.

## Explicit limitations

- It does not capture physical steering-wheel buttons or intercept Android key events.
- It does not send media, audio, vehicle-bus, or head-unit commands.
- Real mapping requires a documented input API exposed by the exact head-unit firmware and a separate compatibility test.

## Verification status

The change is committed in the Android source tree and is covered by the existing push-triggered GitHub Actions workflow. Build/test success for this revision is pending verification.

## Next

1. Confirm the latest CI run.
2. Add configurable labels/actions and reset for the simulator.
3. Begin navigation integration discovery without force-stopping or controlling third-party apps.
