# Saved home destination for public map handoff

## Behavior

- The user can enter a place name or address and save it in the app's private `navigation_preferences`.
- The destination is limited to 160 characters and whitespace is normalized.
- The user can clear it at any time.
- Saying “回家” creates a suggestion from the saved destination; a separate confirmation tap opens the public map-app chooser.

## Privacy and safety

- No GPS, contacts, location permission or cloud lookup is used.
- The app does not infer the home address.
- The destination remains local to this app and is not automatically shared.
- Without a saved destination, “回家” creates no action.

## Phase 54 — Destination input hardening

- Rejects embedded non-whitespace ISO control characters before a saved destination is stored.
- Continues to normalize ordinary whitespace and enforce the 160-character limit.
- Added tests for embedded NUL/control characters and newline/space normalization.
- Saved destinations remain app-private and are used only after the explicit voice-action confirmation flow.
