# ECU-supported dashboard PID report

After the user connects a paired ELM327 adapter, the app shows which of the four live dashboard fields the ECU advertised in the standard PID support bitmaps:

- `010D` — vehicle speed
- `010C` — engine RPM
- `0105` — coolant temperature
- `012F` — fuel level

Only the advertised fields are polled. If none of these four appears in the ECU support response, the UI says so and displays unavailable values instead of sample data. The supported-PID list is an in-memory connection status and is cleared on disconnect.

This is based on the adapter's current ECU response. It does not claim all 2019 Civic trims/markets support the same data, and CI cannot substitute for testing the user's actual vehicle and adapter.
