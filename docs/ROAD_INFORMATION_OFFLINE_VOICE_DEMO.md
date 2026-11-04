# Road information and offline voice demo

## Original APK behavior observed

The original preference screen exposes:
- Enable road information (dependent on the custom meter).
- Play voice alerts for road information (dependent on road information).

The original runtime checks the Amap Auto package/version and app process state, exchanges navigation information through app-specific broadcasts/hooks, and plays bundled voice clips for road start, speed limits and camera alerts. It is not a generic public navigation API.

## Rebuild behavior

- Added matching local preference controls to the Civic demo editor.
- The displayed road and turn instruction are fixed sample data; no navigation app is queried or launched.
- Voice playback requires both checkboxes and an explicit user tap.
- Playback uses only an installed Android TextToSpeech voice that reports no network requirement. If no offline voice exists, the app refuses to use a network voice.
- TTS is stopped and released when the Activity is destroyed.
- No background announcements, Amap process monitoring, private broadcasts, bundled copyrighted audio, boot service or location tracking is added.

## Validation

Unit tests verify the sample road text and spoken instruction string. Actual speech quality and offline voice availability require device testing.
