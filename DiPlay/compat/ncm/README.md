# Offline NCM / NTB16 compatibility primitive

This package is a small Java 7 byte-array codec ported from the original DiPlay 0.3.11 Ntb16Codec format.

- Encodes one Ethernet frame into an NTH16 + NDP16 NTB block.
- Parses NTH16/NDP16 datagrams with range, declared-block-length, signature, NDP-chain loop and datagram-bound checks.
- Preserves the original one-byte zero-length-packet marker behavior when the NTB block length is an exact multiple of the 512-byte USB packet size.
- Contains no Android framework dependency, USB API call, socket, root command or device I/O.
- The standalone self-test runs on a desktop JVM and uses synthetic fixtures only.

## Test

The Ntb16CodecTest self-test covers round-trip payload, little-endian sequence, 512-byte boundary/ZLP behavior, invalid signature, invalid range, invalid NTH header, maximum u16 block length, and invalid build arguments.

The codec is compiled into the isolated Android compatibility APK as an unused/offline primitive. It is not yet wired to USB endpoints or the NCM bridge. The original NCM driver remains unported and requires API 17 hardware/driver validation.