#!/usr/bin/env bash
set -euo pipefail

JADX_VERSION="1.5.3"
JADX_ZIP_SHA256="8280f3799c0273fe797a2bcd90258c943e451fd195f13d05400de5e6451d15ec"
APK_SHA256="e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" || $# -lt 1 ]]; then
  echo "Usage: $0 /path/to/本田黑科技7.6.0_可联网_去注销_任意.apk [workspace]"
  echo "Runs the pinned JADX $JADX_VERSION CLI and writes sources to workspace/jadx-out."
  exit 0
fi

APK_PATH="$(realpath "$1")"
WORKSPACE="${2:-"$HOME/hondahack-re-analysis"}"
if [[ ! -f "$APK_PATH" ]]; then
  echo "ERROR: APK not found: $APK_PATH" >&2
  exit 2
fi

ACTUAL_APK_SHA="$(sha256sum "$APK_PATH" | awk '{print $1}')"
if [[ "$ACTUAL_APK_SHA" != "$APK_SHA256" ]]; then
  echo "ERROR: APK SHA-256 mismatch." >&2
  echo "Expected: $APK_SHA256" >&2
  echo "Actual:   $ACTUAL_APK_SHA" >&2
  exit 3
fi

for command_name in java curl unzip sha256sum realpath; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "ERROR: required command missing: $command_name" >&2
    exit 4
  }
done

JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
echo "Java runtime: $JAVA_VERSION"

mkdir -p "$WORKSPACE/cache" "$WORKSPACE/tools" "$WORKSPACE/config" "$WORKSPACE/tmp"
FS_TYPE="$(df -T --output=fstype "$WORKSPACE" | tail -n 1 | tr -d ' ')"
if [[ "$FS_TYPE" != "ext4" && "${ALLOW_CASE_INSENSITIVE_FS:-0}" != "1" ]]; then
  echo "ERROR: workspace filesystem is '$FS_TYPE', not ext4." >&2
  echo "Use a WSL2 Linux home directory (for example /home/<user>/hondahack-re-analysis)." >&2
  echo "This APK has case-colliding class names; NTFS/DrvFS can silently lose one during decompilation." >&2
  echo "Only override with ALLOW_CASE_INSENSITIVE_FS=1 if you accept incomplete output." >&2
  exit 5
fi

JADX_ARCHIVE="$WORKSPACE/cache/jadx-$JADX_VERSION.zip"
JADX_HOME="$WORKSPACE/tools/jadx-$JADX_VERSION"
JADX_OUTPUT="$WORKSPACE/jadx-out"
JADX_JAR="$JADX_HOME/lib/jadx-$JADX_VERSION-all.jar"

if [[ -e "$JADX_OUTPUT" ]]; then
  echo "ERROR: output already exists: $JADX_OUTPUT" >&2
  echo "Move it aside before rerunning; this script will not overwrite prior analysis." >&2
  exit 6
fi

if [[ ! -f "$JADX_ARCHIVE" ]]; then
  curl -fL --retry 3 \
    "https://github.com/skylot/jadx/releases/download/v$JADX_VERSION/jadx-$JADX_VERSION.zip" \
    -o "$JADX_ARCHIVE"
fi

echo "$JADX_ZIP_SHA256  $JADX_ARCHIVE" | sha256sum -c -
mkdir -p "$JADX_HOME"
unzip -q -o "$JADX_ARCHIVE" -d "$JADX_HOME"
if [[ ! -f "$JADX_JAR" ]]; then
  echo "ERROR: expected JADX CLI jar not found: $JADX_JAR" >&2
  exit 7
fi

export APPDATA="$WORKSPACE/config"
export LOCALAPPDATA="$WORKSPACE/config"
export JADX_CONFIG_DIR="$WORKSPACE/config"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=\"$WORKSPACE/tmp\""

echo "Input APK SHA-256: $ACTUAL_APK_SHA"
echo "JADX version: $JADX_VERSION"
echo "Output directory: $JADX_OUTPUT"

java -Xmx2g -cp "$JADX_JAR" jadx.cli.JadxCLI \
  -d "$JADX_OUTPUT" --no-res --no-debug-info -j 4 "$APK_PATH"

SOURCE_DIR="$JADX_OUTPUT/sources"
if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "ERROR: JADX did not produce a sources directory." >&2
  exit 8
fi

JAVA_COUNT="$(find "$SOURCE_DIR" -type f -name '*.java' | wc -l | tr -d ' ')"
cat > "$WORKSPACE/analysis-metadata.txt" <<EOF
APK_SHA256=$ACTUAL_APK_SHA
JADX_VERSION=$JADX_VERSION
JADX_ARCHIVE_SHA256=$JADX_ZIP_SHA256
JAVA_SOURCE_COUNT=$JAVA_COUNT
SOURCE_DIRECTORY=$SOURCE_DIR
EOF

echo "Decompilation finished. Java source files: $JAVA_COUNT"
echo "Metadata: $WORKSPACE/analysis-metadata.txt"
echo "Review JADX warnings and DEX/smali for classes that fail Java decompilation."
