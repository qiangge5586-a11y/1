# Standard OBD-II stored trouble-code decoder

## Scope

`ObdDtcDecoder` parses positive SAE J1979 Mode 03 responses beginning with `43` and converts two-byte DTC records to standard codes such as `P0133`, `C0133`, `B0133` and `U0133`.

- Accepts compact or spaced ELM text, optional CAN headers, echoed `03` command and the prompt suffix.
- Preserves ECU response order and removes duplicate codes.
- Skips `0000` padding.
- Negative, no-data, truncated, oversized or malformed responses return an empty list.

## Optional explicit read action

After a paired ELM327 adapter is connected, the user can tap **Read stored fault codes (Mode 03)** and confirm a second dialog. The request is queued on the Bluetooth session worker so it does not race the telemetry poller. The result is displayed in memory only. The action is never automatic on connection.

## Safety boundary

Only the exact standard command `03` is allowed for this diagnostic read. No `04` clear-DTC request, freeze-frame erase, ECU reset, actuator control, coding or write command is permitted. The request requires an active foreground adapter session and an explicit confirmation tap.

## Validation

Unit tests cover P/C/B/U code families, order/deduplication, zero padding, optional CAN header, echoed command, negative/no-data responses and malformed/truncated/oversized input.
