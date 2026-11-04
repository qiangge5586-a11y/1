# Original Civic meter telemetry decoding rules

## Confirmed input fields

The original meter reads integer fields named `SET_SPEED`, `DISTANCE_TO_EMPTY`, `TRIP_A_DISTANCE`, `TRIP_A_UNIT`, `AVG_FUEL_EFF`, `AVG_FUEL_EFF_UNIT`, `AVG_FUEL_EFF_A`, `AVG_FUEL_EFF_A_UNIT` and `ENG_COOLANT_TEMP`.

## Display transformations

- Speed is displayed as an integer.
- Distance unit code 0 means km; any other code means mi.
- Distance-to-empty is displayed as an integer in the selected distance unit.
- Trip A distance is divided by 10; values above 10,000 use integer formatting, otherwise one decimal.
- Average fuel-efficiency fields are divided by 10 and displayed with one decimal.
- Fuel unit code 0 means km/L, 1 means mpg, 2 means imperial mpg, and all other values mean L/100km.
- Coolant temperature is raw integer minus 40; unit code 0 means °C, otherwise convert Celsius to Fahrenheit and round to an integer.
- Missing/negative values use the original display placeholders where applicable.

## Rebuild implementation

`CivicTelemetryDecoder` implements these pure value transformations and is covered by unit tests. It does not read the original privileged file channel, issue shell commands, receive Xposed broadcasts, or connect to any vehicle. It is a protocol/display decoder only; no live data source is present.

Do not apply these scaling rules to generic OBD-II values or another adapter unless its data contract confirms identical units and offsets.
