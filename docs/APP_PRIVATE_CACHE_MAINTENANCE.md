# App-private cache maintenance

## Behavior

- Displays the size of files inside this app's private cache directory.
- Allows a user to refresh the size report.
- Cache deletion requires an explicit confirmation dialog.
- Deletes only children of this app's `getCacheDir()`; the cache root itself is retained.

## Safety boundaries

- Canonical-path checks prevent traversal outside the app cache directory.
- Does not delete app preferences, named meter presets, app files, user-selected source documents, shared storage, downloads, other apps' data or system files.
- Temporary files used for meter-preview sharing are cache files and may be removed; previously shared temporary links can stop working.
