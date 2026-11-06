# DiPlay 0.3.11 transport and scheduling performance audit

Date: 2026-11-06
Repository: `qiangge5586-a11y/1`
Target baseline: Android 4.2.2 / API 17 / ARMv7 / 800x480

## Scope

Reviewed the RFCOMM byte stream, bounded ring buffer, single-owner iAP2 pump scaffold, offline model driver, Android prototype source set, and diagnostic dashboard. This is a source-level audit; it is not runtime profiling or a claim of measured frame-rate improvement.

## Changes made

### 1. RFCOMM close could wait behind a blocked writer

Before the change, `BluetoothRfcommByteStream.close()` acquired `writeLock` before calling `BluetoothSocket.close()`. The writer holds that same lock around `OutputStream.write()`. If the write blocks, close cannot reach the socket close operation that is needed to interrupt the write. This creates a shutdown deadlock/stall risk.

Now close marks the stream closed under `readLock`, wakes blocked readers, releases that lock, and closes the Bluetooth socket without acquiring `writeLock`. A concurrent writer is then interrupted by carrier closure and observes the resulting I/O failure. Concurrent writes remain serialized by `writeLock`.

### 2. Pump outbound queue could monopolize the owner thread

Before the change, `deliverCommands()` drained the entire bounded producer queue before returning to the read/timer cycle. Under a full queue, inbound ACK/data processing could be delayed by the time spent delivering all queued commands.

Now the pump delivers at most 8 commands per turn, drains resulting output, then returns to the receive/timer cycle. FIFO order and the existing 64-command / 1 MiB queue limits are unchanged. This is a fairness bound, not a change to protocol framing or ACK policy.

### 3. Listener callback thread contract made explicit

Pump listener callbacks execute on the pump worker. A listener that updates Android views directly or blocks waiting for UI work can stall transport reads and protocol timers. The interface now explicitly requires callbacks to return promptly and dispatch UI changes to the main thread.

## Existing cost centers and constraints

- RFCOMM uses a dedicated reader because the Bluetooth socket input read is blocking; the bounded ring then copies bytes into the pump's read buffer. Removing that hop safely requires a carrier cancellation/timeout design, not simply reading the socket on the pump.
- Pump command admission copies payload arrays to preserve caller ownership. Removing that copy requires a documented ownership-transfer contract and tests for caller mutation; it is not changed here.
- The offline `Iap2LinkModelPumpDriver` copies inbound chunks and aggregates output with `ByteArrayOutputStream`. It is explicitly a test/model adapter, not the production source-parity engine. Do not use its allocation profile as evidence of production engine behavior.
- The pump worker is single-owner for driver and stream I/O. Do not move protocol state mutation to producer/UI threads.
- MainActivity's diagnostic ROOT probe already runs on a worker and posts results to the main thread. USB and Bluetooth read-only button handlers currently perform their small diagnostic calls directly in the click callback; measure before changing these UI-only paths.
- The Android prototype source set includes the iAP2 models and pump scaffold, but this does not mean the original DiPlay production engine has been replaced or connected to this prototype.

## Validation required

1. Run the Java 7 iAP2/RFCOMM offline suites and Android API 17 build.
2. Add/execute a transport-close test with a deliberately blocked output write when a controllable socket test seam is available.
3. Add a pump fairness test proving inbound bytes/timers are serviced while more than 8 commands remain queued.
4. On device, record read/write duration, queue depth, command batch count, ACK delay, and close-to-worker-termination latency.
5. Only consider further copy elimination after allocation profiling and explicit byte-array ownership tests.

## Not changed

No iAP2 frame format, sequence/ACK behavior, retransmission timing, CSM framing, MFi identity/authentication, AirPlay crypto, nonce/counter handling, or ROOT hotspot write behavior was changed.


## Buffer-copy audit (follow-up)

The RFCOMM receive path currently performs two bounded copies after the socket read:

1. Reader-owned 8 KiB scratch chunk -> bounded ring storage.
2. Ring storage -> pump-owned 8 KiB input buffer.

These copies are intentional ownership boundaries: the reader reuses its scratch array on the next socket read, while the pump/driver owns its current input window. Directly exposing the reader scratch array to the pump would introduce a race when the reader overwrites it. Removing the ring would also remove the independent timeout-aware pump read contract and would require a separately proven cancellation/timeout mechanism on Android API 17.

The pump outbound sendControl() copies caller payloads before queueing. This prevents caller mutation after admission. It should remain until an explicit ownership-transfer API is introduced and tested; silently removing the copy is unsafe.

Conclusion: do not remove these copies based only on source inspection. The next optimization should add low-overhead counters/timing around socket read, ring occupancy, pump read, driver onBytes, driver output, and socket write. Use aggregate counters (not per-packet logcat output), then profile on target hardware to determine whether either copy is material.


## Aggregate pump instrumentation

Added Iap2PumpMetrics, an API-17-compatible aggregate snapshot exposed by Iap2LinkPump.metricsSnapshot(). It records read calls/bytes/time, driver input bytes/time, timer calls/time, write calls/bytes/time, delivered commands, and maximum queued command count/bytes. Updates use AtomicLong counters and max high-water marks; there is no per-packet logcat output and no allocation per metric event. Snapshot allocation occurs only when a caller explicitly requests metricsSnapshot(). These counters measure pump-level stream and driver costs; they do not yet measure the RFCOMM reader thread's socket.read duration or ring occupancy. Device-side sampling is still required before selecting further optimizations.
