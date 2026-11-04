# Vehicle telemetry adapter boundary

## Implemented

- `VehicleSnapshot` is an immutable, validated model for speed, engine RPM, fuel percentage, coolant temperature and gear.
- `VehicleDataProvider` defines a read-only interface for future adapters.
- `MockVehicleDataProvider` supplies static offline demo values.
- The main screen labels the values as demo data and offers a refresh action.

## Not implemented

- No CAN bus, OBD-II, ECU, Bluetooth dongle, OEM AIDL service, GPS, or factory head-unit connection is active.
- No vehicle control or write operation exists.
- Range validation prevents impossible values from entering the display model, but does not validate a vehicle's actual sensor calibration.
- A production adapter requires exact head-unit model/build details and a documented, authorized read-only interface, then bench validation before vehicle testing.
