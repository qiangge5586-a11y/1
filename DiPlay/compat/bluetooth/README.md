# Bluetooth API 17 compatibility layer

## Passive adapter probe

- Reads adapter presence, state, enabled flag, and adapter name when enabled.
- Does not enable/disable Bluetooth, initiate discovery, pair, connect, read contacts, or inspect messages.
- Bluetooth adapter name may be unavailable on vendor firmware or due to permission policy; status remains useful without it.
- No Bluetooth LE scan APIs are used.

## RFCOMM transport

`BluetoothRfcommConnector` is an isolated, blocking connector intended to run on a worker thread. It follows the original DiPlay fallback order:

1. Secure RFCOMM service-record socket.
2. Insecure RFCOMM service-record socket after the 100 ms fallback delay.
3. Reflective hidden `createInsecureRfcommSocket(1)` fallback.

The service UUID defaults to `00000000-deca-fade-deca-deafdecacafe`. A single monotonic deadline is now shared by all three attempts, rather than granting each fallback a fresh timeout. The accepted overall timeout range is 1..300,000 ms; the default is 10,000 ms. `cancel()` closes the currently active socket. Pairing, device selection, consent and adapter state changes remain outside this class.

The hidden API fallback is ROM-dependent. A successful connector return only means the RFCOMM socket connected; it does not mean iAP2 identification, MFi authentication, Wi-Fi handoff, AirPlay IAP tunnel setup or CarPlay session startup succeeded.

## RFCOMM byte stream

`BluetoothRfcommByteStream` adapts an already-connected socket to `Iap2DuplexByteStream`. One daemon reader owns the socket InputStream and places bytes into a bounded 1 MiB ring; reads support monotonic caller timeouts, writes are serialized, and close closes the socket to release a blocked reader. API 17/vendor-stack behavior for socket-close unblocking and sustained throughput remains unverified.

## Validation boundary

- Pure Java ring-buffer and connection-deadline tests run in the iAP2 CI workflow.
- Android CI compiles/packages this module into the isolated compatibility APK.
- Neither CI job tests the Android Bluetooth stack, hidden API access, socket cancellation, or RFCOMM behavior on MY16ADA.
- Real MY16ADA and iPhone testing remains required before production use.
