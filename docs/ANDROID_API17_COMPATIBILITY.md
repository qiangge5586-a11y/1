# Android API 17 compatibility target

## Why API 17

The reverse-engineering report for the supplied HondaHack 7.6.0 APK records `minSdk=17`, `targetSdk=17`, `compileSdk=28`. The rebuild previously required API 23, which would exclude Android 4.2.2 (API 17) head units.

The rebuild now declares `minSdk=17` while retaining a modern compile SDK. CI also runs `lintDebug` to identify API-level calls that need runtime guards or alternatives.

## Vehicle evidence and limits

- The public `librick/ic1101` reverse-engineering project describes most 2016–2021 10th-generation Civic head units as Android 4.2.2 / API 17, but explicitly notes that some lower-end trims use different hardware.
- Contemporary coverage of the 2019 China-market Civic identifies the CVT 劲动版 as a lower trim with a 7-inch display and CarPlay; it does not establish the exact Android version or prove that this specific factory head unit accepts arbitrary APKs.
- Therefore API 17 is a compatibility target derived from the original APK and related 10th-gen head-unit research, not proof that the user's exact 2019 劲动版 screen is Android or supports sideloading.

## Compatibility rules

- Newer runtime permission APIs are called only on Android versions that provide them; pre-23 devices use legacy permission behavior.
- Unsupported features must fail closed or remain disabled rather than crashing the Activity.
- The current debug APK must pass unit tests, `lintDebug` and assembly before API 17 compatibility can be marked CI-verified.
- A successful build/lint run is not a substitute for installing and testing on a physical API 17/19 head unit.

## Sources

- Original sample reverse-engineering report in the Library: APK manifest minSdk 17.
- https://github.com/librick/ic1101 — 10th-gen Civic head-unit research and explicit trim caveat.
- 2019 China-market Civic trim coverage: https://www.sohu.com/a/346038777_215942 — 2019 CVT 劲动/燃动 lower trims and CarPlay context.
