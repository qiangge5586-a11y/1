# Bluetooth passive status probe

- Compatible API baseline: Android 2.0 / API 5+ BluetoothAdapter status APIs; intended target is API 17.
- Reads adapter presence, state, enabled flag, and adapter name when enabled.
- Does not enable/disable Bluetooth, initiate discovery, pair, connect, read contacts, or inspect messages.
- Bluetooth adapter name may be unavailable on vendor firmware or due to permission policy; status remains useful without it.
- No Bluetooth LE scan APIs are used.
- Real MY16ADA behavior remains unverified until run on the head unit.
