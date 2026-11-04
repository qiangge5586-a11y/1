# Original vehicle telemetry transport audit

## Confirmed transport design

The original meter does not simply read standard OBD-II values from a Bluetooth adapter. Its six Civic vehicle-information fields are bridged through a privileged local file channel:

- `RunnableC0284xa.run()` creates `/data/local/tmp/shared_files`, creates the shared integer files and applies `chmod -R 777` to the directory.
- `C0185dc` maps a symbolic key to `/data/local/tmp/shared_files/<KEY>`, opens it with `RandomAccessFile`, and uses `FileChannel.lock/tryLock` to write/read integer values.
- `C0269ua` reads `SET_SPEED`, `DISTANCE_TO_EMPTY`, `TRIP_A_DISTANCE`, `TRIP_A_UNIT`, `AVG_FUEL_EFF`, `AVG_FUEL_EFF_UNIT`, `AVG_FUEL_EFF_A`, `AVG_FUEL_EFF_A_UNIT` and `ENG_COOLANT_TEMP`.
- The meter's polling runnable checks speed on its periodic loop and refreshes the other vehicle fields every twentieth iteration.
- Xposed callback classes write selected values such as average fuel efficiency and coolant temperature into the same files. Broadcast actions also update trip and fuel values.

## Security and compatibility implications

- The directory is under `/data/local/tmp`, outside normal app-private storage, and its permissions are explicitly widened to world-writable.
- The design relies on root/privileged shell setup and Xposed hooks into another process. It is not a public Android API and is not a documented Honda vehicle interface.
- The Mitsubishi/Clarion service bindings are a separate integration path; the file bridge is the meter's shared data transport.
- Reproducing this mechanism would require privileged system access and would create a broad local write surface. It is intentionally excluded from this rebuild.

## Rebuild decision

- Keep `VehicleDataProvider` as a read-only boundary.
- Keep all current Civic values as static demo data.
- Do not create `/data/local/tmp/shared_files`, grant broad file permissions, read privileged logcat, or install Xposed/root hooks.
- A real vehicle adapter requires a documented, authorized read-only interface for the exact 2019 Civic head unit, validated on a bench/spare device before any in-vehicle trial.
