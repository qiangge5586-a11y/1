# Original APK method-level audit — 2026-11-04

## Input and toolchain integrity

- Input: user-supplied `本田黑科技7.6.0_可联网_去注销_任意.apk`.
- Input SHA-256: `e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe` (matches the Library/reverse-engineering record).
- Decompiler: official JADX 1.5.3.
- Official release archive SHA-256: `8280f3799c0273fe797a2bcd90258c943e451fd195f13d05400de5e6451d15ec`.
- The archive checksum was verified by the dedicated GitHub Actions workflow before download.
- Standard output: 1,286 Java source files total; 381 under `cn/autohack`; 9.4 MB decompiled source tree.
- Standard mode contains 31 `Method not decompiled` placeholders across 23 files and no `JADX ERROR` markers.
- A second pass with `--show-bad-code --comments-level debug` emitted source bodies for those fallback methods (zero remaining placeholders). This mode may emit inconsistent Java; those methods are not considered fully verified until checked against DEX/smali.

## Confirmed class and method observations

### 1. Main entry and menu

- `cn.autohack.hondahack.MainActivity.onCreate` sets a custom ActionBar and loads `C0234na` as the main `PreferenceFragment`.
- `C0234na.onPreferenceTreeClick` routes to key remapping, voice assistant, meter, app management, startup, navigation, autorun, optimize, fix, advanced, settings and about screens.
- `MeterActivity.onCreate` loads `R.xml.pref_meter` and wires listeners for `enable_custom_meter`, `nav_in_dashboard` and `enable_screen_cast`. It is primarily a preference screen, not the meter renderer itself.

### 2. Root/Xposed installation path — do not reproduce

- `C0234na.c(Context, boolean)` is an optimization/setup path that assembles and executes shell commands through the privileged command helper.
- Confirmed command families include enabling persistent ADB/TCP port 5555, remounting `/system` read-write, replacing `/system/bin/app_process`, installing Xposed bridge files, editing recovery startup scripts, replacing `/system/etc/permissions/platform.xml`, disabling Android media components, granting `READ_LOGS`, and installing a vendor whitelist APK.
- This is a system-modification/bootstrap workflow, not ordinary application configuration. It is excluded from this rebuild.
- `C0170b` is the Xposed load-package hook entry. It hooks framework/application methods across processes and contains integration branches for navigation, audio focus, vendor services and vehicle-specific packages.
- `C0170b.a(int)` reflects into `InputManager` and injects synthetic key down/up events. This is not a public third-party app API.

### 3. Modified licensing and remote revoke behavior

- `C0270ub.c(Context, String)` returns `true` unconditionally in this supplied modified APK (JADX notes 18 unreachable blocks / 52 removed instructions).
- `C0270ub.b(Context)` returns `false` unconditionally (JADX notes 4 unreachable blocks / 27 removed instructions).
- `C0270ub.h(Context)` reads `/system/etc/hondahack/auth-code` before falling back to a private preference.
- These observations confirm the supplied APK is a modified/repacked sample; they do not describe the clean vendor implementation. The rebuild will not reproduce arbitrary-code activation, signature spoofing or remote revocation.

### 4. Steering-wheel key mapping

- `RemapKeysActivity` configures ten custom key slots (`custom_key_0` through `custom_key_9`) and supports app-launch and special-action mappings.
- The capture dialog polls the shared last-key field every second.
- `MyApplication` runs a background logcat reader for vendor tags such as `StrgSwApService`, parses key down/up and scan-code data, then looks up the configured mapping.
- The same path can send mapped key actions back through a bound service or synthesize system key events.
- This depends on privileged log access/vendor logs and Xposed/InputManager hooks. A public foreground Activity cannot be assumed to receive steering-wheel events from a factory head unit.

### 5. Vehicle and vendor services

- `MyApplication.onCreate` starts and binds `MyService`, initializes preferences and background tasks.
- On the Mitsubishi/Clarion capability branch, it binds service interfaces named `IAvApService`, `IVehicleInfoManagerApService`, `ICameraAPService` and `IVideoManagerApService`.
- `ServiceConnectionC0279wa` maps those binders to AV, vehicle-information, camera and video interfaces.
- The raw DEX fallback pass confirms `C0269ua.getMeterLayout()` returns `meter_civic` for `civic`/`clarity`, `meter_crv` for `crv`/`breeze`, and `meter_avancier` for `avancier`/`avancier240`/`urv`/`cdx`. This proves the APK has a Civic-specific meter layout branch, but not that a 2019 Civic factory head unit exposes the required vendor services or can install/run the APK.

### 5a. Vehicle telemetry file bridge

- `RunnableC0284xa.run()` creates `/data/local/tmp/shared_files`, touches the telemetry files and applies `chmod -R 777`.
- `C0185dc` uses `RandomAccessFile` plus file locks to exchange integer values through that directory.
- `C0269ua` polls `SET_SPEED`, `DISTANCE_TO_EMPTY`, `TRIP_A_DISTANCE`, unit codes, average fuel-efficiency values and coolant temperature.
- Xposed callback classes write selected values such as average fuel efficiency and coolant temperature into the same files.
- This is a privileged local file bridge, not a public Honda/OBD-II interface. It is excluded from the rebuild. See `docs/VEHICLE_DATA_TRANSPORT_AUDIT.md`.

### 6. Navigation and audio integration

- `C0170b` contains process hooks for Amap (`com.autonavi.amapauto`) and Waze, and hooks audio-focus request/abandon methods.
- Navigation data is translated into app broadcasts such as `UPDATE_NAVI` and `UPDATE_NAVI_AMAP`, carrying maneuver, road, distance, camera and speed fields.
- `MyService.onLocationChanged` emits GPS availability and bearing updates.
- The navigation bridge depends on Xposed hooks into third-party app internals and vendor APIs; it is not a stable public integration contract.
- The rebuild's current navigation provider remains a typed mock/demo adapter until a documented interface is confirmed.

### 7. Startup video and voice assistant

- `StartupActivity` controls auto-start app selection and launches `StartupVideoActivity` for video settings.
- `SelectLocalVideoActivity` scans local MP4 files in selected storage/download locations.
- `SelectOnlineVideoActivity` hosts a JavaScript-enabled WebView and handles custom preview/download URL schemes. This online flow is not copied into the offline-first rebuild.
- `VoiceAssistantActivity` is a preference screen; its test action calls `MyService.a(false)`. It is not itself the speech-recognition engine.
- The rebuild uses user-selected local media previews and explicit on-device speech-recognition controls instead of hidden boot/background behavior.

### 8. Civic meter selection and telemetry decoding cross-check

- A normal JADX Java decompilation of `C0269ua.getMeterLayout()` incorrectly returned `meter_none` on all branches. This was not accepted as ground truth.
- A class-only JADX pass using `--decompilation-mode fallback` exposed the raw DEX-derived instruction flow. The verified return branches are:
  - `civic` / `clarity` → `meter_civic`.
  - `crv` / `breeze` → `meter_crv`.
  - `avancier` / `avancier240` / `urv` / `cdx` → `meter_avancier`.
- The same class confirms a 500 ms polling loop for speed, with other telemetry fields refreshed every twentieth iteration (approximately every 10 seconds while the loop remains active).
- `C0185dc` stores each integer under `/data/local/tmp/shared_files/<KEY>` and uses file-channel locks. `RunnableC0284xa` creates the directory/files and applies world-writable permissions.
- The verified field transforms include distance unit code 0=km/other=mi; fuel codes 0=km/L, 1=mpg, 2=imperial mpg, other=L/100km; coolant raw value minus 40 then Celsius/Fahrenheit conversion; Trip A distance and fuel values divided by 10, with a special large-distance display branch.
- Rebuild implementation: `CivicTelemetryDecoder` is a pure value transformer with tests. It intentionally does not implement the original privileged file IPC.

## Remaining DEX-level verification

The second JADX pass can display the 31 fallback methods, but some are marked by JADX as inconsistent or have difficult switch reconstruction. Prioritize DEX/smali cross-checks for:

1. `C0234na.onCreate` and `onPreferenceTreeClick`.
2. `C0170b.handleLoadPackage` and the nested Xposed hook callbacks.
3. `MyApplication.a(Context, SharedPreferences, String)` and service initialization helpers.
4. `ServiceConnectionC0279wa.onServiceConnected/onServiceDisconnected`.
5. `NavigatorActivity.onPreferenceTreeClick`, `StartupVideoActivity.onPreferenceTreeClick`, and `RemapKeysActivity.onPreferenceTreeClick`.
6. `W` mapping store and vehicle capability selection.

## Rebuild decisions

- Reuse feature concepts and data formats only after review.
- Do not transplant root/Xposed, hidden API, logcat interception, persistent ADB, system-file writes, signature spoofing, remote destructive actions or undocumented vendor interfaces.
- Continue implementing public Android APIs, explicit user actions, offline demo adapters and read-only integration boundaries.
- No Civic OEM head-unit compatibility claim is made from these source observations.
