# Explicit on-device voice recognition prototype

## Behavior

- The user must tap **Start on-device voice recognition**.
- The app checks for Android 12+ and an available on-device speech-recognition service.
- The microphone permission is requested only after the user taps Start.
- Partial and final recognition text is displayed in the app.
- The user can tap **Stop listening**; recognition is also cancelled and the recognizer destroyed when the activity stops.

## Privacy and safety boundaries

- Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` only; there is no cloud-recognition fallback.
- Does not start on boot, use a wake word, listen in the background, upload audio, or execute recognized text as a vehicle command.
- If on-device recognition is unavailable or the device is older than Android 12, the feature reports that it is unavailable instead of silently switching to a network service.
- Actual language availability and recognition quality depend on the Android system's installed on-device recognition service and language packs.
