#!/usr/bin/env bash
set -euo pipefail
APK="${1:?Usage: analyze-native-libs.sh <apk> [output-dir]}"
OUT="${2:-DiPlay/native-audit}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"
unzip -q "$APK" 'lib/*/*.so' -d "$TMP" || true
{
  echo "# DiPlay 0.3.11 native library audit"
  echo
  echo "APK: $APK"
  echo "Generated: $(date -u +%FT%TZ)"
  echo
  echo "| Library | ABI | ELF class | Machine | Android API note | NEEDED |"
  echo "|---|---|---|---|---|---|"
  while IFS= read -r -d '' f; do
    rel="${f#"$TMP"/}"
    abi="$(basename "$(dirname "$f")")"
    header="$(readelf -h "$f" 2>/dev/null || true)"
    cls="$(printf '%s\n' "$header" | awk -F: '/Class:/{gsub(/^[ \t]+/,"",$2);print $2}')"
    machine="$(printf '%s\n' "$header" | awk -F: '/Machine:/{gsub(/^[ \t]+/,"",$2);print $2}')"
    notes="$(readelf -n "$f" 2>/dev/null || true)"
    # First 4 bytes of .note.android.ident description are little-endian API level.
    # Example: 13 00 00 00 means API 19; readelf does not print this as decimal text.
    note_bytes="$(printf '%s\n' "$notes" | sed -nE 's/.*description data: ([0-9a-fA-F]{2}) ([0-9a-fA-F]{2}) ([0-9a-fA-F]{2}) ([0-9a-fA-F]{2}).*/\1 \2 \3 \4/p' | head -n1)"
    if [ -n "$note_bytes" ]; then
      read -r b0 b1 b2 b3 <<< "$note_bytes"
      api=$((16#$b0 + (16#$b1 << 8) + (16#$b2 << 16) + (16#$b3 << 24)))
    else
      api="not-reported"
    fi
    needed="$(readelf -d "$f" 2>/dev/null | sed -nE 's/.*Shared library: \[([^]]+)\].*/\1/p' | paste -sd ',' -)"
    [ -n "$needed" ] || needed="none"
    printf '| %s | %s | %s | %s | %s | %s |\n' "$rel" "$abi" "${cls:-unknown}" "${machine:-unknown}" "$api" "$needed"
  done < <(find "$TMP" -type f -name '*.so' -print0 | sort -z)
  echo
  echo "## Interpretation"
  echo
  echo "- Android API note is an NDK build-platform marker, not a complete proof of runtime compatibility."
  echo "- A missing note means the minimum API cannot be inferred from this field alone."
  echo "- ABI compatibility (for example armeabi-v7a) does not guarantee Android API compatibility."
  echo "- Final confirmation requires loading each required library on Android 4.2.2/API 17 and checking linker errors."
} > "$OUT/REPORT.md"
find "$TMP" -type f -name '*.so' -printf '%P\n' | sort > "$OUT/LIBRARIES.txt"
cat "$OUT/REPORT.md"
