# Local voice command confirmation

## Supported commands

The on-device voice assistant now classifies a small local command set:

- “打开仪表盘” / “显示仪表” → suggest scrolling to the meter editor.
- “打开应用 [名称]” / “运行 [名称]” → match a user-installed app by visible label or package name.

Navigation and music phrases are recognized as unsupported because no live navigation or media integration is connected. Unknown phrases create no action.

## Confirmation boundary

- Recognition never directly executes a command.
- A separate **Confirm suggested action** button must be tapped.
- App launch is restricted to exactly one matching user-installed app with a public launcher activity.
- Ambiguous app matches are rejected and direct the user to the app picker.
- No vehicle commands, Amap private broadcasts, QQ Music internal actions, media key injection, wake word or background listening are used.

## Validation

Unit tests cover local meter phrases, app-target extraction, unavailable navigation/music and unknown input. Speech recognition itself still depends on the device's installed Android on-device language pack.
