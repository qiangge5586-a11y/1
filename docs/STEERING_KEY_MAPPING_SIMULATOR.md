# Steering-key mapping — foreground-safe implementation

The original Phase 14 simulator has been superseded by the foreground-only mapping in Phase 49.

See `docs/FOREGROUND_STEERING_KEY_MAPPING.md` for the current ten-slot capture workflow, local in-app actions, safety limits and validation.

The old simulator did not capture Android key events. The current implementation captures only events delivered to the foreground Activity after explicit opt-in. It still does not use root, Xposed, vendor logcat, private broadcasts, CAN messages or global input injection.
