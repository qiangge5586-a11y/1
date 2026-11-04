# Standard OBD-II stored trouble-code decoder

## Scope

`ObdDtcDecoder` parses positive SAE J1979 Mode 03 responses beginning with `43` and converts two-byte DTC records to standard codes such as `P0133`, `C0133`, `B0133` and `U0133`.

- Accepts compact or spaced ELM text, optional CAN headers, echoed `03` command and the prompt suffix.
- Preserves ECU response order and removes duplicate codes.
- Skips `0000` padding.
- Negative, no-data, truncated, oversized or malformed responses return an empty list.

## Optional explicit read action

After a paired ELM327 adapter is connected, the user can separately request stored (Mode 03), pending (Mode 07) or permanent (Mode 0A) codes, each with a second confirmation dialog. Requests are queued on the Bluetooth session worker so they do not race the telemetry poller. Results are displayed in memory only. No category is read automatically on connection.

## Safety boundary

Only the exact standard commands `03`, `07` and `0A` are allowed for these diagnostic reads. Mode 04 clear-DTC, freeze-frame erase, ECU reset, actuator control, coding and write commands remain blocked. Each request requires an active foreground adapter session and an explicit confirmation tap.

## Validation

Unit tests cover P/C/B/U code families, order/deduplication, zero padding, optional CAN header, echoed command, negative/no-data responses and malformed/truncated/oversized input.


## Generic offline descriptions

The result list includes a small offline dictionary for common generic codes such as P0101, P0113, P0128, P0133, P0171, P0300, P0420, P0442, P0455, P0500 and P0700. Unknown but syntactically valid codes are shown without an invented explanation. Descriptions are generic OBD-II meanings, not Honda-specific repair procedures; exact interpretation and diagnosis must be checked against the vehicle's service information.
