# Feature Traceability — HondaHack 7.6.0

This matrix separates behavior observed in the 7.6.0 reverse-engineering report from the safe rebuild target. It is a planning artifact, not proof that the vehicle exposes the required interfaces.

| Original feature | Evidence in 7.6.0 report | Rebuild module | Initial implementation boundary | Status |
|---|---|---|---|---|
| Steering-wheel key remapping | MyService key dispatch; RemapKeysActivity | `feature-keymap` | UI mapping model, key-event simulator, app launch intents where Android permits | Partial — four-key simulator plus opt-in foreground Android key-event monitor; no global capture or vehicle command execution |
| Startup video / voice | PLAY_STARTUP_VIDEO; StartupVideoActivity; audio assets | `feature-startup` | User-selected local media, preview and user-controlled playback; no boot takeover initially | Partial — local video and audio selection/preview; no boot playback or automatic voice |
| Instrument cluster / meters | MeterActivity; SHOW_METER/UPDATE_METER; meter JSON; nine shared vehicle data files | `feature-meter` + `vehicle-adapter` | Demo data provider and read-only adapter interface; no guessed CAN/AIDL protocol | Partial — multi-widget mock-data editor, named JSON presets and validated vehicle-data provider contract/mock and user-imported JSON snapshots; no live vehicle telemetry |
| Navigation presentation | START_NAVI/STOP_NAVI/UPDATE_NAVI broadcasts; AMap/Waze hooks | `feature-navigation` | Receive documented app intents only; show simulated navigation state if no supported interface | Partial — detects/launches supported installed navigation apps; typed provider contract and mock state demo; no live nav-state bridge |
| Navigation audio ducking | REQUEST_AUDIO_FOCUS/ABANDON_AUDIO_FOCUS; music_volume_decrease | `feature-audio` | Android AudioFocus API with bounded, reversible volume behavior | Partial — explicit transient audio focus; no volume manipulation |
| Local APK install | AppsInstallLocalActivity | `feature-app-manager` | Use Android package installer user confirmation; no silent installs | Partial — local document picker and system-confirmed install handoff; device validation pending |
| Online app catalog | AppsInstallOnlineActivity; appstore endpoint | `feature-app-catalog` | Omit until endpoint authenticity and privacy are established; use user-provided APKs first | Deferred |
| SD-card write repair | SdcardActivity; platform.xml overwrite | `feature-storage-diagnostics` | Diagnose scoped-storage/permission state and explain supported settings; never overwrite platform.xml | Partial — app-private/scoped storage diagnostics; no arbitrary SD-card repair |
| Startup / autorun manager | StartupActivity; AutorunManagerActivity; boot receiver | `feature-startup-manager` | List this app's own startup behavior and supported app settings only; no force-stop/disable of system apps | Planned |
| Cleanup / optimization | OptimizeActivity; CleanInternalSdActivity; process killing | `feature-maintenance` | Storage usage report and app-owned cache cleanup; no arbitrary process killing | Planned |
| Display / screensaver / screenshots | SettingsActivity; screenshot hook; screen saver action | `feature-display` | In-app display preferences and Android-supported screenshot/share flow | Partial — app-window brightness, visible-only keep-awake, and explicit meter-preview PNG sharing; no system screenshots/screensaver |
| Voice assistant | VoiceAssistantActivity/Service; RECORD_AUDIO | `feature-voice` | Explicit microphone permission, visible listening state, on-device/mock first | Partial — explicit on-device speech-to-text prototype on Android 12+; no voice commands |
| Mitsubishi / Clarion adapters | AIDL service names, power broadcasts, whitelist XML | `vehicle-adapter-mitsubishi`, `vehicle-adapter-clarion` | Separate optional adapters; disabled unless exact head-unit identity and interface are verified | Deferred |
| Emergency mode | 5-tap sequence; motion-state guard | `feature-emergency` | UI-only demo until motion signal is verified; never bypass driving safety | Deferred |
| Root / Xposed framework installer | app_process replacement, XposedBridge, platform.xml, su commands | Not in default app | Excluded from baseline; any future lab-only study isolated from vehicle installation | Excluded |
| Remote authorization / revocation | activate.php, blocked flag, destructive cleanup | Not in rebuild | No remote kill switch, remote uninstall, or remote root deletion | Excluded |
| Signature spoofing / hook | PmsHookApplication and PackageManager proxy | Not in rebuild | No signature forgery or platform package-manager hooking | Excluded |

## Current implementation status

The safe baseline now includes the app shell, read-only capability snapshot, multi-widget offline meter editor, four-key mapping simulator, navigation-app launcher, user-confirmed local APK installer, read-only storage diagnostics, app-scoped display controls, on-device voice recognition, meter-preview PNG sharing, navigation state provider contract/demo, vehicle telemetry provider contract/demo, one-time telemetry JSON import, foreground key event monitor, named preset management with JSON paste/share and direct document import/export, explicit audio-focus controls, startup editor preference, and local video/audio selection and preview. These are independent safe implementations, not proof of full feature parity. GitHub Actions runs #47 and #48 verify the startup-video and direct JSON file workflows respectively. The vehicle adapter, physical steering-key capture, navigation-state bridge, voice assistant, app manager, and vehicle telemetry remain unimplemented or deferred.

## Common architecture

- `app`: navigation shell, onboarding, settings, permission explanations.
- `core-capability`: Android API level, ABI, manufacturer/model, available system services; read-only.
- `core-preferences`: typed settings and migrations.
- `core-vehicle-api`: documented adapter contract and mock implementation.
- `feature-*`: isolated UI and domain logic.
- `vehicle-adapter-*`: opt-in vendor-specific adapters, separately permission-gated.
- `test-fixtures`: simulated vehicle events and meter streams.

## Vehicle compatibility rule

The intended first test target is a 2019 Honda Civic factory head unit. The reverse-engineering report lists multiple head-unit ecosystems, but does not establish that this specific unit exposes every service. Therefore:
1. Identify the head-unit model, Android build fingerprint, API level and CPU ABI from read-only diagnostics.
2. Verify each service/action on bench hardware while parked.
3. Keep unsupported features visibly disabled rather than guessing protocol names or issuing root commands.
4. Do not install any system-level component until a separately verified full backup and recovery route exists.

## Acceptance criteria for the first safe baseline

- App installs and launches without root.
- Capability report is read-only and exportable by user action.
- Demo mode can show steering-key mapping, meter data and navigation state without a vehicle connection.
- Each feature explains the exact permission it requests.
- No network calls are required for core functions.
- No Xposed, app_process, platform.xml, silent package install, remote revocation or system partition writes.


## Fresh source audit notes (2026-11-04)

- Main UI flow confirmed in `MainActivity` and `C0234na`; `MeterActivity` is the settings surface.
- Original steering-key capture depends on vendor logcat tags, privileged log access, Xposed hooks and synthetic InputManager events; the rebuild intentionally uses only its simulator and foreground-only pass-through key monitor.
- Original meter/navigation data hooks are coupled to Xposed hooks into Amap/Waze internals and Mitsubishi/Clarion binder services. The rebuild's navigation/vehicle provider interfaces remain mock/demo until a documented read-only integration is verified.
- The supplied modified APK's `C0270ub` license stubs and `C0234na.c` privileged bootstrap are not reproduced.
- See `docs/ORIGINAL_APK_METHOD_AUDIT_2026-11-04.md` for method-level evidence and remaining DEX checks.


## Civic meter resource-derived fields

The fresh resource audit identified a 584×215 px Civic layout and the original six vehicle-information keys. The rebuild now has a text-only, offline Civic layout template and mock token values for those fields, plus navigation/compass/speed-limit indicators. This is a preview of the resource field set, not a claim of pixel-perfect recreation or 2019 Civic head-unit compatibility. See `docs/CIVIC_METER_LAYOUT_AUDIT.md`.


## Civic meter demo units

The source-derived Civic template exposes explicit demo unit tokens for distance (km), fuel efficiency (L/100km) and coolant temperature (°C). These metric labels are fixed preview defaults only; they are not read from the vehicle's configuration. See `docs/CIVIC_METER_LAYOUT_AUDIT.md`.


## Selectable meter demo unit systems

The Civic meter preview supports metric, km/L, US customary and imperial unit display modes. Values are converted from fixed sample data and the selection is stored only in the editor preferences. It does not access or modify vehicle settings.
