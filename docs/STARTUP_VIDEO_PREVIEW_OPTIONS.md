# Startup video preview playback options

## Original APK settings observed

The original startup-video preference screen includes:
- Audio enabled (default true).
- Delay before playback (default 0 seconds; maximum 30 seconds).
- Maximum playback time (default 30 seconds; range 5–60 seconds).
- Fade-out at the playback limit (default true).

The original app also offers online video selection and searches local mounted storage paths. The rebuild does not reproduce unrestricted filesystem scanning, file deletion/copying or online video download.

## Rebuild behavior

- Added audio on/off, delay, maximum preview duration and fade-out controls to the existing SAF local-video picker.
- These options apply only after the user enables local preview and taps the preview button.
- The preview uses the selected document URI; it does not launch at boot or run in the background.
- Playback is capped at 60 seconds. When enabled and audio is on, the volume fades down over one second at the configured limit; otherwise playback pauses at the limit.
- Closing the dialog cancels pending callbacks and stops playback.
- Settings are stored locally in the app's private startup preferences.

## Validation

The options model clamps delay to 0–30 seconds and duration to 5–60 seconds. Unit tests cover normal values and out-of-range inputs. Device testing is still required for provider codecs, audio behavior and fade timing.
