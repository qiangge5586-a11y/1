# Civic compass and track-up options

## Original APK preference parity

The original meter preference screen includes:
- Enable compass (default on).
- Track-up mode, dependent on compass being enabled (default off).

## Rebuild behavior

- The Civic demo editor exposes both options and persists them locally.
- Disabling the compass hides the compass widget and disables track-up; track-up is reset off.
- Track-up changes the sample compass label to an arrow-prefixed heading.
- The UI explicitly says orientation is simulated. No device compass sensor, GPS bearing, map rotation or live navigation provider is connected.

## Validation

Unit tests cover widget visibility, label changes and explicit simulated-mode descriptions.
