# Startup preferences

## Scope

Phase 19 adds a local, opt-in startup navigation preference to the independent rebuild.

- A checkbox controls whether the app scrolls to the offline multi-widget meter editor after launch.
- The setting is stored in the app's private SharedPreferences.
- Default is disabled.
- The app does not register a boot receiver, launch itself at device boot, request extra permissions, or start third-party apps.
- This is an in-app landing-position preference, not an Android boot/autostart feature.

## Validation

The change is covered by the repository Android CI workflow, which runs unit tests and assembles a debug APK. Device-level behavior still requires manual testing on a spare Android device. It has not been tested on the user's Civic factory head unit.

## Future work

A user-selected startup video and playback preview may be added separately with the Storage Access Framework and explicit user action. No automatic playback or background service is part of this phase.
