# Foreground hardware key event monitor

## Behavior

- The user explicitly enables the monitor from the steering-key section.
- While this Activity is in the foreground, Android key-down events are displayed by key name and numeric code.
- Unmapped events are passed to the normal Activity dispatch chain unchanged. Explicitly captured and mapped keys may trigger only local in-app navigation actions.
- Monitoring is disabled by default and is not persisted across launches.

## Boundaries

- No accessibility service, input-method hook, global key interception, root permission, Xposed hook, or background listener is used. Capture rejects Home, Back, Power and volume keys.
- A mapped key can navigate to the meter editor, navigation app list, voice-assistant settings or road-information demo. No key event is translated into a vehicle or media command.
- Steering-wheel buttons are visible only if the head unit routes them to this foreground Activity; many factory units consume or redirect those events before third-party apps can observe them.
- Test only while parked. Do not rely on this monitor for driving controls.
