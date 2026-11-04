# Device compass sensor integration

## Behavior

- Uses Android's public `TYPE_ROTATION_VECTOR` sensor when available.
- Converts azimuth to one of eight compass directions and accounts for the display rotation.
- Starts only while the meter Activity is foregrounded and the compass option is enabled.
- Stops sensor updates in `onPause`; no background sensor collection is performed.
- Requires no location permission and does not access GPS, navigation apps, vehicle services, CAN, OBD or ECU data.
- If the device has no rotation-vector sensor or registration fails, the preview falls back to the clearly labelled sample heading.

## Track-up limitation

Track-up remains a display-only demo choice. A device compass provides device orientation, not the direction of travel. A real track-up mode requires a validated navigation course/bearing source and is not inferred from the compass sensor.

## Validation

Unit tests cover all eight directions, negative/overflow normalization, sector boundaries and non-finite values. Sensor accuracy and display-axis alignment require physical device testing; the head unit may not contain a rotation-vector sensor.
