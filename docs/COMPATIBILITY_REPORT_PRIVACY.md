# Compatibility report privacy controls

## Default report

The default report includes manufacturer, model, Android release/API level, supported ABIs, display resolution/density and a note that root access was not tested. Detailed firmware identifiers are excluded.

## Optional detailed report

The user can explicitly enable the detailed-identifier checkbox before sharing. This adds device, product, board, hardware and build fingerprint values, which can identify a specific firmware build. The checkbox defaults off and is not persisted.

## Validation

Unit tests verify that the default output omits detailed identifiers, the opt-in report includes them, and missing values are rendered safely. Sharing always rebuilds the report from the current checkbox state.
