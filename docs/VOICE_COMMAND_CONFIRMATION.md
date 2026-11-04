# Local voice command confirmation

## Supported commands

The on-device voice assistant now classifies a small local command set:

- “打开仪表盘” / “显示仪表” → suggest scrolling to the meter editor.
- “打开应用 [名称]” / “运行 [名称]” → match a user-installed app by visible label or package name.

Destination phrases such as “导航到 [地点]” and “带我去 [地点]” are handled by the separate confirmed public map-app handoff documented in `docs/VOICE_NAVIGATION_HANDOFF.md`. Navigation shortcuts without a destination and music phrases remain unavailable. Unknown phrases create no action.

## Confirmation boundary

- Recognition never directly executes a command.
- A separate **Confirm suggested action** button must be tapped.
- App launch is restricted to exactly one matching user-installed app with a public launcher activity.
- Ambiguous app matches are rejected and direct the user to the app picker.
- Public map handoff uses Android `ACTION_VIEW` with a `geo:` URI only after confirmation; the user chooses the receiving app. No vehicle commands, Amap private broadcasts, QQ Music internal actions, media key injection, wake word or background listening are used.

## Validation

Unit tests cover local meter phrases, app-target extraction, destination extraction, unavailable shortcuts/music and unknown input. Speech recognition itself still depends on the device's installed Android on-device language pack.
