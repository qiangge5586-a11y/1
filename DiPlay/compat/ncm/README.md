# Offline NCM / NTB16 compatibility primitive

This package is a small Java 7 byte-array codec ported from the original DiPlay 0.3.11 Ntb16Codec format.

- Encodes one Ethernet frame into an NTH16 + NDP16 NTB block.
- Parses NTH16/NDP16 datagrams with range, declared-block-length, signature, NDP-chain loop and datagram-bound checks.
- Supports multiple datagram entries in one NDP and chained NDP tables.
- Rejects NDP tables shorter than the 8-byte header plus 4-byte terminator.
- Preserves the original one-byte zero-length-packet marker behavior when the NTB block length is an exact multiple of the 512-byte USB packet size.
- Includes an incremental stream decoder for arbitrary USB chunk fragmentation, concatenated NTBs, the original optional short-packet pad handling, bounded buffering and header resynchronization.
- Includes a pure-Java CDC-NCM interface selector that mirrors the original interface-class, alternate-setting, bulk-endpoint and status-endpoint selection rules; it consumes fixtures only and never opens or claims USB interfaces.
- Contains no Android framework dependency, USB API call, socket, root command or device I/O.
- The standalone self-test runs on a desktop JVM and uses synthetic fixtures only.

## Test

The Ntb16CodecTest self-test covers 14 cases: round-trip payload, little-endian sequence, 512-byte boundary/ZLP behavior, invalid signature/range/NTH header, maximum u16 block length, invalid build arguments, multiple datagrams, chained NDPs with cycle detection, undersized NDP, out-of-block datagram rejection, and nonzero source-buffer offset. It also feeds 2,000 deterministic malformed/random byte slices to the parser and verifies that they are rejected or handled without throwing.

The Ntb16StreamDecoderTest self-test covers 11 cases: fragmented NTB input, multiple concatenated blocks, optional short-packet pad consumption, preserving the next NTB when no pad byte is present, recovery after leading garbage, buffer-limit enforcement, reset behavior, resynchronization when garbage and an NTB header arrive across separate input chunks, a pad byte arriving in a later chunk, and preservation of a partial next NTH16 header after a 512-byte boundary block. The decoder is modeled on the original NcmUsbBridge framing loop but remains a standalone offline component; it is not connected to USB bulk reads.

The NcmInterfaceSelectorTest self-test covers 9 cases: CDC control/data selection, alternate-setting 1 preference, alternate-setting 0 claim sibling, missing control rejection, ambiguous/missing bulk endpoint rejection, unique optional status endpoint handling, and first matching control-interface ordering, stable first-candidate behavior when multiple alternate-setting-1 data interfaces exist, and fallback to the first eligible data interface when alternate setting 1 is absent.

The codec, stream decoder and interface selector are compiled into the isolated Android compatibility APK as offline primitives. They are not wired to USB endpoints or the NCM bridge. The original NCM driver remains unported and requires API 17 hardware/driver validation.
