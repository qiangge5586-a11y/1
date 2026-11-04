# Live OBD overlay on the meter preview

## Behavior

When a user connects a paired ELM327 adapter and the controller reports a live session, the meter preview overlays four OBD values:

- `speed` from PID 010D
- `rpm` from PID 010C
- `coolant` from PID 0105
- `fuel` from PID 012F

Speed is converted from km/h to mph for US/Imperial display modes. Coolant is converted from °C to °F in those modes. RPM and fuel percent are shown directly.

If an advertised PID is unsupported or its response is missing, that field displays `--`; the demo value is not substituted while the live OBD session is active. On disconnect or when the Activity pauses, the overlay is cleared and the preview returns to clearly labelled demo values.

## Fields that remain demo

Road names, turn instructions, route distance, ETA, compass unless a separate sensor/GPS source is active, trip/fuel-economy values and gear remain demo/sample fields. Standard OBD PIDs do not provide a universally available gear position or navigation route.

## Validation

Unit tests verify that disconnected mode preserves demo values, live values override only the four OBD fields, speed/coolant unit conversions are correct, and unavailable PIDs display placeholders. Real adapter and vehicle validation is still required.
