# Foreground hardware key event monitor

## Behavior

- The user explicitly enables the monitor from the steering-key section.
- While this Activity is in the foreground, Android key-down events are displayed by key name and numeric code.
- Events are passed to the normal Activity dispatch chain unchanged.
- Monitoring is disabled by default and is not persisted across launches.

## Boundaries

- No accessibility service, input-method hook, global key interception, root permission, Xposed hook, or background listener is used.
- No key event is translated into a vehicle command. Existing key mapping remains a simulator.
- Steering-wheel buttons are visible only if the head unit routes them to this foreground Activity; many factory units consume or redirect those events before third-party apps can observe them.
- Test only while parked. Do not rely on this monitor for driving controls.
