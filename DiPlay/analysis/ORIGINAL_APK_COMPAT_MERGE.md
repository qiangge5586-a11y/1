# DiPlay 0.3.11 + Honda compatibility merge

Date: 2026-11-06

## Inputs

- Original APK: `diplay0.3.11.apk`
- Verified original SHA-256: `22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`
- Recovered original version: 0.3.11, versionCode 131
- Original Android package: `com.shihab.diplay`
- Original decompile evidence: JADX Java reconstructions plus APKTool Smali/resources

## Merge layout

The build keeps the original DiPlay app implementation and its four original DEX files. It decodes the original APK with Apktool 3.0.3, rebuilds the resources and Smali, then adds the compiled compatibility overlay as `classes5.dex`.

The overlay compiles production Java sources from:

- `compat/bluetooth`
- `compat/hotspot`
- `compat/usb`
- `compat/ncm`
- `compat/iap2`
- `compat/mfi`
- `compat/cluster`

Test sources remain in the source bundle and Java 7 CI suites; they are not shipped in the APK. The original app's internal code namespace remains `com.shilapi.xcertplay`. The Android package is changed to `com.shihab.diplay.honda` so the test fork can be installed alongside the original app. Package-scoped permissions and provider authorities are changed with it.

The original Application extends AndroidX `MultiDexApplication`, so the additional `classes5.dex` is loaded through the existing multidex bootstrap.

## MFi identity and backend safety

The original APK contains a bundled offline MFi identity. The merged test build intentionally excludes the `assets/offline-mfi` identity directory. Its default MFi target is changed to the original app's I2C hardware backend, with USB/CH341 retained as the alternate hardware choice. The original offline/local option is not exposed in the merged settings UI.

This is a fail-closed hardware configuration choice, not proof that the MY16ADA exposes the expected I2C device or that the MFi coprocessor is reachable. The configured path remains the original app's `/dev/i2c-1` default and must be verified on the head unit.

## What this merge proves

- Original APK SHA-256 matched before decoding.
- Original DiPlay resources and Smali rebuilt with Apktool.
- Optimized compatibility Java modules compiled with Java 7 source/target and D8 min API 17.
- Overlay DEX added as `classes5.dex`.
- APK signed with v1 + v2 schemes; API 17 can use the v1 signature.
- AAPT package/minSdk checks and MFi identity hygiene scan passed in CI.

## Important runtime boundary

This is a source/package integration build, not proof that every compatibility model is now the live production transport. The original DiPlay production `Iap2LinkEngine`, `Iap2LinkChannel`, `Iap2CsmChannel`, RFCOMM and AirPlay tunnel implementation remain intact in the original app DEX. The new compatibility iAP2/MFi Java models and lifecycle scaffolds are packaged alongside them, but the offline model pump is not substituted for the original production engine.

No live Honda head-unit, iPhone, Bluetooth, AirPlay type-130 tunnel, MFi coprocessor, or API-17 runtime test has been performed. ROOT hotspot write operations remain disabled.
