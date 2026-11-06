#!/usr/bin/env python3
"""Fail a built APK if bundled offline MFi identity or private-key PEM payload is present."""
import re
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

PRIVATE_KEY_PEM_BLOCKS = (
    (b"-----BEGIN PRIVATE KEY-----", b"-----END PRIVATE KEY-----"),
    (b"-----BEGIN RSA PRIVATE KEY-----", b"-----END RSA PRIVATE KEY-----"),
    (b"-----BEGIN EC PRIVATE KEY-----", b"-----END EC PRIVATE KEY-----"),
    (b"-----BEGIN ENCRYPTED PRIVATE KEY-----", b"-----END ENCRYPTED PRIVATE KEY-----"),
    (b"-----BEGIN OPENSSH PRIVATE KEY-----", b"-----END OPENSSH PRIVATE KEY-----"),
)


def contains_private_key_pem_payload(data):
    # Some production TLS parsers legitimately contain the PEM boundary strings
    # as byte-array constants. Reject an actual Base64 payload between matching
    # delimiters, not a parser's delimiter-only constants.
    for begin, end in PRIVATE_KEY_PEM_BLOCKS:
        pattern = re.escape(begin) + rb"\\s*([A-Za-z0-9+/=\\r\\n]{64,})\\s*" + re.escape(end)
        if re.search(pattern, data):
            return True
    return False


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
            if contains_private_key_pem_payload(data):
                findings.append("private-key PEM payload: " + info.filename)
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
    print("MFi release hygiene: PASS (no offline identity or private-key PEM payload)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
