# Normalized Meter Canvas and Layout Controls (Phase 13)

Date: 2026-11-04  
Repository: `qiangge5586-a11y/1`

## Implementation

Added `MeterCanvasView`, a custom Android `View` that renders a preset using normalized 0..100 coordinates. It draws a background, a rounded meter panel and centered multi-line text with configurable color and text size.

The editor now exposes:
- Demo speed slider (0–240 km/h).
- Text size slider.
- Horizontal and vertical position sliders, constrained so the preset's panel remains inside the 0..100 canvas.
- Background theme cycle and text color cycle.
- Reset-to-default control.
- Automatic persistence of edited values in app-private `SharedPreferences`.

The preview now consumes the preset's declared width/height and text template, rather than using a fixed TextView layout. The editor still supports only the first view in a preset and a simple text renderer.

## Safety

All values remain mock values. No OBD, CAN bus, vehicle ECU, proprietary head-unit service, system setting or privileged Android API is accessed. Steering key buttons remain simulator-only.

## Validation status

Source changes are committed. GitHub Actions workflow is configured to run unit tests and assemble the debug APK on pushes touching `android/**`. The latest run's completion and APK artifact have not yet been independently verified; do not treat the editor as build-verified or vehicle-tested.

## Remaining editor work

- Multiple widgets per preset.
- Drag-and-drop positioning.
- Text alignment, stroke, shadow, font family and image backgrounds.
- Multiple named presets and import/export.
- Device-specific preview dimensions and aspect ratios.
