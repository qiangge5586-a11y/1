# Head-unit compatibility diagnostics

## Report fields

- Android manufacturer, model, device and product.
- Board and hardware identifiers.
- Android release/API level and supported ABIs.
- Build fingerprint.
- Physical display resolution and density metrics.

The user can share the report through Android's share chooser. Sharing is an explicit user action.

## Safety and limitations

- Uses Android public read-only build and display APIs.
- Does not probe root, read protected system files, query private OEM services, or change device settings.
- The report helps identify a head unit but does not prove compatibility with Honda Civic factory-screen integration.
- Build fingerprint and hardware identifiers may reveal firmware details; review the report before sharing it publicly.
