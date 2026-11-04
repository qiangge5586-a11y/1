# Live OBD overlay on the meter preview

## Behavior

When a user connects a paired ELM327 adapter and the controller reports a live session, the meter preview overlays four OBD values:

- `speed` from PID 010D
- `rpm` from PID 010C
- `coolant` from PID 0105
- `fuel` from PID 012F

Speed is converted from km/h to mph for US/Imperial display modes. Coolant is converted from °C to °F in those modes. RPM and fuel percent are shown directly.

If an advertised PID is unsupported or its response is missing, that field displays `--`; the demo value is not substituted while the live OBD session is active. Non-OBD route/distance/economy tokens are also replaced with `--` so sample values are not mistaken for live data. On disconnect or when the Activity pauses, the overlay is cleared and the preview returns to clearly labelled demo values.

## Fields unavailable in live mode

Road names, turn instructions, route distance, ETA, distance-to-empty, trip distance, fuel economy, speed limits, camera distance and gear display `--` while the live OBD overlay is active. Standard generic OBD PIDs in this session do not provide those values. Compass remains sourced from the device sensor/GPS course when separately enabled.

## Validation

Unit tests verify that disconnected mode preserves demo values, live values override only the four OBD fields, speed/coolant unit conversions are correct, and unavailable PIDs display placeholders. Real adapter and vehicle validation is still required.
