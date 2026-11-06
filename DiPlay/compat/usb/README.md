# Passive USB descriptor model

This module is the first isolated USB compatibility step for the DiPlay Honda project.

- Pure Java 7 value object; no Android imports.
- Stores VID, PID, device class/subclass/protocol and interface count.
- Rejects values outside USB descriptor integer ranges.
- Descriptor key excludes serial numbers and is not guaranteed unique.
- No USB permission request, interface claim, endpoint access, transfer, or device mutation.

The model is fixture-testable on the CI runner. It is not yet wired to Android's USB Host APIs and does not identify CarPlay, NCM, or any vendor-specific protocol.
