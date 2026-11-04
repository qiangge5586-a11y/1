# Paired Bluetooth ELM327 adapter (read-only)

## User workflow

1. Pair an ELM327-compatible Bluetooth Classic SPP adapter in Android Settings.
2. Open the vehicle data section in HondaHack Rebuild.
3. Tap “Choose paired OBD-II adapter”, select the paired device (only the last four address characters are shown), review the confirmation dialog and tap Connect.
4. The app reads speed, RPM, coolant temperature and fuel level. Missing/unsupported PIDs display `--`.
5. Tap Disconnect, or leave the Activity; the Bluetooth socket is closed when the Activity pauses.

The app does not scan for devices, pair automatically, reconnect in the background or persist the adapter address.

## Commands and safety boundary

The controller sends only these ELM adapter setup commands: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`.

The fixed Mode 01 poll allowlist is:

- `010D` — vehicle speed
- `010C` — engine RPM
- `0105` — coolant temperature
- `012F` — fuel level

No Mode 04 clear-DTC, Mode 08 control, Mode 09 write, actuator test, coding, immobilizer, ECU reprogramming or Honda enhanced PID is sent. The app does not write to vehicle ECUs.

## Permissions and privacy

- Android 12+: `BLUETOOTH_CONNECT` is requested only after the user taps the paired-adapter picker.
- Android 11 and earlier: legacy Bluetooth permissions are declared for bonded-device access.
- No Bluetooth scan/discovery permission is requested.
- The controller runs on a worker thread only while connected from the foreground Activity; it disconnects in `onPause`.
- Live values remain in memory and are not stored, uploaded or shared.
- The existing offline demo snapshot remains separate and is not overwritten by OBD data.

## Limitations

- Physical adapter compatibility, ELM firmware quality, Civic ECU PID support and real-world response timing require bench/vehicle validation.
- Some low-cost ELM327 clones do not implement the full SPP profile or standard command set.
- Generic PID availability varies by ECU. Missing responses are displayed as unavailable, not replaced by sample values.
- This does not prove compatibility with the Civic factory infotainment display.
