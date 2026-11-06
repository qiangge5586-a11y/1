# DiPlay API 17 compatibility audit (static review)

Date: 2026-11-06

## Scope

Reviewed the isolated prototype at `DiPlay/android`, including its manifest, app Gradle configuration and current Chinese dashboard Activity. This is a source-level compatibility review only. It is not a runtime test on Android 4.2.2 or the Honda MY16ADA head unit.

## Findings

- `minSdk 17` is declared, matching the report baseline Android 4.2.2 / API 17.
- The app uses Java 7 source and target compatibility.
- The launcher Activity currently uses long-established framework classes and methods: `Activity`, `Bundle`, `ScrollView`, `LinearLayout`, `TextView`, `ViewGroup.LayoutParams`, display density, text sizing, padding, background colors and `setContentView`.
- The Activity does not currently call `su`, start a process, access Wi-Fi APIs, enumerate USB devices, access Bluetooth, read system files, or modify system settings.
- The manifest requests no runtime permissions. Its launcher Activity is explicitly landscape.
- `android:screenSize` in `configChanges` has existed since API 13; `supportsRtl` is available from API 17.
- The build uses compile SDK 35 and target SDK 28 while retaining min SDK 17. Build-time success and AAPT metadata checks prove package metadata, not that every framework behavior is identical on the target firmware.

## Compatibility gates still open

1. Install and launch on an actual API 17 emulator/device. No such test has been completed.
2. Check the actual firmware's package installer behavior and launcher registration.
3. Verify 800x480 rendering, density, font fallback, scrolling and touch targets on the factory screen.
4. Inspect all transitive/native libraries before adding them. The original DiPlay APK contains native libraries whose minimum Android API compatibility is not established by this prototype.
5. Add features behind explicit API-level checks and keep API 17 implementations free of newer framework references at class-load time.
6. Validate USB Host availability and permissions, Bluetooth stack behavior, and Honda-specific services independently before porting those features.

## USB module next-step design

The next implementation should remain a passive, isolated USB inventory module:
- model USB device identity using vendor ID, product ID, device class/subclass/protocol and interface descriptors;
- use Android USB Host APIs only after checking `PackageManager.FEATURE_USB_HOST` and API 12+ availability;
- request user permission through the platform USB permission flow; do not silently claim access;
- do not open endpoints, send control/bulk transfers, claim interfaces, or alter attached device state in the first stage;
- provide fixture-based parser/model tests that run without a physical USB accessory;
- keep USB device discovery separate from CarPlay/NCM protocol handling.

## Conclusion

The current dashboard source appears to use API-17-era framework functionality and compiles as a minSdk-17 APK. API 17 runtime compatibility remains **unverified** until installed and launched on an API 17 environment and then on the target head unit. No ROOT, hotspot, USB, Bluetooth or CarPlay hardware operation was performed during this review.
