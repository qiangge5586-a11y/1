# Navigation state adapter boundary

## Implemented

- `NavigationState` is an immutable, validated snapshot containing source, destination, current road, next maneuver, distance, ETA and active/inactive state.
- `NavigationStateProvider` defines a read-only contract that future authorized integrations can implement.
- `MockNavigationStateProvider` supplies a deterministic sample route and inactive state for UI testing.
- The navigation panel can advance and reset the sample state.

## Not implemented

- No Amap/Baidu broadcasts, undocumented content providers, accessibility scraping, notification interception, location tracking, or vehicle-bus integration is used.
- The current UI state is explicitly labelled demo data; it is not live navigation information.
- A real provider requires a documented, permission-appropriate interface and verification on the target head unit before it can be enabled.
