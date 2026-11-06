#!/system/bin/sh
# DiPlay / Honda head-unit read-only capability probe.
# Android 4.2.2 / toolbox-compatible POSIX shell.
# This script MUST remain read-only: it does not change Wi-Fi, files, services or interfaces.

section() { echo; echo "===== $1 ====="; }

section "IDENTITY"
date 2>/dev/null
id 2>&1
uname -a 2>&1
getprop ro.build.version.release 2>/dev/null | sed 's/^/android_release=/'
getprop ro.build.version.sdk 2>/dev/null | sed 's/^/sdk_int=/'
getprop ro.build.fingerprint 2>/dev/null | sed 's/^/fingerprint=/'
getprop ro.product.cpu.abi 2>/dev/null | sed 's/^/abi_primary=/'
getprop ro.product.cpu.abi2 2>/dev/null | sed 's/^/abi_secondary=/'
getprop ro.product.device 2>/dev/null | sed 's/^/device=/'
getprop ro.product.model 2>/dev/null | sed 's/^/model=/'

section "SECURITY"
if command -v getenforce >/dev/null 2>&1; then getenforce 2>&1; else echo "getenforce=unavailable"; fi
if command -v su >/dev/null 2>&1; then echo "su_binary=present"; else echo "su_binary=missing"; fi

section "WIFI SERVICES"
service list 2>/dev/null | grep -i wifi
dumpsys wifi 2>/dev/null | head -n 100
dumpsys connectivity 2>/dev/null | head -n 80

section "NETWORK INTERFACES"
ifconfig -a 2>&1
cat /proc/net/dev 2>/dev/null
cat /proc/net/wireless 2>/dev/null

section "WIFI SYSFS"
for p in /sys/class/net/wlan0 /sys/class/net/wlan1 /sys/class/net/p2p0; do
  if [ -e "$p" ]; then
    echo "interface=$p"
    ls -ld "$p" 2>&1
    [ -r "$p/address" ] && { printf "mac="; cat "$p/address"; }
    [ -r "$p/operstate" ] && { printf "operstate="; cat "$p/operstate"; }
    [ -r "$p/uevent" ] && sed -n '1,20p' "$p/uevent"
  fi
done
for p in /sys/class/net/wlan0/device/driver /sys/class/net/wlan0/phy80211 /sys/class/ieee80211; do
  if [ -e "$p" ]; then ls -ld "$p" 2>&1; fi
done

section "HOTSPOT BINARIES"
for b in hostapd dnsmasq iw ifconfig ip iwconfig wpa_supplicant app_process; do
  found=0
  for d in /system/bin /system/xbin /vendor/bin /bin; do
    if [ -x "$d/$b" ]; then echo "$b=$d/$b"; found=1; break; fi
  done
  [ "$found" = "0" ] && echo "$b=not-found-in-standard-paths"
done

section "WIFI CONFIG METADATA (NO CONTENT READ)"
for p in /data/misc/wifi /data/misc/wifi/hostapd.conf /data/misc/wifi/wpa_supplicant.conf /data/local/tmp /data/local/tmp/wifi.dex /data/local/tmp/bt.dex; do
  if [ -e "$p" ]; then ls -ld "$p" 2>&1; else echo "missing=$p"; fi
done

section "KERNEL / USB"
cat /proc/version 2>/dev/null
ls -l /sys/bus/usb/devices 2>/dev/null | head -n 40
ls -l /dev/bus/usb 2>/dev/null | head -n 40

section "DONE"
echo "READ_ONLY_PROBE_COMPLETE"
