# Civic meter vehicle-information field selection

## Original APK behavior confirmed

The original `res/xml/pref_meter.xml` exposes a multi-select preference named `vehicle_information_list`. Its six values are:

1. Distance to empty
2. Current drive distance
3. Average fuel efficiency
4. Trip A distance
5. Trip A average fuel efficiency
6. Engine coolant temperature

The original defaults select the first three fields.

## Rebuild implementation

- Added six explicit checkboxes to the Civic demo editor.
- Defaults match the original first-three selection.
- Choices are saved locally in the app's meter-editor preferences.
- The selected bottom-row fields are reflowed evenly across the meter canvas; unselected fields are omitted.
- Selection affects only the offline demo preview. It does not access the vehicle's ECU, CAN bus, OBD adapter or Honda head-unit services.

## Tests

Unit tests cover the original default selection, all-six layout, sparse selection/reflow, empty selection and short-input handling.
