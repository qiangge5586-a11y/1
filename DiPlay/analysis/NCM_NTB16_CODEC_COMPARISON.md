# Original DiPlay NCM / NTB16 codec comparison

## Evidence inspected

This note compares the JADX Java-like output from the original DiPlay 0.3.11 APK:

- `com.shilapi.xcertplay.transport.Ntb16Codec`
- `com.shilapi.xcertplay.transport.NcmUsbBridge`

The APK's JADX report contains 104 decompilation errors overall. The codec methods inspected here are readable, but this remains a static-analysis result rather than a lossless source reconstruction.

## Confirmed original behavior

- NTH16 signature `0x484D434E`, 12-byte NTH16 header.
- NDP16 signature `0x304D434E`, NDP header at offset 12 for the encoder.
- One Ethernet datagram begins at byte offset 28.
- Sequence and lengths are little-endian u16 values.
- The encoder appends one zero byte when the NTB block length is exactly divisible by 512.
- The original parser follows the NDP next-pointer chain, iterates 4-byte datagram entries, and caps chain traversal by a hop count.
- `NcmUsbBridge.send()` calls the codec, then writes the resulting byte array through chunked USB bulk OUT transfers.
- The receive path buffers bulk IN data, reads the NTH16 declared block length, passes exactly that block to the parser, discards the consumed block, and separately handles an optional one-byte short-packet pad.

## Compatibility implementation differences

The isolated Java 7 codec preserves the original wire-format constants, single-frame encoder layout, sequence handling, and 512-byte padding convention.

It intentionally hardens parsing compared with the decompiled original:

- Validates the declared NTH16 block length against the supplied slice.
- Rejects NDP pointers outside the declared block.
- Rejects NDP tables shorter than the 8-byte NDP header plus the 4-byte terminator.
- Uses a visited-offset set to stop cyclic NDP chains.
- Uses widened arithmetic for datagram end calculations.
- Skips datagrams that extend outside the declared NTB block.
- Tests nonzero source-buffer offsets, multiple entries, chained NDPs and a cyclic chain.

These checks may reject malformed buffers that the original parser might partially accept. They do not alter valid single-frame encoding.

## API 17 and integration status

The original bridge contains both an asynchronous `UsbRequest` read path and a synchronous `bulkTransfer` fallback. The synchronous method is present as a separate implementation. The exact async-selection decision is supplied to `NcmUsbBridge.open$shared(..., useAsyncRead, ...)`; do not infer runtime behavior from the existence of the branch alone.

The compatibility app currently compiles the codec as an offline primitive only. It does not open USB devices, claim interfaces, send vendor control requests, call bulkTransfer, or connect this codec to the original NCM bridge. No MY16ADA/API 17 USB or iPhone test has been performed.

## Next integration gates

1. Inspect the caller that chooses `useAsyncRead` and the `SharedUsbConnection` implementation.
2. Audit legacy USB interface claim/alternate-setting behavior on API 17.
3. Port a passive, deterministic NCM stream parser before enabling USB I/O.
4. Test synthetic fragmented NTB input and short-packet padding.
5. Only after API 17 emulator/static gates pass, perform explicitly authorized hardware tests with read-only diagnostics first.
