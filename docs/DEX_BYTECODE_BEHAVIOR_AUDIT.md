# DEX Bytecode Behavior Audit (Phase 9)

Date: 2026-11-04  
Input: user-provided `本田黑科技7.6.0_可联网_去注销_任意.apk`  
Package/version: `cn.autohack.hondahack` / `7.6.0`  
APK SHA-256: `e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe`

## Scope and confidence

This phase parses DEX string/type/prototype/field/method/class tables and encoded class_data, then performs a preliminary instruction-reference scan of selected code_item bodies. It collects const-string references and invoke method references from the relevant classes.

This is stronger than a string-table-only inventory, but it is **not yet a full Java decompilation**. Obfuscated methods remain named `a`, `b`, etc.; the instruction scanner is a triage aid and does not prove complete control flow, runtime behavior, device compatibility, or the exact meaning of every vendor call. JADX remains the next step for readable source/control-flow review. The official JADX project documents APK/DEX decompilation, resource decoding and optional call-graph output; it also warns that decompilation may be incomplete: https://github.com/skylot/jadx

## Confirmed feature-path indicators

| Area | Bytecode evidence observed | Rebuild implication |
|---|---|---|
| Main service orchestration | `MyService.onStartCommand` references many app-defined actions: startup-video control, orientation lock, audio focus, source change, media playback, speed/camera voice prompts, rear-camera toggle and voice-assistant state | Implement an explicit, typed internal event bus. Do not reproduce implicit privileged shell behavior. |
| Meter | `MeterActivity.onCreate` references `enable_custom_meter`, `nav_in_dashboard`, `enable_screen_cast`; service action strings include `SHOW_METER`, `SHOW_TEST_METER`, `HIDE_TEST_METER` | Build an offline meter designer and mock-data preview first; keep vehicle telemetry behind a separately validated adapter. |
| Navigation | `NavigatorActivity` references `nav_in_dashboard`, `auto_kill_amap2`, `enable_nav_volume_control`, `fix_baidu_offline_map`; `MyService` references AmapAuto package/service and create/destroy actions | Treat map-app integration as optional, package-detected and user-authorized. Do not force-stop other apps in the safe baseline. |
| Steering-key mapping | `RemapKeysActivity` references `enable_key_remapping`, `custom_key_`, `set_audio_button_app`; action names include media previous/next/play/pause and custom actions for meter, voice, rear camera | Rebuild a configurable mapping UI and test simulator. Real key capture requires a documented/public head-unit input API. |
| Startup automation | `StartupActivity` references `auto_start_app`, delay settings, startup video, restore-last-function and auto-confirm settings | Provide transparent, opt-in Android-side startup preferences only; never auto-accept agreements or bypass vendor confirmation. |
| Audio and voice | `MyService.onStartCommand` references request/abandon audio focus, volume decrease/restore, road/speed/camera prompts and voice-assistant actions; `VoiceAssistantService` manages an overlay window | Implement Android-supported audio focus and an explicit foreground/overlay permission flow. No hidden overlay or always-on microphone. |
| GPS/time | `MyService.onLocationChanged` references GPS status, bearing, calibration and date-setting command strings | Keep GPS read-only; do not change system clock. Use Android location/time APIs and user-granted permissions only. |
| Receiver lifecycle | `MyBroadcastReceiver.onReceive` references package replaced/removed, boot complete and removable-storage mount/eject events | Limit to documented broadcasts and app-owned state. |
| Maintenance/settings | `SettingsActivity` references app/system log views, debug/test flags and ADB TCP/IP setting; `OptimizeActivity` includes cache/storage-related preferences | Keep diagnostics local and read-only by default. Cache cleanup must target app-owned cache only. |

## High-risk implementation indicators (quarantined)

Static instruction references include command strings such as:
- `busybox pkill`
- `am force-stop ...`
- `pm disable` / `pm enable`
- `am start -n ...`
- `/system/bin/input keyevent ...`
- `date -s ...`

These are recorded only as reverse-engineering evidence. The rebuild will **not** execute or port shell commands that kill/disable other apps, inject key events, change system time, bypass Android permission boundaries, install privileged hooks, or alter system partitions. Any legitimate equivalent must use public Android APIs, explicit user action and a validated device-specific adapter.

## Key class/method observations

- `MeterActivity`: preference screen lifecycle plus preference-change listener; meter toggles are present.
- `NavigatorActivity`: preference screen plus USB mount-path candidates and navigation settings.
- `RemapKeysActivity`: mapping preferences, custom-key entries and media-control action names.
- `StartupActivity`: startup app, delays, startup video and restore-last-function preferences.
- `MyService`: large central service; `onStartCommand` dispatches a broad action vocabulary; other methods reference startup-app launch, Wi-Fi setting, rear-camera display, AmapAuto and emergency-key press counting.
- `MyBroadcastReceiver`: package/storage/boot lifecycle event handling.
- `VoiceAssistantService`: overlay window creation/removal and listener/callback management.

## Next steps

1. Obtain/run JADX 1.5.6 in the analysis environment and export source/resources plus call graph.
2. Decompile the high-value classes first: `MyService`, `MyBroadcastReceiver`, `MeterActivity`, `NavigatorActivity`, `RemapKeysActivity`, `StartupActivity`, `VoiceAssistantService`.
3. Replace inferred feature notes with method-level evidence and confidence labels.
4. Add unit tests for the independent safe modules (meter layout parsing, action routing, key-map persistence) before implementing vehicle-facing integrations.

## Reproducibility note

The source APK was inspected as a ZIP/DEX artifact. No APK code was executed and no vehicle/head-unit was modified or contacted during this audit.
