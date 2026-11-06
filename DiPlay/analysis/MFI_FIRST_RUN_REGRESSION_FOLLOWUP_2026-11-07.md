# DiPlay 0.3.11 MFi first-run regression follow-up

Date: 2026-11-07
Repository: qiangge5586-a11y/1
Target: DiPlay 0.3.11 Honda fork, package com.shihab.diplay.honda

## New user-confirmed fact

The user confirms that the original DiPlay 3.11 MFi setting was never manually changed. The original app connected successfully on both the OnePlus PLC110 phone and the Honda Civic factory head unit.

## Source-backed first-run path

The existing decompilation audit establishes:

1. `AirPlayPersistence.loadMfiTarget()` reads `mfi_target`; when absent or invalid it returns `MfiTarget.LOCAL`.
2. `DiPlayBootstrap.ensure(context, target)` copies the bundled `assets/offline-mfi` material into app-private storage when target is LOCAL.
3. The Honda fork patch changed the missing-preference default from LOCAL to I2C in persistence, changed the host initial target from LOCAL to I2C, and replaced the LOCAL settings choice with I2C.
4. The same patch deletes `assets/offline-mfi`.
5. The user's modified-build runtime report records `MFI target=I2C` and `open /dev/i2c-1 failed: ENOENT`.

Together these facts establish a first-run backend-selection regression: the modified build does not follow the original first-run LOCAL/bootstrap path and instead immediately attempts a Linux I2C device that is absent on the tested OnePlus phone.

## What is not established

- The original successful session's exact provider method calls and identity provenance have not been captured from a live trace.
- Static presence of the LOCAL provider and bundled offline identity does not establish that the bundled identity is authorized for production MFi authentication.
- The original connection success does not establish that LOCAL is an appropriate production provider for a redistributed or modified APK.
- No real MFi hardware, iPhone, or Civic head unit was contacted during this source review.

## Security and release constraint

Do not extract, copy, repackage, or reuse the bundled offline private identity as a way to make the modified build authenticate. Do not fake authentication success or silently fall back from unavailable I2C/USB/REMOTE providers to LOCAL.

## Safe next implementation sequence

1. Add explicit backend-state diagnostics: persisted target, effective target, bootstrap outcome, provider class selected, and failure class. Never log certificate, challenge, signature, private key, or identity bytes.
2. Stop rapid retry loops for deterministic backend-unavailable errors such as I2C ENOENT; expose an actionable backend-unavailable state.
3. Preserve explicit hardware provider choices (I2C and USB/CH341) and make unsupported/missing hardware a clear configuration error.
4. Keep LOCAL disabled for production unless a documented, authorized test identity and explicit test-mode policy are supplied by the maintainer.
5. To reproduce original LOCAL behavior for legitimate development testing, use a separately provisioned, authorized test identity outside the production APK and verify it against the MFi program/provider requirements. Do not use the bundled fixture as a production credential.
6. Build a new APK only after the diagnostic and backend-selection changes pass source-level and offline tests; label it as not hardware-validated until tested on the OnePlus and Civic.

## Current status

This follow-up records the user's first-run confirmation and the regression evidence. It does not change runtime code, credentials, backend defaults, or produce a new APK. The previously built artifact remains the last available APK and still contains the I2C-default regression.
