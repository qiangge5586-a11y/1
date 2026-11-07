# DiPlay LOCAL MFi provider-path restoration

Date: 2026-11-07
Target: DiPlay 0.3.11 Honda merged test build.

## Implemented in this change

- Restored the original first-run MfiTarget.LOCAL selection in AirPlayPersistence when no valid persisted target exists.
- Restored the original CarPlayHostActivity initial LOCAL target.
- Restored the original LOCAL/offline option in the MFi settings list; I2C and USB/CH341 remain separate selectable backends.
- Kept the original APK's LOCAL provider, iAP2 MFi exchange, authenticator dispatch, and AirPlay SAP code in the decoded original DEX/Smali path. The merge does not replace these protocol implementations with a simulated success path.
- Kept assets/offline-mfi excluded from the merged APK and source bundle. No private identity, certificate, key, or signature material is copied.
- No automatic fallback from I2C/USB/REMOTE to LOCAL is added.

## Runtime meaning and limitation

This restores the original LOCAL provider selection and code dispatch path, but does not make LOCAL authentication operational by itself: the bootstrap identity assets are intentionally absent. A separately provisioned, authorized MFi test identity/provider is required before the LOCAL branch can complete a real certificate/challenge/signature exchange. The app must fail closed if that provider material is absent or invalid.

This is deliberate. The fact that the original APK contains offline identity assets, or that the unmodified app connected on the user's devices, does not establish that those private credentials may be redistributed or used as production MFi credentials.

## Verification required

- CI must rebuild the merged APK and run the archive hygiene scan.
- Inspect the merged Smali to confirm the three LOCAL selection anchors and original provider classes remain present.
- On OnePlus, confirm first-run no longer selects I2C /dev/i2c-1 by default; verify the diagnostic identifies LOCAL/test mode and reports missing authorized test identity rather than claiming authentication success.
- On Civic Android 4.2.2/API 17, validate the authorized provider and complete iAP2 MFi exchange with a known-compatible iPhone.
- Verify I2C and USB/CH341 explicit selections still dispatch to their own provider and never silently fall back.

## Status

Source patch committed; CI and device validation are pending. This change restores backend selection and preserves the original protocol implementation. It is not a claim of completed live MFi authentication.