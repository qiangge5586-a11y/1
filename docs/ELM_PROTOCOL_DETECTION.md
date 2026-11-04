# ELM327 protocol identification

## Behavior

After the user explicitly connects a paired Bluetooth SPP adapter, the controller runs the existing harmless ELM initialization sequence and then issues the read-only adapter identification commands `ATDP` and `ATDPN`.

- `ATDP` reports the detected protocol description.
- `ATDPN` reports the ELM protocol number, including the automatic-detection prefix when supplied by the adapter.
- Responses are trimmed, line breaks are normalized and displayed text is bounded to 80 characters.
- The detected protocol is shown in the connection status before standard PID polling begins.

## Safety boundary

These commands query adapter status; they do not change the selected protocol or send additional ECU control commands. The app still uses automatic protocol selection (`ATSP0`) and the strict Mode 01 / confirmed DTC allowlists. A reported protocol is not proof that every vehicle PID is supported.

## Validation

Unit tests cover normal, missing, unknown, multiline and overlong adapter responses. Real adapter/vehicle verification remains outstanding.
