# Direct JSON preset file import and export

## User workflow

- Import: tap **Import preset JSON file**, choose a document through Android's Storage Access Framework, and the app reads and validates the JSON immediately.
- Export: open **Manage / export saved presets**, choose a preset, then select **Save JSON file** and choose a destination and filename.
- The existing paste-import and share-sheet export workflows remain available.

## Safety and data handling

- Uses Android document-picker intents and content URIs; it does not request broad storage permissions or scan shared storage.
- Imported content is limited to 1 MiB and is validated by `PresetLibrary.importPreset` / `MeterPreset.fromJson` before it is stored.
- Importing a preset with an existing name replaces that named entry, consistent with the existing preset-library behavior.
- Export writes UTF-8 JSON to the user-selected destination. No network upload or background file access is performed.
- Manual validation with real document providers and Android devices is still required; CI only verifies compilation and automated unit tests.

## Request codes

- `7602`: one-shot JSON document import.
- `7603`: user-selected JSON document export.
