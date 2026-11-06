# DiPlay Honda Compatibility Android Project

This is an isolated Android application project for the DiPlay 0.3.11 Honda adaptation work. It is deliberately separate from the repository's existing `android/` HondaHackRebuild project.

## Build target
- Android Gradle Plugin 8.7.3 / Gradle 8.9
- Compile SDK 35
- Minimum SDK 17 (Android 4.2.2)
- Target SDK 28
- Java source/target compatibility 7
- ARMv7 device baseline documented in the DiPlay analysis report

The application ID is `com.shilapi.xcertplay.compat`, intentionally distinct from the original DiPlay package so this prototype does not replace the installed original application.

## Current contents
- Minimal Chinese-language landscape launcher screen.
- The pure Java hotspot safety/diagnostic classes under `../compat/hotspot` are included in the app compilation source set.
- No runtime permissions, ROOT process executor, hotspot write operation, service launch, or system configuration mutation is enabled.

## Build
From this directory, with JDK 17, Android SDK platform 35 and Gradle 8.9:

```sh
gradle --no-daemon :app:assembleDebug
```

GitHub Actions workflow: `.github/workflows/diplay-android-build.yml`. A successful build proves that this isolated source project compiles and packages; it does not prove API-17 runtime behavior or vehicle compatibility. No APK should be treated as vehicle-ready until installed and tested on the documented MY16ADA head unit under a separately approved test procedure.


## First CI package (2026-11-06)

The isolated project completed its first successful GitHub Actions Android build in run #4:
https://github.com/qiangge5586-a11y/1/actions/runs/42147380599

Artifact: `diplay-honda-compat-debug` (artifact ID `13470194027`, 21,128 bytes; expires 2026-11-20). The artifact archive digest reported by Actions is `sha256:993b8b4e6643f81186bd8fdff3638290b0f9c7fd3c454c97d7e3d8348034527b`.

This confirms a debug APK was assembled and uploaded by CI. It is an initial Chinese-language shell containing the safety/diagnostic classes, not a feature-complete recreation of DiPlay 0.3.11. It has not been installed on the Honda head unit, and ROOT/hotspot operations remain disabled.
