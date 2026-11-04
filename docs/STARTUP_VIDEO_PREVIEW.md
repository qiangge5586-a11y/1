# Local startup-video selection and preview

## Behavior

- Uses Android's Storage Access Framework with `ACTION_OPEN_DOCUMENT` and MIME type `video/*`.
- Stores the selected content URI in private app preferences and requests persistable read access from the document provider.
- Shows the selected document display name when available.
- Preview is available only when the user enables the local preview controls and taps the Preview button.
- Playback is shown in an in-app `VideoView` with standard media controls; the preview dialog stops playback when dismissed.
- Clear removes the stored URI and attempts to release its persisted read grant.

## Privacy and safety

- No broad storage permission is requested.
- No file-system path is guessed or accessed directly; the app uses the granted content URI.
- No automatic playback, boot receiver, background service, network streaming, or audio focus side effect is added.
- The setting does not launch the app at boot. It is a manually invoked local preview feature.

## Validation

The GitHub Actions Android workflow runs unit tests and assembles the debug APK. Successful CI does not replace device testing: verify selection, persistent access after process restart, playback, and clear behavior on a spare Android device and multiple document providers. No Civic head-unit compatibility is implied.
