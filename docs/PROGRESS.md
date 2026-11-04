# HondaHack 7.6.0 Rebuild Progress

## Phase 65 — Explicit read-only stored fault-code request (CI pending)

- Added a separate exact allowlist for standard Mode 03 command `03`; Mode 04 clear-DTC and all other commands remain blocked.
- Added a user-visible “Read stored fault codes” button with a second confirmation dialog. The request is queued on the Bluetooth worker so it cannot write to the socket concurrently with the telemetry poller.
- Results are decoded with `ObdDtcDecoder` and shown in memory only. Reading is never automatic on connect; no clear/reset/write action exists.
- If there is no active paired adapter session, the UI asks the user to connect first.
- Tests cover the exact Mode 03 allowlist boundary; parser tests remain in Phase 64.
- Details: `docs/OBD2_DTC_READ.md`.
- Source/test commits: `c2b0e719c6726424aab5502b16690cc25319f906`, `2d82b192d5eaed7ca1e42091c5edcbcc5862a0f4`, `45506dd455b6651776ef3a3c1987a16845f5f082`, `0a61396365226ffbf09506da4fc37959df850d22`; CI pending.


## Phase 64 — Standard OBD-II stored trouble-code decoder (CI verified)

- Added a parser for positive Mode 03 `43` responses and standard P/C/B/U trouble-code categories.
- Accepts compact/spaced ELM output, optional CAN header, echoed `03` and prompt suffix; preserves order, removes duplicates and skips zero padding.
- Negative, no-data, malformed, truncated and oversized responses return no codes.
- Parser-only: no Mode 03 request is sent, no DTC is cleared, and no ECU reset/write action is introduced. The Bluetooth allowlist remains unchanged.
- Added unit tests for all four code categories, ordering/deduplication, padding, no-data and malformed input.
- Details: `docs/OBD2_DTC_READ.md`.
- The first test run exposed a malformed import in the test source; corrected in the follow-up commit.
- Source/test commits: `32709529699f32818b30c85d572448e0f11db49e`, `971fd28b3d2c3b8edd1ec7bf7c3ed9fa990154d0`, `81349d1171a9e4064a55fb1a786181a3413ba9fe`.
- GitHub Actions run #207 (ID 41779519513) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41779519513. Artifact ID: 13297678990; APK artifact ZIP size: 97,427 bytes.


## Phase 63 — Live OBD values in the meter preview (CI verified)

- Connected live OBD speed, RPM, coolant and fuel values to the Civic meter token map.
- Converts OBD speed to mph and coolant to °F for US/Imperial display modes; RPM and fuel percent remain direct. The speed slider is disabled and relabelled while live data is active.
- Missing/invalid OBD fields display `--` rather than demo values. Route names, speed limits, distance-to-empty, trip distance and fuel-economy tokens also display `--` because the current generic OBD session does not supply them.
- Compass and unit labels retain their own sources. Disconnecting or leaving the Activity clears the in-memory live overlay and restores the demo preview; live values are not persisted.
- Tests cover disconnected demo restoration, live overlay, metric/US conversions, missing PID placeholders and suppression of unsupported demo tokens.
- Details: `docs/OBD_LIVE_METER_OVERLAY.md`.
- GitHub Actions run #204 (ID 41779323368) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41779323368. Artifact ID: 13298123936; APK artifact ZIP size: 96,813 bytes.
- Source/test commits: `af97513ef320c98d348958d14604b565c7a60c5f`, `2f37d6ecf445e6ccdeaaa4bfdabc814b856cd938`, `c6d0d17c200f06fc37c85bc2018c9736f27cbd3b`; duplicate mapper cleanup: `7b7824c91e80f81e20719a4b478341ffa94df7e6`, `f52efba09959fa418c97dcad6db84be442f84f0d`.

## Phase 62 — ECU-supported PID polling (CI verified)

- Integrated `ObdPidQueryPlan` into the paired Bluetooth ELM327 session.
- On connection, the client queries `0100`, and requests `0120` / `0140` only when the previous bitmap advertises that extension.
- The live polling loop now intersects ECU-advertised PIDs with the strict allowlist for speed, RPM, coolant and fuel level. Unsupported or missing bitmap entries are not polled and display as unavailable.
- Added only the three explicit read-only bitmap commands to the command allowlist; no other Mode 01 PID, DTC clearing, actuator control, coding, reset or ECU write command is enabled.
- Added allowlist tests for the bitmap discovery commands and explicit rejection of other PIDs.
- Details: `docs/OBD2_READ_ONLY_QUERY_PLAN.md`.
- The first two integration attempts exposed a mismatch between the query planner and the command allowlist; the final allowlist test verifies only `0100`, `0120`, `0140` discovery plus four data PIDs are enabled.
- GitHub Actions run #194 (ID 41778921208) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41778921208
- Final source/test commit: `6dfa46d181f3907158eb271955b15ec725666c0e`; artifact ID: 13298301695; APK size: 99,092 bytes; SHA-256: `81c1562c5ff67b01c87de762b2a2677fd28ee32e50bdec9060a10898d7bce8e9`.


## Phase 61 — Paired Bluetooth ELM327 read-only session (CI verified)

- Added a manual picker for already-paired Bluetooth devices; no discovery scan or automatic pairing is performed.
- Requires explicit adapter selection and a second Connect confirmation. Android 12+ requests BLUETOOTH_CONNECT only when the user opens the picker; legacy Bluetooth permissions are limited to Android 11 and below.
- The foreground-only SPP client performs an explicit allowlisted ELM setup sequence and polls only four allowlisted standard Mode 01 PIDs (speed, RPM, coolant and fuel level).
- Session generation checks prevent stale callbacks; sockets and worker threads are closed on disconnect and when MainActivity pauses. No background reconnect.
- The UI keeps demo snapshots separate from live adapter readings; missing/invalid PIDs display as unavailable, not invented values.
- Tests cover strict PID and adapter-setup allowlists. Run #185 exposed a compile-time mismatch during allowlist refactoring; the follow-up polling-loop correction and test run #187 passed. Final permission/build check Run #188 also passed.
- Details: `docs/OBD2_ELM327_DECODER.md`, `docs/OBD2_READ_ONLY_QUERY_PLAN.md`.
- Source/permission commits: `35de13ec1dfb95016b103cd458da18173f3669e0`, `7e459945d0ec12c928bd428b583be1c09c42d1a1`, `3f92ed968e5c5a68139d8896c498df79b746ed87`, `e3b89d782b6b769684f3822483ac25ebebfb50f4`, `31f5998d8898501dd8dfc909da69c1b096f1ca34`, `6f1720a83a56cc21136dbdffd853a8cba263691c`, `bf93b0515bdd82a82c3ae3868293472ae079c9d9`; GitHub Actions run #188 (ID 41778706948) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41778706948. Artifact ID: 13297509782; APK artifact ZIP size: 92,026 bytes. Hardware validation remains outstanding.

## Phase 60 — Read-only OBD-II PID query planning (CI verified)

- Added a transport-independent query planner that starts with Mode 01 PID bitmap 0100 and follows 0120/0140 only when the ECU advertises the extension.
- Generates commands only for supported common dashboard PIDs, in stable order; missing or malformed bitmaps do not invent supported values.
- The planner generates standard read-only Mode 01 requests only; no DTC clearing, actuator control, programming, ECU reset or manufacturer-specific PID is included.
- Added tests for bitmap bit ordering, extension discovery, supported PID scheduling, malformed responses and PID-range validation.
- Details: `docs/OBD2_READ_ONLY_QUERY_PLAN.md`.
- GitHub Actions run #187 (ID 41778681165) passed the combined unit-test suite, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41778681165
- Source/test commits: `16df646bf778bfcb172e90c019fcd64453c47cad`, `01b8f839046646e755db9795acf88dc999d58377`; final verification includes allowlist hardening commits `31f5998d8898501dd8dfc909da69c1b096f1ca34`, `6f1720a83a56cc21136dbdffd853a8cba263691c`.


## Phase 59 — Deterministic spoken app matching (CI verified)

- Refactored voice app resolution into `VoiceAppMatcher`: exact app-label/package matches take priority, then unique partial matches are considered.
- Rejects empty and one-character targets, ambiguous exact/partial matches and missing apps rather than guessing.
- Continues to use the user-app-only launcher list and separate confirmation tap before launch.
- Added unit tests for exact priority, unique partial matches, ambiguity, empty/short targets and case/whitespace normalization.
- Original behavior reference: the Library reverse-engineering report describes the original “打开/运行 …” installed-app launch family; this phase documents report-level traceability and does not claim line-by-line parity with unavailable decompiled source.
- Details: `docs/VOICE_APP_MATCHING.md` and `docs/VOICE_COMMAND_CONFIRMATION.md`.
- GitHub Actions run #178 (ID 41778451015) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41778451015
- Source commits: `64fd3c719ff14e39ec2674aaa9ff76b7d51fd703`, `f9872883501e2b76ba750eba1f97df10c403bc05`, `af2904db2db9bec2b255170514c31750a72b6228`; artifact ID: 13297663538; APK artifact ZIP size: 87,070 bytes. (No APK SHA-256 is recorded for this artifact.)


## Phase 58 — Standard OBD-II / ELM327 protocol decoder (CI verified)

- Added a pure ELM327 positive-response parser for compact/spaced responses, optional CAN headers, command echo, SEARCHING text and prompt.
- Added common Mode 01 PID decoders for speed, RPM, coolant, fuel level, engine load, throttle, MAF, intake/oil temperature, manifold/barometric pressure, timing advance and fuel rate.
- Unsupported, negative and truncated responses return null; no fabricated values.
- No Bluetooth/USB/Wi-Fi connection, pairing, vehicle command, Honda enhanced PID or new permission is added. The UI remains demo data until a real adapter is selected and tested.
- Added unit tests for formulas, framing and malformed/no-data cases.
- Details: `docs/OBD2_ELM327_DECODER.md`.
- GitHub Actions run #175 (ID 41778407286) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41778407286
- Final source/test commit: `d3c775b7c6bc4b047ec0c1999c98ad381f88fe92`; artifact ID: 13297542896; APK size: 89,703 bytes; SHA-256: `c5b6754d07baad5678932421359e02f28d363c3de0999ea0f4502323923ebcaf`.


## Phase 57 — Opt-in foreground GPS course (CI verified)

- Added GPS_PROVIDER-only location updates after the user enables the meter setting; fine+coarse permissions are requested together only on that explicit opt-in, and GPS course requires precise access.
- Updates run only while the Activity is foregrounded and stop in `onPause`. No network provider, coordinates display/storage/upload, contacts, background location or map-app internals.
- GPS course drives track-up only when bearing and speed are valid and speed is at least 5 km/h; otherwise the UI falls back to device compass/sample heading.
- GPS speed is labelled separately and is not treated as ECU/CAN/OBD vehicle speed.
- Added tests for course threshold, missing data, invalid speeds and compass-mode descriptions.
- Details: `docs/FOREGROUND_GPS_COURSE.md`.
- GitHub Actions run #172 (ID 41778171954) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41778171954
- Final source/manifest commit: `7e65d378206c45bf8bd5f8aa1d7c299ad79fad83`; artifact ID: 13297348399; APK size: 88,480 bytes; SHA-256: `bd39bcb74ff13e3dfacf56e4c43da4b63c377a3b2ce6b33e83bc06b39eb744d2`.

## Phase 56 — Explicit return-home phrase guard (CI verified)

- Replaced substring matching for `回家` with an explicit allowlist of home-navigation phrases.
- Supports direct/polite variants such as `回家`, `我想回家`, `请带我回家` and `导航回家`.
- Negated/question-like phrases such as `我不回家`, `不要回家` and `回家吗` do not create a navigation action.
- Still requires a saved home destination and a separate confirmation tap before the public map chooser opens.
- Added tests for positive phrases and false-positive rejection.
- Details: `docs/VOICE_NAVIGATION_HANDOFF.md`.
- GitHub Actions run #161 (ID 41777751795) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41777751795
- Final source/test commit: `702d908ff97937dd24f9633df5ae1e3ad808b7e0`; artifact ID: 13298230197; APK size: 84,964 bytes; SHA-256: `6519594b479f6513a3bf25710ef370da7f404bcff8809289b9be5ac4eca61427`.

## Phase 55 — Saved destination input hardening (CI verified)

- Rejects embedded non-whitespace ISO control characters before saving a home destination.
- Preserves ordinary whitespace normalization and the 160-character limit.
- Added tests for NUL/control characters and newline/space normalization. The first test draft used escaped text rather than actual control characters; corrected to construct control chars explicitly and use a real newline.
- Normalization now uses an explicit whitespace pass and rejects ASCII/ISO control characters (except ordinary whitespace) before persistence.
- GitHub Actions run #159 (ID 41777567961) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41777567961
- Final source commit: `8db5e082fd8324e9b1b19b9f8a931773f7c1b799`; artifact ID: 13297991332; APK size: 84,756 bytes; SHA-256: `02c2979e828ddd86dfe9cd376f540527d427d808af5189bdb8ce07ac295dc2a0`.
- Details: `docs/SAVED_HOME_DESTINATION.md`.

## Phase 54 — Navigation phrase normalization and destination validation (CI verified)

- Added polite Chinese navigation phrase variants and cleanup of spoken trailing particles/punctuation.
- Empty, generic and over-80-character destinations are rejected before the confirmation button can be enabled.
- Public Android geo URI chooser and separate user confirmation remain required.
- Added parser tests; no geocoding, location access, private map APIs or automatic route start.
- Details: `docs/VOICE_NAVIGATION_HANDOFF.md`.
- GitHub Actions run #155 (ID 41777206607) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41777206607
- Final parser/test commit: `a3dc4a7ad3662f7ad41793383401cce664b5e6ce`; artifact ID: 13298010640; APK size: 84,489 bytes; SHA-256: `811951e39f7795c919428d4453fc03aa1de95953818f92ef6492713907877df6`.


## Phase 53 — Saved home destination (CI verified)

- Added a user-entered home place/address stored only in app-private preferences, with clear and length/whitespace validation.
- “回家” now proposes the saved destination and still requires the Confirm button before opening the public map chooser.
- Without a saved destination, no navigation action is created. No GPS, contacts, location permission or cloud lookup.
- Added parser and destination validation tests.
- Details: `docs/SAVED_HOME_DESTINATION.md`.
- Early builds exposed a voice-phrase parser mismatch and test expectation drift; destination parsing was hardened for direct/polite phrases, trailing punctuation and bounded target length.
- GitHub Actions run #155 (ID 41777206607) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41777206607
- Final source/test commit: `a3dc4a7ad3662f7ad41793383401cce664b5e6ce`; artifact ID: 13298010640; APK size: 84,489 bytes; SHA-256: `811951e39f7795c919428d4453fc03aa1de95953818f92ef6492713907877df6`.

## Phase 52 — Confirmed public navigation handoff (CI verified)

- Extended the on-device voice parser to extract a destination from navigation phrases.
- After a separate confirmation tap, opens a public Android geo URI through the system map-app chooser.
- Does not target Amap/Baidu directly, send private broadcasts, read current location or start background guidance.
- Phase 53 adds a user-entered local home destination; “回家” opens the public map chooser only after confirmation when one is configured.
- Added parser tests and documentation.
- Details: `docs/VOICE_NAVIGATION_HANDOFF.md`.
- The first parser update run #144 failed because the previous test still expected the old unsupported-navigation classification; the test was updated to assert destination extraction.
- GitHub Actions run #146 (ID 41776843071) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41776843071
- Final source/test commit: `aa67d7347bebd2a4625a776c668dd5c75d97ac6f`; artifact ID: 13296863136; APK size: 82,661 bytes; SHA-256: `791a93ee6d76db180172c8679f6c6c88ce2ad6a17d4923ebcbc207b6597b2671`.

## Phase 51 — Confirmed local voice commands (CI verified)

- Added a conservative parser for local meter-editor and user-app launch phrases.
- Recognized navigation destinations are handled by the separate Phase 52 public map-app chooser; navigation shortcuts without a destination and music remain unavailable. Unknown speech creates no action.
- Every supported action requires a separate Confirm button tap. App launches require exactly one matching installed user app.
- Added parser unit tests and documentation.
- No vehicle command, private Amap/QQ Music integration, media-key injection, wake word or background listening.
- Details: `docs/VOICE_COMMAND_CONFIRMATION.md`.
- The first parser-only build failed because the MainActivity section-anchor code lacked the `android.view.View` import; this was corrected before the final build.
- GitHub Actions run #143 (ID 41776668344) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41776668344
- Final source/test commit: `8108502f3a02819b26868cc6ec8148935a73602a`; artifact ID: 13297650304; APK size: 82,118 bytes; SHA-256: `f46521bd727f0b744b119feaaf224108c86dcc4d5be5815758ddfe5df58713bf`.

## Phase 50 — Original voice assistant audit (analysis complete)

- Confirmed original speech recognition uploads recorded MP3 audio through an HTTP form request; it is not on-device ASR.
- Mapped original command families: installed-app launch, Amap navigation/POI broadcasts, QQ Music Car playback and bundled spoken responses.
- Rebuild keeps the Android on-device recognizer, explicit microphone opt-in and foreground-only lifecycle. Phase 51 adds only local meter/app actions with a separate confirmation tap; navigation and music remain unavailable.
- No cloud audio upload, private navigation broadcasts, QQ Music internals, shared-storage recording, wake word or background service is copied.
- Details: `docs/VOICE_ASSISTANT_ORIGINAL_AUDIT.md`.

## Phase 49 — Ten-slot foreground steering-key mapping (CI verified)

- Replaced the simulator-only mapping with ten configurable foreground key slots.
- User explicitly enables monitoring, captures a key delivered to the Activity, and assigns a local action: show meter editor, navigation app list, voice-assistant settings or road-information demo.
- Duplicate key assignments are cleared; Home, Back, Power and volume keys are rejected. Unmapped events pass through unchanged.
- Actions only navigate within the app; no media, volume, vehicle or external-app command is sent.
- Added unit tests for slot lookup, duplicate reassignment, invalid slots and system-key filtering.
- Does not use root, Xposed, vendor logcat, private broadcasts, CAN or global input injection. Physical steering-wheel event delivery remains unverified.
- Details: `docs/FOREGROUND_STEERING_KEY_MAPPING.md` and `docs/FOREGROUND_KEY_EVENT_MONITOR.md`.
- GitHub Actions run #141 (ID 41776603787) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41776603787
- Final source commit: `d362e867fb0d662daa746e7fa45a04752f978472`; artifact ID: 13296883225; APK size: 80,857 bytes; SHA-256: `1e424f24b57c61f19f24f659627d968bb6a35ebd97050e09c7a296129430c197`.

## Phase 48A — Original startup automation audit (analysis complete)

- Traced original delayed app launch, optional media keyevent 126 injection, agreement auto-confirm and restore-last-function Xposed hook.
- Agreement auto-confirm can dismiss an OEM legal/safety prompt without user review; restore-last-function modifies a host-process method.
- These behaviors are not public Android APIs and are not copied. The rebuild keeps user-initiated app launch and its own local landing-position preference.
- Details: `docs/STARTUP_AUTOMATION_AUDIT.md`.

## Phase 48 — Startup app selection (CI verified)

- Added a picker for installed user apps with public launcher activities.
- Saves the selected package and launches only after an explicit tap.
- Excludes system apps and the rebuild app. No boot-time automation or private app access.
- Added unit coverage for eligibility rules.
- Details: `docs/STARTUP_APP_SELECTION.md`.
- GitHub Actions run #136 (ID 41776272254) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41776272254
- Final source/test commit: `cff839b06583e00d626f51401c30581014f48126`; artifact ID: 13297027510; APK size: 77,543 bytes; SHA-256: `880ecaa7a822705058d55d8870a2396e94d7cc25d843fc08e3d02434b0aee0ba`.

## Phase 47 — Startup video preview controls (CI verified)

- Audited the original settings: audio on by default, delay 0 seconds (0–30), max preview 30 seconds (5–60), fade-out on by default.
- Added local preview settings for audio on/off, a 0–30 second delay, a 5–60 second maximum duration and optional fade-out.
- Settings apply only to the selected local document after the user explicitly taps Preview; no boot playback, network, background service or storage scanning is introduced.
- Preview callbacks are cancelled and playback is stopped when the dialog closes.
- Added a bounded options model and unit tests for valid values and clamping.
- Preview is manually launched only; no boot playback, background service, online video picker, unrestricted storage scan, file deletion or copying to fixed paths. Closing the dialog cancels callbacks and stops playback.
- Added a bounded options model and tests for valid values and clamping.
- Details: `docs/STARTUP_VIDEO_PREVIEW_OPTIONS.md`.
- GitHub Actions run #131 (ID 41776032442) and run #133 (ID 41776056994) passed unit tests, debug APK assembly and artifact upload. Final verification: https://github.com/qiangge5586-a11y/1/actions/runs/41776056994
- Final source/test commit: `7fb6ee0dab316c22155190b157b9e66725f905d1`; artifact ID: 13296299094; APK size: 75,234 bytes; SHA-256: `e4b57e3ff0194eaeae6d8898c8f484fc9a2b765e385df2bf93af1af47cacc48b`.


## Phase 46 — Foreground device compass sensor (CI verified)

- Added Android public `TYPE_ROTATION_VECTOR` support for the Civic meter compass when the sensor exists.
- Converts azimuth to eight compass directions and remaps axes for display rotation.
- Sensor starts only while the Activity is foregrounded and compass is enabled; it stops in `onPause`.
- No location permission, GPS, navigation app, vehicle service, CAN, OBD or ECU access. Missing sensor falls back to the labelled sample heading.
- Track-up remains simulated because device orientation is not a verified direction-of-travel source.
- Added unit tests for cardinal/intercardinal directions, angle normalization, boundaries and invalid values.
- Details: `docs/DEVICE_COMPASS_SENSOR.md`.
- Corrected the NW/N sector-boundary test in commit `b7a8cc3a52b91b7844c213b90697e4944f6ca079`.
- GitHub Actions run #131 (ID 41776032442) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41776032442
- Final source/test commit: `98fddd2c3a74d7ad7119fff59b260b05a1efcb20`; artifact ID: 13296738360; APK size: 75,187 bytes; SHA-256: `233c76239111cdd8781c3705b0093748c09b0ccd05bfc2955f16088856207f07`.

## Phase 45A — Original screen-casting integration audit (analysis complete)

- Confirmed original screen casting binds to the private SystemUI `TakeScreenshotService` through an Xposed process hook.
- Verified the Messenger frame protocol: message 100 initializes a 400×240 ARGB_8888 buffer, 101 streams 384,000-byte frames, 102 releases resources; quality values 5/8/12/15 control the frame loop.
- Confirmed private `START_CAST` / `STOP_CAST` broadcasts and source-state gating.
- This is not public MediaProjection and is not assumed available on the 2019 Civic OEM screen. The rebuild will not bind private SystemUI services or inject Xposed hooks.
- Details: `docs/SCREEN_CAST_INTEGRATION_AUDIT.md`.

## Phase 45 — Road information and offline voice demo (CI verified)

- Added the original road-information and voice-alert preference controls to the Civic meter demo.
- Road/turn values are fixed sample content; no Amap app, private broadcast, location feed or vendor API is accessed.
- Voice output requires both explicit opt-ins and a manual button tap. It selects only an installed TTS voice marked as not requiring network access; if unavailable, it refuses network speech.
- TTS is stopped and released with the Activity. No background announcement service or copyrighted bundled voice assets are used.
- Added tests for road display and spoken sample strings.
- Details: `docs/ROAD_INFORMATION_OFFLINE_VOICE_DEMO.md`.
- GitHub Actions run #124 (ID 41775670071) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41775670071
- Final source/test commit: `767f5d7370a1e8e6a6d8f4030b072c1ac42ecf06`; artifact ID: 13296029067; APK size: 69,984 bytes; SHA-256: `235df14287b6d4fd22ba9ecf44ae8e318e3245267588f17aed70c2b7a6a69b43`.

## Phase 43 — Civic compass and track-up options (CI verified)

- Added the original compass enable (default on) and dependent track-up (default off) controls.
- Disabling compass hides the compass widget, disables track-up and resets it off.
- Track-up changes only the static sample compass label; UI explicitly states orientation is simulated and no compass sensor/GPS/live map is connected.
- Added tests for visibility, label behavior and demo-only status.
- Details: `docs/CIVIC_COMPASS_OPTIONS.md`.
- Corrected an earlier Civic telemetry decoder line-break/formatting issue in commit `196e94df486f15de090f895ca0d7cd2d62191843`; the final build includes the fix.
- GitHub Actions run #121 (ID 41775390619) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41775390619
- Final source/test commit: `196e94df486f15de090f895ca0d7cd2d62191843`; artifact ID: 13296662705; APK size: 67,445 bytes; SHA-256: `b39d8511252ecb132b4dbd0473bacf80b488dd123f1290a8300aac55798e6318`.

## Phase 44 — Source-derived Civic telemetry decoder (CI verified)

- Added a pure decoder for the original Civic meter's integer fields: speed, distance-to-empty, Trip A distance, average fuel efficiency, unit codes and coolant temperature.
- Reproduces verified original scaling: trip/fuel tenths, large Trip A distance formatting, distance/fuel unit codes, coolant offset (-40) and Celsius/Fahrenheit conversion.
- Missing values or missing unit metadata remain placeholders; no guessed units.
- Added tests for metric/mile conversions, all fuel-efficiency unit codes, large-distance formatting, missing values and missing unit metadata.
- The decoder transforms supplied values only. It does not read privileged shared files, start shell/root/Xposed, access CAN/OBD/ECU or connect to a vehicle.
- Details: `docs/CIVIC_TELEMETRY_DECODER.md`.
- GitHub Actions run #121 (ID 41775390619) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41775390619
- Final source commit: `196e94df486f15de090f895ca0d7cd2d62191843`; artifact ID: 13296662705; APK size: 67,445 bytes; SHA-256: `b39d8511252ecb132b4dbd0473bacf80b488dd123f1290a8300aac55798e6318`.

## Phase 42 — Original Civic vehicle-information field selection (CI verified)

- Reproduced the original six selectable Civic meter fields and the original first-three default selection.
- Added persistent local checkboxes and responsive bottom-row reflow; deselected fields are hidden.
- Added tests for defaults, all-six layout, sparse/empty selection and canvas bounds.
- Based on the original `pref_meter.xml` and `VehicleInformationListDefaultValues` / `VehicleInformationListValues` resources.
- Demo-only: no ECU/CAN/OBD or head-unit data connection.
- Details: `docs/CIVIC_VEHICLE_INFO_SELECTION.md`.
- GitHub Actions run #112 (ID 41775167146) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41775167146
- Final source/test commit: `24e9f24c7a8510b88f10688b10845a4932a71bc6`; artifact ID: 13296183020; APK size: 65,103 bytes; SHA-256: `cab3cfb315c301cd28a08ba4101eb5fe02122b0210f8a6c4987d0e5ab53900fd`.

## Phase 41 — Selectable Civic meter demo unit systems (CI verified)

- Added metric, km/L, US customary and imperial display-only unit modes.
- Demo values convert speed, distance, fuel efficiency and coolant temperature; speed and field labels update from the selected mode.
- Unit choice is saved locally in the meter editor. It never reads or changes the vehicle's own unit settings.
- Added tests for US/imperial conversions and unit labels.
- Details: `docs/CIVIC_METER_LAYOUT_AUDIT.md`.
- Source/test commits: `5afd46518fd3b6e9c4cd8d574a026583812f19ad`, `7f1d8405b430cb46786ce96fc4a1a24340b00dc8`, `71a40c298f99c417854dcd290357b5d7385bdb25`, `48098bca863cd7271f527bf25da05cadba0de061`, `0b304f3d06438337f28197179d6bfd381259ac18`, `70f08615503e909281669c22bc341ec37c139e34`, `2912738168e2823c65354dab14aaf946583237cc`, `96cffa517d39a07f4aa7f5e90beed13a411632b6`, `92248a0e14b025290427f0f1c6a60390ba0468c2`, `215b3a9e0705a39eab6900436899ea25667bb7b3`.
- GitHub Actions run #109 (ID 41773638600) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41773638600
- Final source/test commit: `215b3a9e0705a39eab6900436899ea25667bb7b3`; artifact ID: 13296365787; APK size: 63,549 bytes; SHA-256: `b6f3a19ffcb29d19179755c6ada33b6fa390e798f45b31d3f80dc3c5b49ec938`.

## Phase 40 — Explicit Civic demo units (CI verified)

- Added separate tokens for distance, fuel-efficiency and coolant-temperature units.
- Civic sample template now displays km, L/100km and °C labels; these are explicit demo defaults, not vehicle-derived settings.
- Added a UI warning that values and units are simulated, and tests for token rendering and unit values.
- Details: `docs/CIVIC_METER_LAYOUT_AUDIT.md`.
- GitHub Actions run #99 (ID 41773148350) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41773148350
- Final test commit: `8050a4008b6e876e58418923686c9387da238bc1`; artifact ID: 13295239494; APK size: 61,324 bytes; SHA-256: `eda10a1a72a2405e0cc572755da7bda7b7bf8df46127254bf07db52d4929e2d9`.

## Phase 39 — Original vehicle telemetry transport audit (analysis complete)

- Confirmed original meter values use a privileged file IPC directory at `/data/local/tmp/shared_files`, created by shell and made world-writable (`chmod -R 777`).
- `C0185dc` uses `RandomAccessFile` and file locks to read/write integer values; `C0269ua` polls speed, distance-to-empty, trip distance, fuel efficiency and coolant fields.
- Xposed callbacks populate selected shared files; this is not a documented OBD-II or public Honda API.
- The rebuild deliberately does not reproduce the privileged file bridge, broad permissions, root or Xposed hooks.
- Details: `docs/VEHICLE_DATA_TRANSPORT_AUDIT.md`.

## Phase 38 — DEX-level Civic layout selector verification (analysis complete)

- Rechecked `C0269ua.getMeterLayout()` in JADX raw-instruction fallback mode after normal Java output incorrectly reconstructed its switch return path.
- Confirmed the actual DEX mapping: Civic/Clarity → `meter_civic`; CR-V/Breeze → `meter_crv`; Avancier/Avancier240/UR-V/CDX → `meter_avancier`.
- This confirms the original APK's Civic-specific meter branch, but not compatibility with a 2019 Civic OEM center display or access to its proprietary data services.
- Updated evidence: `docs/CIVIC_METER_LAYOUT_AUDIT.md` and `docs/ORIGINAL_APK_METHOD_AUDIT_2026-11-04.md`.

## Phase 37 — Source-derived Civic meter template (CI verified)

- Resource audit found original `meter_civic.xml` layouts at 584×215 px and six vehicle-information fields.
- Expanded validated meter preset tokens for distance-to-empty, current drive distance, average fuel efficiency, Trip A distance/efficiency, coolant, compass, navigation, speed limit and camera distance.
- Added static `CivicMeterDemoData`, a loadable Civic 584×215 demo layout and aspect-ratio-preserving preview. All values remain mock data; no CAN/OBD/ECU access.
- Added tests for token rendering, clamping and rejection of unknown tokens.
- Details: `docs/CIVIC_METER_LAYOUT_AUDIT.md`.
- GitHub Actions run #93 (ID 41772763133) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41772763133
- Final UI fix commit: `58d2a277060618e201d87fa9c62c5b3c9e2444d1`; artifact ID: 13295233557; APK size: 61,077 bytes; SHA-256: `445d738f8ea71c7e67b6f543e1b66f0aff85e069c01db23fd17bc10e9b28b246`.

## Phase 36 — Fresh source-level audit of original APK (analysis complete; DEX follow-up pending)

- Regenerated the original APK's JADX 1.5.3 source tree from the verified Library APK.
- Reviewed the main entry/menu, root/Xposed bootstrap path, modified licensing stubs, steering-key remapping, vendor service binding, navigation/audio hooks, startup video and voice-assistant entry points.
- Confirmed the setup path contains persistent ADB, system remount/app_process replacement, Xposed bridge deployment, platform.xml replacement and privileged component changes. These are explicitly excluded from the rebuild.
- Confirmed the supplied modified sample has unconditional registration success and a disabled revoke stub; this is not a clean vendor baseline.
- Full findings and remaining DEX review targets: `docs/ORIGINAL_APK_METHOD_AUDIT_2026-11-04.md`.
- Standard JADX mode: 1,286 Java files, 381 under `cn/autohack`, 31 fallback methods across 23 files; debug bad-code pass emits bodies but they remain subject to DEX validation.

## Phase 35 — Reproducible original APK decompilation setup (CI verified)

- Added a pinned JADX 1.5.3 CLI script with SHA-256 checks for both the original APK and official decompiler release archive.
- Requires WSL2 ext4 output because the APK contains case-colliding class names such as `d.java` and `D.java`; warns/refuses a case-insensitive workspace by default.
- Keeps the original APK unchanged, avoids overwriting existing decompilation output, and records analysis metadata.
- Added a CI shell-syntax validation step. The verified toolchain was retrieved by GitHub Actions run #1 (ID 41772005669), then the APK was successfully decompiled in an ext4 Linux workspace. The WSL2 helper remains available for repeatable local reproduction.
- Details: `docs/JADX_REPRODUCIBLE_DECOMPILATION.md`.
- GitHub Actions run #85 (ID 41771880665) passed the helper shell-syntax check, unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41771880665
- Final workflow commit: `b183b2bdf5c0bf64fbff823f185ac912c47ac420`; artifact ID: 13295291240; APK size: 58,116 bytes; SHA-256: `da37bb70c9d1516ab72d5654ff2b36dc14a052897ee99ed7f2af7762b9fbd7d3`.

## Phase 34 — Privacy-aware compatibility report (CI verified)

- Fixed the compatibility report so the default includes manufacturer/model, Android/API, ABIs and display metrics.
- Detailed device/product/board/hardware/build-fingerprint identifiers are excluded by default and included only after the user enables an explicit checkbox.
- The opt-in is not persisted; the share action regenerates the report from the current selection.
- Added unit tests for default redaction, opt-in details and missing values.
- Details: `docs/COMPATIBILITY_REPORT_PRIVACY.md`.
- GitHub Actions run #84 (ID 41771646620) passed debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41771646620
- Final source/test commit: `afde30bb8a0ff06c117f2c6c325b0d36de1bc4ff`; artifact ID: 13294793518; APK size: 58,121 bytes; SHA-256: `f2f62062162c6896a7c53afd38f59e1fd803df16a422829e667cc14ef9066b27`.

## Phase 33 — Physical device validation checklist (documentation)

- Added a structured checklist for spare-device installation, local feature checks, head-unit identity capture and vehicle-integration gates.
- Explicitly blocks sideloading to proprietary factory infotainment platforms and prohibits root/Xposed/system-file modifications as compatibility workarounds.
- Clarifies that GitHub Actions is build/unit-test evidence only; no physical head unit or vehicle has yet been tested.
- Details: `docs/DEVICE_VALIDATION_CHECKLIST.md`.

## Phase 32 — One-time vehicle snapshot JSON import (CI verified)

- Added document-picker import for vehicle telemetry snapshots, capped at 64 KiB.
- Strictly validates required fields, integer/boolean types and vehicle value ranges before display.
- Persists the imported snapshot locally; users can clear it by restoring demo values.
- This is a one-time snapshot only, not live CAN/OBD/ECU access and not access to protected /data/local/tmp/shared_files.
- Details: docs/VEHICLE_SNAPSHOT_JSON_IMPORT.md.
- GitHub Actions run #81 (ID 41763810035) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41763810035
- Final source/test commit: `7cc1d6d2862107daa50149efb1e53572fd08f5e4`; artifact ID: 13291648129; APK size: 57,156 bytes; SHA-256: `d21bcbf22b41f5b396dbe6afd2098aa88492a5038d19d0928ac170eb951fb113`.

## Phase 31 — Local startup audio selection and preview (CI verified)

- Added local audio selection through Android's document picker with persisted read-only URI access.
- Added explicit Preview/Stop/Clear controls and transient audio focus; releases MediaPlayer and audio focus when the Activity stops.
- No automatic boot playback, background audio service, network upload, or bundled copyrighted audio.
- Details: `docs/STARTUP_AUDIO_PREVIEW.md`.
- GitHub Actions run #77 (ID 41763603871) passed debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41763603871
- Source commit: `dea6984d388b23e92c6734ae0ffe78477138bb5b`; artifact ID: 13290724947; APK size: 55,671 bytes; SHA-256: `57b2880f22c7f3e49b6d24eb4be4d4e32a6e77d5041655d9e4db809ea680a50a`.

## Phase 30 — App-private cache maintenance (CI verified)

- Added a cache-size report and user-confirmed cleanup restricted to the app's private cache directory.
- Canonical-path checks keep traversal within the cache root; the root directory is retained.
- Presets, preferences, app files, user documents, shared storage, downloads, other apps and system files are not deleted. Temporary preview-share files may be removed.
- Added unit tests for nested size calculation, cleanup scope and missing directories.
- Details: `docs/APP_PRIVATE_CACHE_MAINTENANCE.md`.
- GitHub Actions run #76 (ID 41763472411) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41763472411
- Final source/test commit: `662b6deaaf9930616d9ebcdae4b5f0bc561784e1`; artifact ID: 13291915148; APK size: 53,351 bytes; SHA-256: `04b4971276d99615e6e37a9df14ddef499d13a3dc6ca2e1b210625dea63ec6c9`.

## Phase 29 — Head-unit compatibility diagnostics (CI verified)

- Extended read-only device identity with board, hardware and Android build fingerprint.
- Added real display resolution and density information plus a user-invoked share report.
- No root probes, protected file reads, OEM service calls or settings changes.
- Details: `docs/HEAD_UNIT_COMPATIBILITY_DIAGNOSTICS.md`.
- GitHub Actions run #72 (ID 41763378628) passed debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41763378628
- Final source commit: `2cea6a364aca070c6cb608216286d011c0ac81f7`.

## Phase 28 — Foreground hardware key event monitor (CI verified)

- Added an opt-in, foreground-only Android key event monitor in the steering-key simulator section.
- Displays key name/code and passes the event through unchanged; it does not execute mapped vehicle actions.
- Disabled by default and not persisted across launches. No accessibility service, global interception, root or Xposed hooks.
- Steering keys are observable only if the head unit routes them to this Activity.
- Details: `docs/FOREGROUND_KEY_EVENT_MONITOR.md`.
- GitHub Actions run #70 (ID 41763278421) passed debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41763278421
- Source commit: `8910155cc952919f82ab5df619df0b7fa67711f7`.

## Phase 27 — Vehicle telemetry adapter contract and demo (CI verified)

- Added immutable, range-validated `VehicleSnapshot` for speed, RPM, fuel, coolant temperature and gear.
- Added read-only `VehicleDataProvider` contract and static `MockVehicleDataProvider`.
- Added a clearly labelled offline demo panel and refresh control; no CAN/OBD/ECU/Bluetooth/OEM service is accessed and no vehicle commands are sent.
- Added unit tests for mock values, disconnected state and invalid ranges.
- Details: `docs/VEHICLE_DATA_ADAPTER.md`.
- GitHub Actions run #69 (ID 41763169818) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41763169818
- Final source/test commit: `54210046c14866bd59ee228c6bfb4496bd6793f4`; artifact ID: 13291577259; APK size: 50,453 bytes; SHA-256: `9f386f806126fbe0d49a15f8181e78a5f5e561f9d4e1badcb6131f01498e8d88`.

## Phase 26 — Navigation state provider contract and demo (CI verified)

- Added immutable, validated `NavigationState` snapshots and a read-only `NavigationStateProvider` interface.
- Added deterministic mock navigation states (maneuver, distance, ETA, inactive) and UI controls to advance/reset them.
- The UI labels these values as demo data. No undocumented Amap/Baidu APIs, notification scraping, location tracking, or vehicle bus connection is used.
- Added unit tests for state validation, route cycling, reset and inactive-state display.
- Details: `docs/NAVIGATION_STATE_ADAPTER.md`.
- Source/test commits: `8e53a80cca25240781f3931fe9d3c8899c5af1e1`, `36224317955a343a7d8d8ef9dfa86375d576f2fd`, `fefe200f745e53686111a07780764eb04424fcf5`, `d46526e779e47ee5429ee45f87baf2455939e85f`, `f717914e1814b38c9b638a97bcef625b9acb6b34`; CI pending.

## Phase 25 — Shareable meter preview image (CI verified)

- Added an explicit button to render the app's own meter preview into a PNG in private cache and share it via Android's share sheet.
- Added a non-exported, read-only content provider with temporary URI grants and path validation.
- Does not capture other apps/system UI, write to shared storage, or upload automatically.
- Details: `docs/METER_PREVIEW_IMAGE_SHARING.md`.
- GitHub Actions run #59 (ID 41762478075) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41762478075
- Final source commit: `2931c525758d6274ef3c3d4a1b25ea69679d64e6`; artifact ID: 13291706134; APK size: 47,527 bytes; SHA-256: `4a17240f78e0779af97ae0c77238027238c3c09d5335af8e0c81f45ccbeb824a`.

## Phase 24 — Explicit on-device voice recognition (CI verified)

- Added a user-started speech-recognition prototype for Android 12+ devices that expose an on-device recognition service.
- Requests microphone permission only after Start is tapped; provides Stop and destroys the recognizer when the Activity stops.
- Does not use cloud fallback, wake-word/background listening, network upload, or vehicle command execution.
- Details: `docs/ON_DEVICE_VOICE_ASSISTANT.md`.
- GitHub Actions run #56 (ID 41762298887) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41762298887
- Source/manifest commit: `5f89ec0122d7802cee50df22b5a3387af50bd0aa`; artifact ID: 13291341408; APK size: 45,071 bytes; SHA-256: `5a040c0929f306726fa8865c5673f16d3571845276ea79705ad5558b7f260186`.

## Phase 23 — App-scoped display controls (CI verified)

- Added an opt-in keep-screen-awake switch, active only while the app screen is visible.
- Added per-window brightness override with a system-default reset; does not alter global brightness settings.
- Details: `docs/APP_SCOPED_DISPLAY_CONTROLS.md`.
- GitHub Actions run #54 (ID 41762171192) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41762171192
- Final source commit: `9b43671e9a57a1a6a25cd82779fe63b3c0490807`; artifact ID: 13290488008; APK size: 42,675 bytes; SHA-256: `548f22269a0b54abe559841b714ff451577e027644ccf0816a239e7ed9aaffce`.

## Phase 22 — Read-only storage diagnostics (CI verified)

- Replaced the placeholder storage card with a live diagnostic report for Android API level, shared-storage state, app-private files/cache capacity, app-specific external storage and emulation state.
- Added refresh and user-invoked share controls.
- No broad storage permission, arbitrary file enumeration, SD-card repair, platform.xml modification, partition remount, or deletion is performed.
- Details: `docs/READ_ONLY_STORAGE_DIAGNOSTICS.md`.
- GitHub Actions run #52 (ID 41762049288) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41762049288
- Source commit: `26ca5112ba0954602d48caddbf1bce0f4baf2610`; artifact ID: 13291800139; APK size: 40,827 bytes; SHA-256: `77629511b839c838f4c73d3fd67347a8f1106205062c85bf5cf7f2083d4a0b9c`.

## Phase 21 — User-confirmed local APK installer (CI verified)

- Added document-picker selection for a local APK and handoff to Android's package installer.
- Android 8+ unknown-source authorization is handled through the system app-specific setting; installation itself remains a system-confirmed user action.
- No silent install, root, background install, or APK download is implemented.
- Details: `docs/LOCAL_APK_INSTALLER.md`.
- The selected document URI is persisted across the system settings handoff when the provider supports persistable grants.
- GitHub Actions run #51 (ID 41761942861) passed unit tests, debug APK assembly and artifact upload: https://github.com/qiangge5586-a11y/1/actions/runs/41761942861
- Commit: `f5f42fde32f929ac527563033af101bacc441564`; artifact ID: 13291475493; APK size: 39,348 bytes; SHA-256: `6ca723b650f900f3530dbeb7cb886926faa3c1ab1bf1aa0a0573a89a6e3ec7b0`.

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
- GitHub Actions run #40 (ID 41759036834) passed with unit tests and debug APK assembly.
- Details: `docs/NAMED_METER_PRESET_LIBRARY.md`.


## Phase 16 — Multi-widget meter editor (CI verified)

- Parser now validates every views[] entry (up to 32 widgets), not just the first.
- Preview canvas draws all widgets and supports per-widget alignment, size and color.
- Editor supports selecting, adding and removing widgets, plus editing x/y position and width/height using normalized coordinates.
- Save widget layout stores widget count and widget settings locally; reset restores the bundled preset.
- Unit tests cover multiple widgets, rendering, alignment and invalid layouts.
- GitHub Actions run #36 (ID 41757049880) passed with the multi-widget editor and tests included.

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

1. Obtain a fresh readable JADX source export and review high-value original classes: MyService, MeterActivity, NavigatorActivity, RemapKeysActivity, StartupActivity and VoiceAssistantService. The supplied report is valuable evidence, but its raw source tree is not currently stored in Library or GitHub.
2. Manually test local APK selection and system installation confirmation on a spare Android device; CI verifies compilation, not an actual package installation.
3. Manually test phases 19–22 (startup video preview, JSON file import/export, local APK installation and storage diagnostics) on a spare Android device.
4. Identify the exact 2019 Civic factory head-unit model, firmware, Android API level and ABI; do not infer compatibility from vendor intent strings.
5. Implement navigation-state and vehicle-data adapters only after documented interfaces are verified; until then, keep meter/navigation values clearly marked as simulated.
6. Add remaining safe feature modules (additional display preferences and navigation-state mock/adapter boundary) with permission explanations and tests.


<!-- Previous roadmap retained below for historical context. -->

1. Obtain readable JADX output and review high-value original methods, especially `MyService`, `MeterActivity`, `NavigatorActivity`, `RemapKeysActivity`, `StartupActivity` and `VoiceAssistantService`.
2. Extend the meter editor to multiple widgets, drag positioning, font/alignment/stroke/shadow/background assets and named presets with import/export.
3. Add safe, explicit audio-focus controls and startup preferences using public Android APIs.
4. Build a device compatibility matrix for the exact Civic factory head-unit model/firmware.
5. Test the debug APK on a spare Android device first; do not modify the live vehicle head unit until a documented interface and rollback path are confirmed.
