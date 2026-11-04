# Phase 17 — Named meter preset management

Implemented in the independent Android rebuild.

## User-facing controls
- Save the current meter layout under a user-supplied name.
- List and load named layouts.
- Rename, duplicate and delete saved layouts.
- Export a selected layout as JSON through Android's share sheet.
- Import a JSON preset by pasting its contents into the import dialog.

## Data and validation
- Presets are stored locally in app-private SharedPreferences; no cloud upload is performed.
- `PresetLibrary` validates imported layouts through `MeterPreset.fromJson` before storing them.
- Names are trimmed and constrained to 1–40 characters.
- Duplicate names are rejected for rename/duplicate operations; saving/importing an existing name replaces that entry.
- The supported JSON schema remains `name` plus `views[]`, with normalized 0–100 bounds and supported telemetry placeholders.

## Tests
`PresetLibraryTest` covers save/rename/duplicate/delete, export validation, invalid imports and duplicate-name rejection.

## Limitations
- Import is currently a paste-JSON workflow rather than a document-picker flow.
- Export uses Android's share sheet with JSON text; the receiving app determines how it is saved.
- Presets remain local to this app installation unless explicitly exported by the user.
- This phase does not establish vehicle telemetry or factory head-unit compatibility.
