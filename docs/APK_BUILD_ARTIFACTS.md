# Debug APK build metadata and verification

The Android CI workflow now verifies the packaged APK itself after assembly:

- application ID must be `cn.autohack.rebuild`
- minimum SDK must be 17
- target SDK must be 28
- APK byte size and SHA-256 are recorded in `build-info.txt`

The APK and build-info file are uploaded together as the `hondahack-rebuild-api17-debug` workflow artifact. A separate `android-lint-report` artifact is also uploaded.

Important: this is a debug-signed APK for testing, not a release-signed distribution package. It has not been installed on the user's factory head unit or verified against the exact vehicle firmware.
