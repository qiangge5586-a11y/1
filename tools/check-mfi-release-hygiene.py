#!/usr/bin/env python3
"""Fail a built APK if bundled offline MFi identity or private-key material is present."""
import sys
import zipfile

FORBIDDEN_PATH_MARKERS = (
    "offline-mfi",
    "offline_mfi",
    "offline.mfi",
    "mfi-private-key",
    "mfi_private_key",
    "mfi-private-identity",
    "mfi_private_identity",
)
PRIVATE_KEY_MARKERS = (
    b"-----BEGIN PRIVATE KEY-----",
    b"-----BEGIN RSA PRIVATE KEY-----",
    b"-----BEGIN EC PRIVATE KEY-----",
    b"-----BEGIN ENCRYPTED PRIVATE KEY-----",
    b"-----BEGIN OPENSSH PRIVATE KEY-----",
)


def scan_apk(path):
    findings = []
    with zipfile.ZipFile(path, "r") as archive:
        for info in archive.infolist():
            name = info.filename.lower()
            if any(marker in name for marker in FORBIDDEN_PATH_MARKERS):
                findings.append("forbidden identity path: " + info.filename)
                continue
            if info.is_dir():
                continue
            data = archive.read(info.filename)
            if any(marker in data for marker in PRIVATE_KEY_MARKERS):
                findings.append("private-key PEM material: " + info.filename)
    return findings


def main(argv):
    if len(argv) != 2:
        print("usage: check-mfi-release-hygiene.py APK", file=sys.stderr)
        return 2
    try:
        findings = scan_apk(argv[1])
    except (OSError, zipfile.BadZipFile) as error:
        print("MFi release hygiene scan failed: " + str(error), file=sys.stderr)
        return 2
    if findings:
        for finding in findings:
            print("FAIL: " + finding, file=sys.stderr)
        return 1
    print("MFi release hygiene: PASS (no offline identity or private-key material)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
