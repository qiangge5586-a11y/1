# Read-only OBD-II PID query plan

## Purpose

The decoder phase can interpret standard Mode 01 responses, but an adapter also needs to discover which PIDs the ECU actually supports. `ObdPidQueryPlan` provides that deterministic, transport-independent plan.

## Query sequence

1. Send `0100` to request support for PIDs 01–20.
2. If the response advertises PID 20, send `0120`.
3. If the 0120 response advertises PID 40, send `0140`.
4. Schedule only supported dashboard PIDs from the returned bitmaps, in a stable order.

The selected display PIDs are 04, 05, 0B, 0C, 0D, 0E, 0F, 10, 11, 2F, 33, 5C and 5E. Missing or malformed bitmap responses never create a query for an unsupported PID.

## Safety boundary

- Only standard read-only Mode 01 current-data requests are generated.
- No Mode 04 clear-diagnostic-trouble-codes, actuator control, programming, ECU reset or manufacturer-specific PID is included.
- This class does not connect to Bluetooth, USB, Wi-Fi or the vehicle. It only builds command strings for a future explicitly selected adapter transport.
- The vehicle UI must continue to show demo values until a real adapter session returns validated ECU responses.

## Validation

Unit tests cover bitmap bit ordering, extension discovery, stable command ordering, malformed/missing responses and PID-range validation.
