# Civic meter layout resource audit and safe preview

## Original APK resource findings

Fresh JADX resource extraction identified:

- `res/layout/meter_civic.xml` and `res/layout-zh-rCN/meter_civic.xml`.
- Both declare a fixed 584 × 215 px root layout.
- The layout includes speed, compass, navigation maneuver/road/distance, speed-limit/camera indicators and a six-item vehicle information row.
- The six configurable vehicle fields in `pref_meter.xml` / `arrays.xml` are:
  - distance to empty (`distance_to_empty`)
  - current drive distance (`current_distance`)
  - average fuel efficiency (`avg_fuel_eff`)
  - Trip A distance (`trip_a_distance`)
  - Trip A average fuel efficiency (`avg_fuel_eff_a`)
  - engine coolant temperature (`coolant_temp`)
- The original preference screen also exposes compass, track-up, road-information voice and screen-cast options.

## Rebuild implementation

- Added source-derived text tokens for the six vehicle fields plus compass, next/current road, remaining route, segment distance, speed limit and camera distance.
- Added `CivicMeterDemoData` with clearly labelled static sample values only.
- Added a "Load Civic 584×215 meter layout demo" action to the multi-widget editor.
- The preview canvas preserves the 584:215 aspect ratio.
- The new template is text-only and uses independently authored geometry; it does not copy original resource images, audio, Xposed code or vendor service calls.

## Runtime path caveat

- `C0269ua.getMeterLayout()` maps the `civic` and `clarity` model strings to the internal `CIVIC` enum, but the decompiled method's final return value is `meter_none` for all paths. The meter renderer later checks the enum and inflates the returned layout. This mismatch may be a modified-sample defect or a decompilation/control-flow issue; it must be checked against DEX before claiming the original APK actually activates `meter_civic.xml` at runtime.
- Therefore this rebuild's Civic template is based on the presence and contents of the original XML resources, not on a verified runtime layout selection path.

## Compatibility and data boundaries

- The original XML resource proves that the APK contains a Civic-specific meter layout, not that it works on every Civic year/trim or that the 2019 Civic factory center display can host this APK.
- No live vehicle telemetry is connected. Every value in the Civic template is sample data.
- The six fields are vehicle-computer trip/consumption data; they are not interchangeable with generic OBD speed/RPM/fuel values. A real adapter requires a documented read-only source and unit/calibration validation.
