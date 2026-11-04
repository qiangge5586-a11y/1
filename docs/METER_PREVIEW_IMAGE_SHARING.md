# Shareable meter preview image

## Workflow

- The meter editor renders its current offline preview into a PNG in the app's private cache.
- The user taps **Share meter preview image** and chooses a destination from Android's share sheet.
- A non-exported, read-only content provider exposes only the generated preview file through a temporary URI grant.

## Boundaries

- Captures only this app's meter preview view, not the system screen, other apps, navigation overlays or private third-party content.
- The PNG stays in app cache; no shared-storage write permission is requested.
- The provider accepts read-only opens and validates the filename/canonical path to prevent path traversal.
- Sharing occurs only after the user taps the button; no automatic screenshot capture or upload.
