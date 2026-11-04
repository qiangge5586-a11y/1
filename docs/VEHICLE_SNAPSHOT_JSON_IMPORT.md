# One-time vehicle snapshot JSON import

## Format

```json
{
  "speedKph": 42,
  "engineRpm": 2100,
  "fuelPercent": 65,
  "coolantTempC": 91,
  "gear": "D",
  "connected": true
}
```

Required fields: speedKph, engineRpm, fuelPercent, coolantTempC, and gear. connected is optional and defaults to true.

## Workflow and validation

- User selects a JSON document through Android's Storage Access Framework.
- The file is read once with a 64 KiB limit and validated for required fields, integer values, boolean connection state and telemetry ranges.
- The validated JSON snapshot is stored locally and shown with source label "Imported JSON snapshot".
- Refresh demo values clears the imported snapshot and restores mock data.

## Limitations

- This is a one-time file import, not a live feed or vehicle connection.
- No /data/local/tmp/shared_files access, root, CAN, OBD, ECU, Bluetooth or undocumented OEM interface is used.
- A future live adapter must be separately documented, authorized and bench-tested.
