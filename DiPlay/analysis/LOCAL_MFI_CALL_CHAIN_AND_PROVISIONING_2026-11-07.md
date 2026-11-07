# DiPlay LOCAL MFi source call-chain audit

Date: 2026-11-07
Build under review: workflow run 42317553897

## Verified original call chain

1. CarPlayHostActivity invokes DiPlayBootstrap.ensure(context, mfiTarget) during startup (two call sites are present in the recovered Smali).
2. DiPlayBootstrap.ensure returns immediately for non-LOCAL targets. For LOCAL it uses the app-private no-backup directory on API 21+ and files directory on older Android versions, then manages the offline-mfi directory.
3. The original bootstrap asset manifest has exactly two expected identity files: identity.pk8 and certificate.p7b. The bootstrap stages these from assets/offline-mfi/ and validates them against its expected asset set.
4. CarPlayController.openLocalMfi(directory) runs asynchronously on its executor and calls LocalMfiAuthenticationClient.Companion.load(directory, signatureCallback).
5. The LocalMfiAuthenticationClient implements MfiAuthenticator. protocolMajor() returns 3; readCertificate(maximumOutputLength) returns a bounded defensive copy; signChallenge(challenge) signs and reports only signature-size telemetry to the controller.
6. The LocalMfiAuthenticationClient loader reads identity.pk8 as PKCS#8 EC private-key material and certificate.p7b as an X.509 certificate bundle. It checks for an EC P-256 accessory public key, checks private/public key correspondence using a challenge-signature verification, and rejects malformed or mismatched material.
7. Once the local authenticator has loaded and the controller remains in the MFI phase, CarPlayController stores it in MfiSession, reports MfiReady, and advances to startPhone(). Actual iAP2 certificate/challenge/signature exchange is handled by Iap2MfiAuthenticationClient using the MfiAuthenticator interface.

## What the new build contains

- The original MFi interfaces and provider implementation remain in the decoded production Smali tree.
- The restored LOCAL first-run selection is present in the build patch.
- The merged build intentionally removes assets/offline-mfi, so the original bootstrap cannot stage bundled identity files.
- Therefore restoring LOCAL selection does not, by itself, make the provider load successfully. It will fail closed when no separately provisioned identity pair is available.
- The source bundle and APK build passed CI; this is not a live MFi authentication result.

## Safe next integration

Implement a user-controlled identity provisioning path for a separately supplied, authorized MFi development/test identity:

- Import the two expected files through Android's document picker or a trusted provisioning channel; never embed them in the APK, source repository, build logs, or CI artifacts.
- Copy into app-private storage with restrictive permissions, atomic staging/rename, bounded file sizes, and cleanup of partial imports.
- Validate using the existing loader before marking the provider ready: parse PKCS#8 EC key, parse X.509 certificate bundle, verify P-256 expectations and private/public key correspondence.
- Persist only an identity-present/validation state; do not log, export, back up, or display key bytes or certificate contents.
- On missing, invalid, expired, or mismatched material, return a distinct actionable provider-unavailable/identity-invalid error and do not claim MFi success.
- Do not use an arbitrary self-generated key as an MFi credential; validation of key/certificate structure is not proof of Apple MFi authorization.
- Keep hardware I2C and USB/CH341 backends explicitly selectable. Never silently fall back between backends.

## API 17 compatibility

The original bootstrap already chooses Context.getFilesDir() below API 21, so the private-storage import implementation must use APIs available on Android 4.2.2/API 17 and must not depend on Android Keystore features introduced after API 17. A provisioning implementation should keep the private key in app-private storage and should not weaken the file permissions for convenience.

## Status

Source call-chain traced from recovered Smali. No runtime import code is claimed in this commit. The next code change should implement and test the external identity provisioning boundary before attempting a real LOCAL MFi session.