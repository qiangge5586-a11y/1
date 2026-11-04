# Standard OBD-II stored trouble-code decoder

## Scope

`ObdDtcDecoder` parses positive SAE J1979 Mode 03 responses beginning with `43` and converts two-byte DTC records to standard codes such as `P0133`, `C0133`, `B0133` and `U0133`.

- Accepts compact or spaced ELM text, optional CAN headers, echoed `03` command and the prompt suffix.
- Preserves ECU response order and removes duplicate codes.
- Skips `0000` padding.
- Negative, no-data, truncated, oversized or malformed responses return an empty list.

## Safety boundary

This phase is parser-only. It does not send Mode 03 requests, clear DTCs, erase freeze-frame data, reset an ECU or write to a vehicle. The existing Bluetooth command allowlist remains unchanged. A later UI integration must require a separate user action and must remain read-only.

## Validation

Unit tests cover P/C/B/U code families, order/deduplication, zero padding, optional CAN header, echoed command, negative/no-data responses and malformed/truncated/oversized input.
