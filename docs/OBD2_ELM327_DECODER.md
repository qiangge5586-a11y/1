# Standard OBD-II / ELM327 decoder (offline protocol layer)

## Scope

This phase adds a pure parser for ELM327 text responses and common SAE J1979 Mode 01 PIDs. It does not open Bluetooth, USB, Wi-Fi, CAN or any vehicle connection.

## Supported values

| PID | Value | Formula |
|---|---|---|
| 010D | Vehicle speed | A km/h |
| 010C | Engine RPM | (256 × A + B) / 4 |
| 0105 | Coolant temperature | A − 40 °C |
| 012F | Fuel level | A × 100 / 255 % |
| 0104 | Calculated engine load | A × 100 / 255 % |
| 0111 | Throttle position | A × 100 / 255 % |
| 0110 | MAF air flow | (256 × A + B) / 100 g/s |
| 010F | Intake air temperature | A − 40 °C |
| 015C | Engine oil temperature | A − 40 °C |
| 010B | Intake manifold absolute pressure | A kPa |
| 0133 | Barometric pressure | A kPa |
| 010E | Timing advance | A / 2 − 64 degrees |
| 015E | Fuel rate | (256 × A + B) / 20 L/h |

The parser accepts spaced or compact positive responses, optional 11-bit CAN headers, ELM command echo, SEARCHING text and the `>` prompt. Unsupported, negative or truncated responses return null rather than fabricated values.

## Query planning

The companion `ObdPidQueryPlan` starts with `0100`, follows only advertised `0120` / `0140` bitmap extensions, and schedules only supported common dashboard PIDs. It generates standard read-only Mode 01 commands only. See `docs/OBD2_READ_ONLY_QUERY_PLAN.md`.

## Important limitations

- Standard PID support varies by vehicle ECU; a null result means the response was absent or invalid, not that the vehicle has no such sensor.
- These are generic OBD-II PIDs, not Honda-specific enhanced PIDs.
- OBD speed is not the same as GPS speed. OBD engine/fuel values must not be replaced with GPS values.
- No gear position is decoded because it is not a universally supported standard Mode 01 PID.
- Phase 58 itself adds no hardware connection. Phase 59 adds an optional paired Bluetooth SPP controller with an explicit confirmation and a fixed read-only command allowlist; see `docs/BLUETOOTH_OBD_ADAPTER.md`. The offline demo snapshot remains separate from live OBD values.

## Optional Bluetooth adapter

The UI can list already-paired Bluetooth devices after explicit user action and a runtime permission check. A second confirmation is required before connecting. The controller runs only while the Activity is foregrounded, disconnects on pause, and uses a fixed allowlist of ELM setup commands plus four standard read-only Mode 01 requests. It does not scan, pair, reconnect in the background or send ECU write/control commands.

## Validation

Unit tests cover common PID formulas, CAN headers, ELM echo/prompt, no-data responses, truncated frames, unsupported PIDs, query bitmap planning and strict command allowlists. Hardware validation with a real ELM327 adapter remains outstanding.
