# Foreground GPS course and track-up

## Behavior

- GPS course is disabled by default.
- The user enables it from the meter settings; only then does the app request `ACCESS_FINE_LOCATION`.
- Uses only Android `LocationManager.GPS_PROVIDER`; it does not use the network provider or fetch coordinates from map apps.
- Location updates run only while the Activity is foregrounded and the option is enabled. Updates stop in `onPause`.
- The app does not display, persist, upload or share coordinates. It uses the location callback only to calculate GPS speed and bearing in memory.
- Track-up uses GPS bearing only when a bearing and speed are present and speed is at least 5 km/h. At low speed, without a fix, or without permission, it falls back to the device compass/sample heading.
- GPS speed is labelled separately from vehicle/ECU speed; it is not presented as a live CAN/OBD vehicle speed value.

## Permission and fallback

The app requests fine and coarse location together only after the user enables the checkbox (as required for Android 12+ permission handling). GPS course is used only when precise location is granted; approximate-only access is not used. If permission is denied or GPS is disabled, the feature reports the reason and does not use a network fallback. The user can disable the option at any time.

## Validation

Unit tests cover the 5 km/h course threshold, missing bearing/speed, invalid speed values and display formatting. Device validation is required to check GPS fix quality and course stability on the target head unit.
