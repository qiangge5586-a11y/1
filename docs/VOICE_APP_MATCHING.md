# Voice app matching and launch traceability

## Original APK behavior

The reverse-engineering report identifies the original voice assistant's app-launch family as phrases such as “打开/运行 …”, resolved against installed applications and then launched. The report's source evidence is summarized in the Library document `本田黑科技_7.6.0_逆向分析.md`; the original decompiled app-launch implementation has not yet been independently re-opened in this repository, so this is a report-level finding rather than a line-by-line source match.

## Rebuild implementation

- `VoiceCommandParser` extracts the requested app name from supported “打开应用/打开/运行/启动” phrases.
- `StartupAppLauncher.listUserLaunchableApps` limits candidates to non-system, non-self applications with public launcher activities.
- `VoiceAppMatcher` first checks exact app-label or package-name matches. Only when there is no exact match does it consider a partial match.
- A result is actionable only when exactly one candidate matches. Empty/one-character targets, no match and ambiguous matches are rejected.
- A separate confirmation tap is still required before `StartupAppLauncher.launch` is called.

## Validation

Unit tests cover exact-match priority, unique partial matches, ambiguous labels, empty/too-short targets and case/whitespace normalization. CI must pass the unit-test suite and debug APK assembly before this phase is marked verified.

## Deliberate differences

The rebuild does not copy the original cloud recognition upload, background voice service, private broadcasts, Xposed hooks, or automatic command execution. Speech recognition and app launching remain user-initiated and foreground-only.
