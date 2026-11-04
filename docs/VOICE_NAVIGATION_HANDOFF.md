# Confirmed public navigation handoff

## Supported voice phrases

- “导航到 [地点]”
- “导航去 [地点]”
- “带我去 [地点]”
- “前往 [地点]”

The recognized destination is shown first. The user must tap **Confirm suggested action** before the app creates a public Android `ACTION_VIEW` geo URI and opens the system chooser.

## Boundaries

- The user chooses which installed map/navigation app receives the destination.
- No specific Amap/Baidu package is assumed or launched directly.
- No private broadcasts, navigation app process hooks, POI service APIs, home address, route guidance or background location are used.
- “回家” remains unsupported because no saved home destination exists.
- If no app handles the geo URI, the app reports that no compatible map app is installed.

## Validation

Parser tests cover destination extraction and unsupported shortcuts. Device-level map chooser behavior depends on installed applications and requires manual validation.
