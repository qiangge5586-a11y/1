# Reproducible decompilation of the original 7.6.0 APK

## Purpose

The original reverse-engineering report references JADX 1.5.3, but the complete `jadx-out/sources` tree is not currently in the Library or this repository. This helper recreates that output from the verified original APK without modifying the APK.

## Why WSL2 ext4 is required

The APK contains case-colliding Java class names (for example, `d.java` and `D.java`). A case-insensitive NTFS/DrvFS output directory can collapse these names and silently omit one class. The script therefore requires an ext4 workspace by default. Run it from the WSL2 Linux home directory, not from `/mnt/c`.

## Windows 11 setup

In an elevated PowerShell window, install Ubuntu under WSL2 if it is not already installed:

```powershell
wsl --install -d Ubuntu
```

Open Ubuntu and install the prerequisites:

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless curl unzip
```

Copy the original APK from the Windows filesystem into the Linux home directory. Replace the Windows username/path with the actual location:

```bash
mkdir -p ~/hondahack-input
cp /mnt/c/Users/<WindowsUser>/Downloads/本田黑科技7.6.0_可联网_去注销_任意.apk ~/hondahack-input/
```

Clone the project into the Linux filesystem and run the helper:

```bash
git clone https://github.com/qiangge5586-a11y/1.git ~/HondaHackRebuild
cd ~/HondaHackRebuild
bash tools/decompile-original.sh \
  ~/hondahack-input/本田黑科技7.6.0_可联网_去注销_任意.apk \
  ~/hondahack-analysis
```

## Integrity and reproducibility

The script verifies:

- Original APK SHA-256: `e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe`.
- Official JADX 1.5.3 release archive SHA-256: `8280f3799c0273fe797a2bcd90258c943e451fd195f13d05400de5e6451d15ec`.
- The JADX CLI version is pinned to 1.5.3 to match the existing reverse-engineering report.
- The script does not overwrite an existing output directory and records hashes/version/source count in `analysis-metadata.txt`.

Official release: https://github.com/skylot/jadx/releases/tag/v1.5.3

## Output and next review

The output is written to `~/hondahack-analysis/jadx-out/sources`. The script does not install or execute the APK. JADX warnings must be reviewed; failed or ambiguous methods should be checked against DEX/smali.

Do not commit the complete decompiled source tree to the app repository by default. Preserve the original APK and generated analysis locally, then commit only reviewed behavior notes, class/method maps, and safe independently implemented changes.

## Current status

The script and its CI syntax check are committed. The full local decompilation has not yet been executed in this environment because the JADX release archive could not be downloaded here. Method-level review remains pending until the script is run in WSL2 and the output is available.
