# OBD-II engine warning-lamp status

The OBD section has an optional **Read check-engine light status (PID 01)** action. The user must confirm before the controller sends standard Mode 01 PID 01 (`0101`).

The decoder reads:
- MIL (malfunction indicator lamp) on/off state from bit 7 of the first response byte
- Stored DTC count from the lower 7 bits of that byte

The remaining three response bytes are retained as raw monitor-status fields in the decoder result but are not interpreted as a readiness checklist in this phase.

Invalid, missing or negative ECU responses display as unavailable. This request is queued on the Bluetooth worker and does not clear codes, reset the ECU or write to vehicle memory. Actual response behavior still needs a physical adapter/vehicle test.
