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

## Runtime path verification

- The normal Java reconstruction of `C0269ua.getMeterLayout()` incorrectly showed `meter_none` on every path. This was a JADX switch/control-flow reconstruction error, not the actual DEX behavior.
- A second pass using JADX's raw-instruction fallback mode confirmed the actual branch returns:
  - `civic` / `clarity` → `meter_civic` and internal `CIVIC` enum.
  - `crv` / `breeze` → `meter_crv` and internal `CRV` enum.
  - `avancier` / `avancier240` / `urv` / `cdx` → `meter_avancier` and internal `AVANCIER` enum.
- The renderer then inflates the selected layout and binds the speed, navigation, compass, camera and six vehicle-information views. This confirms that the supplied APK contains an actual Civic-specific layout-selection path.
- This is evidence of the software's Civic model branch only. It does not identify the exact Civic year/trim, factory head-unit firmware, display host API or prove that the 2019 OEM center display can install/run this APK.

## Compatibility and data boundaries

- The original XML resource proves that the APK contains a Civic-specific meter layout, not that it works on every Civic year/trim or that the 2019 Civic factory center display can host this APK.
- No live vehicle telemetry is connected. Every value in the Civic template is sample data.
- The six fields are vehicle-computer trip/consumption data; they are not interchangeable with generic OBD speed/RPM/fuel values. A real adapter requires a documented read-only source and unit/calibration validation.


## Unit handling

The original renderer maps its unit codes to kilometres/miles, fuel-efficiency formats (km/L, mpg, imperial mpg or L/100km), and Celsius/Fahrenheit. The rebuild's Civic demo explicitly chooses metric display labels (km, L/100km, °C) as sample defaults. These are not read from the vehicle and are not asserted to match the user's actual cluster settings.
