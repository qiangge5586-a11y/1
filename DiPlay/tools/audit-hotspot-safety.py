#!/usr/bin/env python3
"""Static safety checks for DiPlay's decompiled ROOT hotspot implementation."""
import argparse
from pathlib import Path
import re
import sys

def read(root, name):
    p = root / "com/shilapi/xcertplay/network" / name
    return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sources", required=True, help="JADX sources directory")
    ap.add_argument("--out", required=True, help="Markdown report path")
    args = ap.parse_args()
    root = Path(args.sources)
    manager = read(root, "HostapdHotspotManager.java")
    config = read(root, "HostapdConfig.java")
    shell = read(root, "RootShell.java")
    findings = []

    def add(level, title, evidence, action):
        findings.append((level, title, evidence, action))

    if re.search(r"kill\s+-9\s+\$\{p#/proc/\}", config):
        add("CRITICAL", "Process cleanup is global by process name",
            "HostapdConfig builds a /proc loop and sends SIGKILL to every process whose comm is hostapd or dnsmasq. It does not prove ownership by this app or compare a recorded PID/start-time token.",
            "Replace with PID ownership records created at launch; verify /proc/<pid>/cmdline, executable and start time before graceful TERM. Never kill unrelated system instances.")
    if re.search(r"kill\s+-9\s+\$2", config):
        add("HIGH", "Legacy ps cleanup also kills by executable name",
            "killStaleScript parses ps output and kills matching hostapd/dnsmasq rows without an app-owned PID registry.",
            "Remove broad name-based cleanup; use only verified app-owned PIDs.")
    if re.search(r"cp\s+/data/local/tmp/hostapd_full\.conf\s+/data/misc/wifi/hostapd\.conf", manager):
        add("CRITICAL", "System hostapd.conf is overwritten without a pre-write backup",
            "HostapdHotspotManager copies the generated configuration directly over /data/misc/wifi/hostapd.conf.",
            "Before any write, snapshot file bytes, existence, mode, owner, group, SELinux label and original Wi-Fi state. Verify backup integrity before replacing.")
    if re.search(r"rm\s+-f\s+/data/misc/wifi/hostapd\.conf", config):
        add("CRITICAL", "Generated-file cleanup deletes the system hostapd.conf path",
            "HostapdConfig.removeGeneratedFilesScript includes /data/misc/wifi/hostapd.conf in rm -f.",
            "Never include the system configuration path in generated-temp cleanup. Restore the verified original file or leave it untouched.")
    if "WifiCtl OFF" in manager and "WifiCtl ON" in manager:
        add("HIGH", "Framework Wi-Fi is toggled as part of hotspot lifecycle",
            "The startup and teardown chains issue WifiCtl OFF/ON; the original state is not shown as a durable snapshot in this manager.",
            "Capture prior Wi-Fi state and only restore the state observed before the transaction.")
    if "writeFile(String path" in shell and '"cat > " + path' in shell:
        add("HIGH", "RootShell.writeFile concatenates a path into a shell script",
            "writeFile constructs a shell command using string concatenation for the path. Safety depends on every caller supplying a fixed trusted path.",
            "Restrict paths to a strict allowlist and use a fixed command protocol; never pass user-controlled paths.")
    if not manager or not config or not shell:
        add("BLOCKER", "One or more expected JADX source files are missing",
            "The scanner could not read all three hotspot source files.",
            "Check JADX output paths and ensure the decompilation step preserved the files.")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# DiPlay ROOT hotspot safety audit",
        "",
        "Scope: JADX Java-like output for HostapdHotspotManager, HostapdConfig and RootShell.",
        "",
        "This is a static pattern audit, not a runtime test. A finding means the corresponding behavior appears in the decompiled source and must be fixed or explicitly justified before use on the Honda head unit.",
        "",
        "| Severity | Finding |",
        "|---|---|",
    ]
    for level, title, _, _ in findings:
        lines.append(f"| **{level}** | {title} |")
    lines += ["", "## Evidence and required remediation", ""]
    for i, (level, title, evidence, action) in enumerate(findings, 1):
        lines += [f"### {i}. [{level}] {title}", "", f"**Evidence:** {evidence}", "", f"**Required action:** {action}", ""]
    if not findings:
        lines += ["No configured risk patterns were detected. This does not prove the implementation is safe; review the source and test on hardware.", ""]
    lines += [
        "## Required transaction design",
        "",
        "1. Read-only preflight: detect interface names, Wi-Fi state, existing processes, config metadata and available tools. Do not assume wlan0, hostapd or dnsmasq.",
        "2. Backup: preserve original config bytes and metadata in an app-owned, root-protected transaction directory; verify backup hash before proceeding.",
        "3. Apply: only after explicit user action and a valid backup; use a temporary config and atomic rename where supported.",
        "4. Process ownership: record PID plus process start-time and command identity; never kill by process name alone.",
        "5. Rollback: on any failure, restore exact original config metadata and the previously observed Wi-Fi state; verify the restored hash.",
        "6. Recovery: persist transaction state so interrupted operations can be detected after app crash or reboot.",
        "",
        "Target device baseline: Honda MY16ADA / Andromeda / vcm30t30, Android 4.2.2 API 17, ARMv7. No live ROOT write or vehicle-side change is performed by this audit.",
        "",
    ]
    out.write_text("\n".join(lines), encoding="utf-8")
    print(out.read_text(encoding="utf-8"))
    return 0

if __name__ == "__main__":
    sys.exit(main())
