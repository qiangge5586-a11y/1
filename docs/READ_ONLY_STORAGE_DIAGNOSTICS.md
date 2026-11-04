# Read-only storage diagnostics

## Reported values

- Android API level.
- Shared-storage state as reported by Android.
- App-private files directory and usable space.
- App cache usable space.
- App-specific external files directory and usable space, when available.
- Whether external storage is emulated.

The report can be refreshed or shared through Android's chooser.

## Safety boundaries

- Uses app-private and app-specific external storage paths only.
- Does not request `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE`.
- Does not enumerate arbitrary shared-storage files or attempt to repair permissions.
- Does not remount or write system partitions, modify `platform.xml`, kill apps, or delete user files.
- Values are diagnostic snapshots; they do not prove that the head unit grants access to arbitrary SD-card paths.
