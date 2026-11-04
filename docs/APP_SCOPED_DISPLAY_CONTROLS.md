# App-scoped display controls

## Controls

- **Keep screen awake**: optional and disabled by default. Adds `FLAG_KEEP_SCREEN_ON` only while this app's activity is visible; the flag is cleared when the activity stops and restored on return if the preference remains enabled.
- **Use system screen brightness**: default. When disabled, a slider applies a brightness override to this app's window only.
- **Restore system brightness**: removes the app-window override.

## Boundaries

- Does not change the device-wide brightness setting, write system settings, request overlay privileges, force orientation, or enable immersive/system UI hooks.
- Preferences are stored locally in the app.
- The user can always return to system brightness or turn off keep-awake.
