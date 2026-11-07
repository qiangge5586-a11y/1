#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/DiPlay/android"
ORIGINAL_APK="$ROOT/diplay0.3.11.apk"
APKTOOL_BIN="${APKTOOL_BIN:-apktool}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BUILD_ROOT="$ANDROID_DIR/build/merged-original"
DECODED="$BUILD_ROOT/decoded"
DEX_DIR="$BUILD_ROOT/overlay-dex"
AAR="$ANDROID_DIR/compat-overlay/build/outputs/aar/compat-overlay-release.aar"
UNSIGNED="$BUILD_ROOT/diplay-0.3.11-honda-unsigned.apk"
ALIGNED="$BUILD_ROOT/diplay-0.3.11-honda-aligned.apk"
OUT="$ANDROID_DIR/build/outputs/apk/merged/diplay-0.3.11-honda-compat.apk"
SOURCE_ZIP="$ANDROID_DIR/build/outputs/apk/merged/diplay-0.3.11-honda-source.zip"

if [[ ! -f "$ORIGINAL_APK" ]]; then
  echo "Original DiPlay 0.3.11 APK missing: $ORIGINAL_APK" >&2
  exit 2
fi
if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME/build-tools/35.0.0" ]]; then
  echo "ANDROID_HOME with build-tools 35.0.0 is required" >&2
  exit 2
fi

EXPECTED_SHA="22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1"
ACTUAL_SHA="$(sha256sum "$ORIGINAL_APK" | awk '{print $1}')"
if [[ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
  echo "Original APK SHA-256 mismatch: $ACTUAL_SHA" >&2
  exit 3
fi

if [[ ! -f "$AAR" ]]; then
  (cd "$ANDROID_DIR" && gradle --no-daemon :compat-overlay:assembleRelease)
fi

rm -rf "$BUILD_ROOT"
mkdir -p "$DECODED" "$DEX_DIR" "$(dirname "$OUT")"
"$APKTOOL_BIN" d -f "$ORIGINAL_APK" -o "$DECODED"

# Keep the original app's internal Java/Kotlin package namespace, but give this
# test build a distinct Android package so it can coexist with the installed APK.
find "$DECODED" -type f \( -name '*.smali' -o -name '*.xml' \) -print0 \
  | xargs -0 -r sed -i 's/com\.shihab\.diplay/com.shihab.diplay.honda/g'
sed -i 's/^  versionCode: .*/  versionCode: 132/; s/^  versionName: .*/  versionName: 0.3.11-honda1/' \
  "$DECODED/apktool.yml"

# Remove stale JAR-signature metadata. The patch restores the original LOCAL
# provider selection path while preserving original bundled identity assets;
# I2C and USB/CH341 remain explicit selectable hardware providers.
rm -rf "$DECODED/unknown/META-INF" "$DECODED/original/META-INF"
python3 "$ROOT/tools/patch-diplay-original.py" "$DECODED"

# Compile only the optimized production compatibility classes. The original
# DiPlay DEX files remain untouched; the overlay is appended as classes5.dex.
unzip -p "$AAR" classes.jar > "$BUILD_ROOT/compat-classes.jar"
D8="$ANDROID_HOME/build-tools/35.0.0/d8"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
"$D8" --min-api 17 --lib "$ANDROID_JAR" --output "$DEX_DIR" \
  "$BUILD_ROOT/compat-classes.jar"

if [[ ! -s "$DEX_DIR/classes.dex" ]]; then
  echo "D8 did not produce classes.dex" >&2
  exit 4
fi

"$APKTOOL_BIN" b "$DECODED" -o "$UNSIGNED"
cp "$DEX_DIR/classes.dex" "$BUILD_ROOT/classes5.dex"
(cd "$BUILD_ROOT" && zip -0 -q "$UNSIGNED" classes5.dex)

BT="$ANDROID_HOME/build-tools/35.0.0"
"$BT/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"

KEYSTORE="${RUNNER_TEMP:-$BUILD_ROOT}/diplay-test.keystore"
mkdir -p "$(dirname "$KEYSTORE")"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias diplaytest -dname "CN=DiPlay Honda Test,O=Local" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false \
  --out "$OUT" "$ALIGNED"
"$BT/apksigner" verify --verbose "$OUT"
python3 "$ROOT/tools/check-mfi-release-hygiene.py" "$OUT"

"$BT/aapt" dump badging "$OUT" > "$BUILD_ROOT/badging.txt"
grep -F "name='com.shihab.diplay.honda'" "$BUILD_ROOT/badging.txt"
grep -F "sdkVersion:'17'" "$BUILD_ROOT/badging.txt"
unzip -l "$OUT" | grep -F 'classes5.dex'
sha256sum "$OUT"

# Export the complete recoverable source set: original APKTool resources/Smali,
# every optimized compatibility Java module (including tests), build scripts,
# and the source-audit documents. The offline MFi identity was already removed.
SOURCE_ROOT="$BUILD_ROOT/source-package"
rm -rf "$SOURCE_ROOT"
mkdir -p "$SOURCE_ROOT"
cp -a "$DECODED" "$SOURCE_ROOT/original-apktool"
cp -a "$ROOT/DiPlay/compat" "$SOURCE_ROOT/compat"
cp -a "$ROOT/DiPlay/analysis" "$SOURCE_ROOT/analysis"
mkdir -p "$SOURCE_ROOT/android/compat-overlay"
cp "$ROOT/DiPlay/android/compat-overlay/build.gradle" "$SOURCE_ROOT/android/compat-overlay/"
cp "$ROOT/DiPlay/android/settings.gradle" "$ROOT/DiPlay/android/build.gradle" \
   "$ROOT/DiPlay/android/gradle.properties" "$SOURCE_ROOT/android/"
mkdir -p "$SOURCE_ROOT/tools"
cp "$ROOT/tools/build-diplay-merged.sh" "$ROOT/tools/patch-diplay-original.py" \
   "$ROOT/tools/check-mfi-release-hygiene.py" "$ROOT/tools/test_check_mfi_release_hygiene.py" \
   "$SOURCE_ROOT/tools/"
cat > "$SOURCE_ROOT/BUILD_INFO.md" <<EOF
# DiPlay 0.3.11 Honda merged source bundle

- Original APK SHA-256: $EXPECTED_SHA
- Recovered original version: 0.3.11 (APK versionCode 131)
- Test-fork Android package: com.shihab.diplay.honda
- Minimum Android API: 17
- Optimized compatibility modules: bluetooth, hotspot, USB, NCM, iAP2, MFi and cluster
- Original production DiPlay Smali/resources: original-apktool/
- Offline MFi identity assets: original APK assets preserved unchanged
- MFi first-run selection: original LOCAL provider path and original bundled asset bootstrap
- MFi hardware providers: explicit I2C and USB/CH341 choices; no silent fallback
- MFi identity bytes are not logged, exported, or rewritten by the merge script
- Signature: test key, v1 + v2 (API 17 uses v1)
- Vehicle/iPhone hardware validation: not yet performed
EOF
(cd "$BUILD_ROOT" && zip -qr "$SOURCE_ZIP" source-package)
sha256sum "$SOURCE_ZIP"
echo "Merged DiPlay APK: $OUT"
echo "Merged DiPlay source bundle: $SOURCE_ZIP"
