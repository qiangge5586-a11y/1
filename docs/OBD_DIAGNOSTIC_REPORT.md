# Local OBD-II diagnostic report

The OBD-II section provides a **Copy OBD-II diagnostic report** action. It copies the current connection/protocol status, ECU-supported dashboard PID list and most recent DTC result to the Android clipboard only after the user taps the button.

- The report is not uploaded, emailed or shared automatically.
- It excludes VIN, adapter MAC address and location.
- Control characters are removed and each section is bounded.
- The report states that the current requests are read-only and do not clear DTCs or write to the ECU.
- The user should review the copied text before sharing it with a mechanic.

The report is a convenience summary, not a repair diagnosis or proof of real-vehicle compatibility.
