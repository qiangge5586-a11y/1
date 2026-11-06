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
