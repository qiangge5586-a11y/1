# Opt-in Navigation App Detection and Launcher (Phase 15)

Date: 2026-11-04  
Repository: `qiangge5586-a11y/1`

## Evidence basis

The original APK's DEX string table contains package identifiers:
- `com.autonavi.amapauto`
- `com.baidu.BaiduMap.auto`
- `com.baidu.naviauto`

These are treated as package-name evidence only. Their presence does not prove that a given package is installed on the user's head unit or that vendor-specific service APIs are compatible.

## Implemented

- Added `NavigationAppHelper` using Android's public `PackageManager.getLaunchIntentForPackage` API.
- The main screen checks whether Amap Auto, Baidu Map Auto and Baidu Navi Auto have launchable activities.
- A launch button is enabled only when the app is detected as launchable.
- Launch occurs only after an explicit user tap.

## Safety and compatibility

- Does not force-stop, kill, modify, patch or inject into another application.
- Does not access another app's private files or preferences.
- Does not send undocumented vendor broadcasts or start private services.
- Package presence is not a compatibility guarantee. A real head-unit integration still needs model/firmware identification and a documented interface.
- The helper requests no new Android permission.

## Verification

Source and docs are committed. CI is triggered by Android source changes; the latest run must complete successfully before calling this phase build-verified.
