# HondaHack 7.6.0 Rebuild Progress

## Phase 21 — User-confirmed local APK installer (CI pending)

- Added document-picker selection for a local APK and handoff to Android's package installer.
- Android 8+ unknown-source authorization is handled through the system app-specific setting; installation itself remains a system-confirmed user action.
- No silent install, root, background install, or APK download is implemented.
- Details: `docs/LOCAL_APK_INSTALLER.md`.
- Source and manifest commits are in `f5e31c2236ebe23c2d74b2604f8607d5bada7a29` and `c610b515a787b6833ff57d8f0659109f07c08d1d`; CI validation pending.

## Phase 20 — Direct JSON preset file import and export (CI verified)

- Added Android document-picker import for JSON presets; files are read through content URIs with a 1 MiB limit and validated before storage.
- Added Save JSON file export through Android's create-document flow; existing paste import and share-sheet export remain available.
- Details: `docs/JSON_FILE_IMPORT_EXPORT.md`.
- GitHub Actions run #48 (ID 41761494816) completed successfully; unit tests, debug APK assembly and artifact upload succeeded: https://github.com/qiangge5586-a11y/1/actions/runs/41761494816
- Artifact ID: 13290362772; APK size: 38,228 bytes; APK SHA-256: `a414bad79f5f9a837d443f93eb7cc9ea0482fa7b5e89566debd931337d6c3de3`.

## Phase 19 — Startup preferences and local video preview (CI verified)

- GitHub Actions run #47 (ID 41760974386) completed successfully: https://github.com/qiangge5586-a11y/1/actions/runs/41760974386

- Added a disabled-by-default checkbox to open the offline meter editor when the app launches.
- Persists the choice locally and scrolls to the editor after layout; no boot receiver, boot permission, background startup, or third-party launch.
- Added local video selection through Android's document picker, persisted read-only URI access, explicit in-app VideoView preview, and clear-selection control. No automatic playback, boot receiver, broad storage permission, or background service.
- Details: `docs/STARTUP_PREFERENCES.md` and `docs/STARTUP_VIDEO_PREVIEW.md`.
- The initial run #44 failed because earlier newline cleanup split Java string literals. Follow-up fixes passed CI run #46 (ID 41760683021), and the local video-preview addition passed CI run #47 (ID 41760974386): unit tests and debug APK assembly succeeded.

## Phase 18 — Explicit audio-focus management

- Added a public-API audio focus controller with Android 8+ AudioFocusRequest and legacy fallback.
- Added explicit opt-in and tap-to-request UI; displays focus grant/denial and focus-change events.
- Manual release and Activity-stop release are implemented. No sound playback or system volume change.
- Audio focus controller commit passed CI run #41 (ID 41760212335). The first UI commit failed because escaped newline characters were inserted into the Java source; the source was corrected in commit `0c895efb846830691a744b6c72a7b2b769beb3de`. Run #43 (ID 41760480324) exposed additional broken string-literal newlines. Commits `e8f1fa80dab026d82487bd8026b278172d8c25de` and `6d7767cf9ccbed7008abee368b5b692daa0ff01e` repair those source literals; run #46 is the current validation.
- Details: `docs/AUDIO_FOCUS_MANAGEMENT.md`.


Last updated: 2026-11-04

## Phase 20 implementation status

- Source implementation committed in `a168d7c5353e43822d8ecc1cc770aa86ddc32ca2`.
- CI run #48 passed and its APK artifact was downloaded and SHA-256 checked.
- Real Android document-provider import/export interaction still needs manual device validation.

## Phase 17 — Named meter preset management

- GitHub Actions run #40 (ID 41759036834) completed successfully, including unit tests and debug APK assembly: https://github.com/qiangge5586-a11y/1/actions/runs/41759036834

- Added local named preset library with save, load, rename, duplicate and delete operations.
- Added JSON import via paste dialog and JSON export through Android share sheet.
- Added validation and unit tests for preset library operations and invalid imports.
- Implementation committed; CI build and unit tests must pass before marking this phase build-verified.
- Details: `docs/NAMED_METER_PRESET_LIBRARY.md`.


## Phase 16 — Multi-widget meter editor

- Parser now validates every views[] entry (up to 32 widgets), not just the first.
- Preview canvas draws all widgets and supports per-widget alignment, size and color.
- Editor supports selecting, adding and removing widgets, plus editing x/y position and width/height using normalized coordinates.
- Save widget layout stores widget count and widget settings locally; reset restores the bundled preset.
- Unit tests now cover multiple widgets, rendering, alignment and invalid layouts.
- This phase is implemented in source; the new CI run is pending and must pass before the updated APK is marked build-verified.

Details: MULTI_WIDGET_METER_EDITOR.md (phase notes are also recorded in this progress entry).  
Repository: https://github.com/qiangge5586-a11y/1  
Target: independent Android rebuild based on the user's supplied HondaHack 7.6.0 APK and reverse-analysis notes.

## Current verified status

- Original APK identity and SHA-256 recorded; see `APK_BASELINE_INVENTORY.md`.
- Original binary manifest, DEX class/method structures and resource inventory audited; see `MANIFEST_COMPONENT_AUDIT.md`, `DEX_CLASS_METHOD_MAP.md`, `RESOURCE_FEATURE_ASSET_AUDIT.md`.
- DEX instruction-reference triage completed for meter, navigation, steering-key, startup, audio, voice, GPS and receiver paths; see `DEX_BYTECODE_BEHAVIOR_AUDIT.md`. This is not full Java decompilation or runtime proof.
- Independent Android app skeleton builds successfully.
- Offline meter parser, editor canvas, editable speed/text size/position/colors and reset control implemented.
- Steering-key mapping simulator with four configurable mock keys and persistent action selections implemented.
- Navigation-app detection and explicit launch buttons implemented for package IDs found in the original APK.
- Unit tests for meter preset parsing and validation are included in CI.

## Phase 16 build verification

- GitHub Actions run #36 (ID 41757049880) completed successfully: https://github.com/qiangge5586-a11y/1/actions/runs/41757049880
- Artifact ID: 13288183434; APK size: 25,793 bytes.
- APK SHA-256: 525122662bb4968844c7152ef3f8cd9c161a603338d929edf8e8c58e15e53502.
- Multi-widget parser, canvas, editor and tests are included in this successful build.

## Latest build verification

GitHub Actions run #31:
- Run ID: `41755385647`
- Commit: `64ffbe9b039b044051c1059b379e9a7b5333a0ba`
- Run URL: https://github.com/qiangge5586-a11y/1/actions/runs/41755385647
- The `Assemble debug APK` step completed successfully. That command runs `:app:testDebugUnitTest` followed by `:app:assembleDebug`.
- The `Upload debug APK` step completed successfully.
- Artifact ID: `13287766903`; artifact name: `hondahack-rebuild-debug`.
- Downloaded APK size: 22,646 bytes (approx. 22.6 KB).
- APK SHA-256: `c26c2a8f0c70682459e43807ec868da363e98d3d90f30ef74339d43403196921`.

The workflow completed with conclusion `success`; test/build and artifact-upload steps all succeeded. This artifact is a debug prototype, not a complete or pixel-identical HondaHack clone.

## Phase history

1. Original 7.6.0 APK verified and fingerprinted.
2. APK ZIP/DEX structural inventory recorded.
3. Existing source ZIP audited: 47 entries, two Java files and basic Gradle/manifest/assets scaffolding; not a complete clone.
4. Feature traceability and safe rebuild boundaries documented.
5. Initial Android project and GitHub Actions debug build established.
6. Binary AndroidManifest.xml parsed: package/version/SDK, permissions and declared components recorded.
7. DEX class_data mapped; app entrypoint methods inventoried.
8. Meter JSON presets, audio resources and privileged payload indicators audited.
9. Preliminary DEX instruction-reference behavior map completed; JADX readable decompilation remains outstanding.
10. Offline meter preset parser/repository and validation tests added.
11. CI updated to run unit tests before assembling the debug APK.
12. Interactive meter editor added with persistent speed, text size and theme settings.
13. Normalized-coordinate canvas, horizontal/vertical layout controls and reset function added.
14. Configurable steering-key simulator added; it does not capture physical inputs or issue vehicle commands.
15. Safe navigation-app detection and explicit launcher added; it does not force-stop, modify or inject into other apps.

## Safety and compatibility boundaries

- No root/Xposed installer, signature spoofing, app_process replacement, platform permission modification, silent installation, remote revocation, destructive shell commands or system-partition writes.
- No force-stop/kill of third-party apps and no undocumented vendor service calls.
- Meter and vehicle values remain mock data. No CAN/OBD/ECU telemetry integration is implemented.
- The user's 2019 Civic factory head-unit compatibility is not assumed from package strings or Mitsubishi/Clarion vendor intents. Model, firmware and documented interfaces must be identified before device integration.
- The debug APK has not yet been tested on the user's vehicle or factory head unit.

## Next steps

1. Obtain readable JADX output and review high-value original methods (MyService, MeterActivity, NavigatorActivity, RemapKeysActivity, StartupActivity and VoiceAssistantService).
2. Obtain readable JADX output and review high-value original methods (MyService, MeterActivity, NavigatorActivity, RemapKeysActivity, StartupActivity and VoiceAssistantService).
2. Add named preset save/rename/duplicate/import/export.
3. Add safe, explicit audio-focus controls and startup preferences using public Android APIs.
4. Build a compatibility matrix for the exact Civic factory head-unit model/firmware.
5. Test the debug APK on a spare Android device first; do not modify the live vehicle head unit until a documented interface and rollback path are confirmed.

<!-- Previous roadmap retained below for historical context. -->

1. Obtain readable JADX output and review high-value original methods, especially `MyService`, `MeterActivity`, `NavigatorActivity`, `RemapKeysActivity`, `StartupActivity` and `VoiceAssistantService`.
2. Extend the meter editor to multiple widgets, drag positioning, font/alignment/stroke/shadow/background assets and named presets with import/export.
3. Add safe, explicit audio-focus controls and startup preferences using public Android APIs.
4. Build a device compatibility matrix for the exact Civic factory head-unit model/firmware.
5. Test the debug APK on a spare Android device first; do not modify the live vehicle head unit until a documented interface and rollback path are confirmed.
