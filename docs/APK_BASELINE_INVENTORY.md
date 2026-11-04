# APK Baseline Inventory — HondaHack 7.6.0

## Sample identity

- Library file: `本田黑科技7.6.0_可联网_去注销_任意.apk`
- Library path: `/本田黑科技7.6.0_可联网_去注销_任意.apk`
- Size: 3,893,398 bytes
- SHA-256: `e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe`
- APK/ZIP structure: valid; 986 entries
- Package string in manifest: `cn.autohack.hondahack`
- Version name in manifest string pool: `7.6.0`
- Primary DEX: `classes.dex`, 1,909,752 bytes
- Resources: 975 entries under `res/`; `resources.arsc` is 648,100 bytes
- Assets: `assets/VERSION` (content `54`), `assets/xposed_init` (content `cn.autohack.hondahack.b`)

## Native libraries

| ABI | Library | Size (bytes) |
|---|---|---:|
| armeabi-v7a | libnative.so | 120,464 |
| armeabi-v7a | libmp3lame.so | 157,368 |
| armeabi-v7a | libxposed.so | 108,124 |

Only ARMv7 native libraries were found in this sample. This is a compatibility consideration for newer head units and 64-bit-only environments.

## Signature evidence

The APK contains legacy signature metadata under `META-INF/` (`CERT.SF`, `CERT.RSA`, `MANIFEST.MF`). The certificate extracted from `CERT.RSA` reports:

- Subject / issuer: `CN=np, OU=np, O=np`
- Serial: `45ff9a3`
- Validity: 2021-04-25 through 3020-08-26
- SHA-1: `26:B0:2D:23:35:09:F4:AE:CF:56:98:00:32:34:34:56:CE:AB:72:2A`
- SHA-256: `49:26:82:F8:77:60:7E:E9:9D:F2:DD:D2:BD:59:53:FD:72:7B:DF:6E:19:D3:97:DE:9D:BB:AF:D5:82:BC:AD:75`
- Signature algorithm: `SHA512withRSA`; RSA 2048-bit public key

This is a third-party/repackaging certificate, not evidence of an original Honda/autohack publisher signature. The reverse-engineering report describes an application-level signature-hook component; do not rely on the app's own signature checks as proof of publisher authenticity.

## Manifest string-pool findings

The binary manifest was confirmed present and its UTF-16 string pool was inspected. The following package, version, permission, component and intent strings were recovered. A full decoded XML export is still pending because apktool/aapt/jadx are not installed in the current analysis environment; therefore this is a string-pool inventory, not a complete semantic manifest decode.

### Permission strings observed

- ACCESS_COARSE_LOCATION
- ACCESS_FINE_LOCATION
- ACCESS_NETWORK_STATE
- ACCESS_SUPERUSER
- ACCESS_WIFI_STATE
- BLUETOOTH
- BLUETOOTH_ADMIN
- CHANGE_WIFI_STATE
- CLEAR_APP_CACHE
- GET_TASKS
- INTERNET
- KILL_BACKGROUND_PROCESSES
- MODIFY_AUDIO_SETTINGS
- MOUNT_UNMOUNT_FILESYSTEMS
- READ_LOGS
- RECEIVE_BOOT_COMPLETED
- RECORD_AUDIO
- REORDER_TASKS
- RESTART_PACKAGES
- SYSTEM_ALERT_WINDOW
- WRITE_EXTERNAL_STORAGE

### Activities/components observed

- AboutActivity
- AdvancedActivity
- AppsActivity
- AppsInstallLocalActivity
- AppsInstallOnlineActivity
- AutorunManagerActivity
- CleanInternalSdActivity
- EmergencyActivity
- FixActivity
- LogActivity
- MainActivity
- MeterActivity
- NavigatorActivity
- OptimizeActivity
- RemapKeysActivity
- SdcardActivity
- SelectLocalVideoActivity
- SelectOnlineVideoActivity
- SettingsActivity
- StartupActivity
- StartupVideoActivity
- TestActivity
- VoiceAssistantActivity
- MyService
- MyBroadcastReceiver
- VoiceAssistantService
- CrashReporterInitProvider
- Application class: `np.manager.signature.PmsHookApplication`

### Intent / integration strings observed

- Android boot, shutdown, power-connected, media mount/unmount/eject, package removed/replaced, reboot, user-present and connectivity/Wi-Fi state actions
- Clarion: `com.clarion.intent.action.WARP_BOOT`
- Mitsubishi HMI power manager start/restart actions
- Navigation package identifiers including AMap, Baidu Auto, Google Maps, HERE, iGO, Sygic and Waze

## Phase 2 conclusions

1. The verified APK is the exact 7.6.0 sample analyzed by the supplied report, based on the matching SHA-256.
2. The app is a single-DEX, ARMv7-native application with Xposed-related payload markers.
3. The manifest string pool exposes a broad privileged permission surface, including log reading, microphone, location, overlay, storage and superuser-related permissions.
4. The signer is the NP repackaging certificate described by the report, not a trustworthy original publisher identity.
5. A full XML manifest decode and DEX method-level mapping remain pending; no claims are made that the current string-pool inventory is a complete manifest parse.

## Safety / rebuild implications

- Do not copy the signature-hook or Xposed bootstrap into the clean rebuild.
- Do not automatically grant root, log, microphone, overlay or storage permissions.
- Implement only the permissions required by each independently rebuilt feature, with runtime explanation and user consent.
- Keep the ARMv7 libraries isolated for analysis; do not load them on a vehicle head unit without ABI and provenance validation.
- Treat the original APK as a read-only forensic baseline. Rebuild functionality independently and document behavior rather than shipping a modified copy of this sample.


## Additional DEX structural pass

A direct DEX header/table parse confirms:
- DEX format magic: `dex\n035`.
- 12,302 string IDs.
- 2,555 type IDs.
- 3,490 proto IDs.
- 13,775 field IDs.
- 15,752 method IDs.
- 1,770 class definitions total.
- 427 classes under the `cn.autohack` namespace (353 under `cn.autohack.hondahack`; the remainder are utility and voice-assistant packages).

The DEX string table also contains internal action identifiers for meter updates, navigation start/stop, audio focus, startup video, key remapping and voice-assistant state changes. It contains references to network endpoints and privileged filesystem operations; these are treated as forensic indicators only and are not copied into the clean rebuild.

This pass is structural/string-based. It does not replace decompilation or control-flow analysis.
