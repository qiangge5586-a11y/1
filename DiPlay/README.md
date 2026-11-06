# DiPlay 0.3.11 — Honda adaptation analysis

## Original APK

Expected path: `DiPlay/original/diplay0.3.11.apk`

Recorded SHA-256:
`22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`

The GitHub Actions workflow checks this hash before decompilation and stops if it differs.

## Run

1. Add the original APK at the path above.
2. Push the commit, or run **Actions → DiPlay APK Decompile → Run workflow**.
3. Download the `diplay-0.3.11-decompiled` artifact.

JADX produces Java-like source. APKTool decodes resources, AndroidManifest.xml and Smali. Outputs are attached as a workflow artifact rather than committed into Git history.

## Honda compatibility target

Android 4.2.2 / API 17 / ARMv7 (`armeabi-v7a`). Follow-up analysis should identify API 18+ calls, native ABI requirements, BYD-specific bridges, and safe ROOT hotspot configuration behavior.

## Current gate

The original APK bytes must be added before the workflow can execute. A filename and recorded hash cannot reconstruct the binary.
