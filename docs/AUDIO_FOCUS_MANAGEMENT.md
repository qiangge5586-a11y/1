# Phase 18 — Explicit audio-focus management

## Implemented
- Added `AudioFocusController` using Android public AudioManager APIs.
- Android 8.0+ uses AudioFocusRequest with navigation-guidance audio attributes and transient-may-duck gain.
- Android 6.0–7.x uses the legacy requestAudioFocus overload.
- UI requires an explicit opt-in checkbox and a user tap before requesting focus.
- UI reports grant/denial and focus-change callbacks.
- Focus can be released manually and is abandoned when the Activity stops.
- No audio is played and system volume is not modified.

## Safety and scope
- This is an audio-focus lifecycle demo, not a complete media/navigation audio mixer.
- No hidden audio routing, vendor API, background playback or microphone access is used.
- The user must explicitly opt in each time the activity is used.

## Verification
- Phase 17 CI run #40 completed successfully: https://github.com/qiangge5586-a11y/1/actions/runs/41759036834
- Phase 18 source commits: AudioFocusController and MainActivity UI.
- CI run #41/#42 is validating the new source; do not mark Phase 18 build-verified until the latest run completes successfully.
