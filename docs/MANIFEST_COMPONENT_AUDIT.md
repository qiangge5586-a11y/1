# Original APK Manifest Component Audit

Date: 2026-11-04

## Method and sample

Parsed the binary Android XML chunk structure in the verified APK's `AndroidManifest.xml` directly (string pool, start-element and attribute records). This is a structural decode, not an apktool/jadx resource rebuild. Sample SHA-256:

`e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe`

## Manifest identity

- Package: `cn.autohack.hondahack`
- Version code: `1`
- Version name: `7.6.0`
- minSdkVersion: `17`
- targetSdkVersion: `17`
- Application class: `np.manager.signature.PmsHookApplication`

## Declared permissions (22)

- `android.permission.ACCESS_SUPERUSER`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.BLUETOOTH_ADMIN`
- `android.permission.CHANGE_WIFI_STATE`
- `android.permission.REORDER_TASKS`
- `android.permission.WRITE_EXTERNAL_STORAGE`
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.ACCESS_WIFI_STATE`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.INTERNET`
- `android.permission.BLUETOOTH`
- `android.permission.GET_TASKS`
- `android.permission.RESTART_PACKAGES`
- `android.permission.KILL_BACKGROUND_PROCESSES`
- `android.permission.READ_LOGS`
- `android.permission.MOUNT_UNMOUNT_FILESYSTEMS`
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `android.permission.CLEAR_APP_CACHE`
- `com.android.launcher.permission.INSTALL_SHORTCUT`
- `android.permission.RECORD_AUDIO`

## Components

### Activities (23)

- `cn.autohack.hondahack.MainActivity` (MAIN/LAUNCHER)
- `cn.autohack.hondahack.RemapKeysActivity`
- `cn.autohack.hondahack.SdcardActivity`
- `cn.autohack.hondahack.SettingsActivity`
- `cn.autohack.hondahack.AppsActivity`
- `cn.autohack.hondahack.StartupActivity`
- `cn.autohack.hondahack.AutorunManagerActivity`
- `cn.autohack.hondahack.NavigatorActivity`
- `cn.autohack.hondahack.OptimizeActivity`
- `cn.autohack.hondahack.FixActivity`
- `cn.autohack.hondahack.AboutActivity`
- `cn.autohack.hondahack.AppsInstallLocalActivity`
- `cn.autohack.hondahack.EmergencyActivity`
- `cn.autohack.hondahack.AppsInstallOnlineActivity`
- `cn.autohack.hondahack.TestActivity`
- `cn.autohack.hondahack.StartupVideoActivity`
- `cn.autohack.hondahack.SelectLocalVideoActivity`
- `cn.autohack.hondahack.SelectOnlineVideoActivity`
- `cn.autohack.hondahack.CleanInternalSdActivity`
- `cn.autohack.hondahack.LogActivity`
- `cn.autohack.hondahack.AdvancedActivity`
- `cn.autohack.hondahack.MeterActivity`
- `cn.autohack.hondahack.VoiceAssistantActivity`

### Services (2)

- `cn.autohack.hondahack.MyService`
- `cn.autohack.voiceassistant.VoiceAssistantService`

### Receiver (1)

- `cn.autohack.hondahack.MyBroadcastReceiver`

### Provider (1)

- `com.balsikandar.crashreporter.CrashReporterInitProvider`

The manifest contains 27 declared components in these categories. Exported/enabled defaults and some boolean attributes need careful typed-value decoding before making runtime accessibility claims.

## Intent filters and platform integration indicators

The receiver declares filters for boot completion, shutdown/reboot, media mount/unmount/eject, package replacement/removal, power connected, user-present, connectivity/Wi-Fi state changes, and an internal orientation-lock action. It also contains vendor-specific boot integration strings:

- `com.mitsubishielectric.ada.appservice.powermanager.ACTION_HMI_SERVICE_START`
- `com.mitsubishielectric.ada.appservice.powermanager.ACTION_HMI_SERVICE_RESTART`
- `com.clarion.intent.action.WARP_BOOT`

These strings indicate integration targets in the manifest; they do not prove that the current user's Civic head unit exposes those interfaces.

## Rebuild implications

1. The original app targets Android API 17-era behavior (min/target SDK 17). This is materially different from modern Android permission and background-execution rules.
2. The manifest requests 22 permissions, several of which are privileged, obsolete, restricted, or unavailable to ordinary third-party apps on current Android.
3. The application class name indicates a signature-hooking layer. Keep this out of the clean-room rebuild.
4. The receiver has broad boot, power, package and media event hooks. Rebuild only the specific event handling that is demonstrably required and disclose background behavior.
5. Do not assume the Mitsubishi/Clarion intent filters match the 2019 Civic factory head unit. Device model, firmware and exposed intents must be measured first.
6. The baseline rebuild should remain permission-minimal and must not request superuser, READ_LOGS, package killing, filesystem mount, or signature-hook capabilities.

## Accuracy note

Earlier inventory described the manifest section as string-pool evidence only. This report supersedes that limitation for the package/version/SDK/permission/component and intent-filter fields listed above: those were decoded from binary XML element and attribute records. It is still not a full apktool resource decode or method-level behavior proof.
