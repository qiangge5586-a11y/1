# Local startup audio selection and preview

## Workflow

- User enables the local audio preview controls.
- Android's document picker selects an audio file and grants read-only URI access.
- The app stores the document URI in local preferences.
- Playback begins only after the user taps Preview; Stop and Activity-stop lifecycle cleanup release the player and temporary audio focus.
- The selected file can be cleared, including release of the persistable URI grant.

## Boundaries

- No automatic startup playback, boot receiver, background service, wake-word listener, or network upload.
- No audio files are bundled; users select their own local media.
- Uses Android MediaPlayer and transient audio focus; it does not change system volume.
- Codec and document-provider compatibility depend on the Android device and selected file.
