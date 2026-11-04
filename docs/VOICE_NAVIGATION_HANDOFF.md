# Confirmed public navigation handoff

## Supported voice phrases

- “导航到 [地点]”
- “导航去 [地点]”
- “带我去 [地点]”
- “前往 [地点]”
- “回家” / “我想回家” / “请带我回家” (only when a saved home destination exists)

The recognized destination is shown first. The user must tap **Confirm suggested action** before the app creates a public Android `ACTION_VIEW` geo URI and opens the system chooser.

## Boundaries

- The user chooses which installed map/navigation app receives the destination.
- No specific Amap/Baidu package is assumed or launched directly.
- No private broadcasts, navigation app process hooks, POI service APIs, automatic route guidance or background location are used. Home destinations are entered by the user and stored locally.
- Return-home matching requires an explicit phrase (for example “回家”, “我想回家” or “请带我回家”) and a saved destination. Negated or question-like phrases such as “不要回家” and “回家吗” do not trigger navigation. See `docs/SAVED_HOME_DESTINATION.md`.
- If no app handles the geo URI, the app reports that no compatible map app is installed.

## Validation

Parser tests cover destination extraction and unsupported shortcuts. Device-level map chooser behavior depends on installed applications and requires manual validation.

## Phase 54 — Navigation phrase normalization and destination validation

- Expanded supported speech variants with polite prefixes such as “请帮我导航到”.
- Removes common spoken trailing particles and punctuation before showing the destination for confirmation.
- Rejects empty, generic navigation labels and destinations longer than 80 characters; invalid phrases do not enable a navigation action.
- Still requires a separate confirmation tap and uses only the public Android geo URI chooser.
- Added parser tests for polite phrasing, punctuation, empty/generic targets and excessive length.
- No geocoding, automatic route start, home address, location access or private map-app API is introduced.
