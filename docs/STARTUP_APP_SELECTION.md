# Startup app selection and explicit launch

## Original APK behavior observed

The original startup preference screen can select:
- None.
- Default launcher.
- Screen saver.
- Installed non-system applications.

It also includes automatic launch delay and an optional media play command. These settings are tied to the original boot/startup automation and head-unit environment.

## Rebuild behavior

- Lists installed user apps that have a public launcher activity.
- Excludes system apps and the rebuild app itself.
- Saves the selected package name locally.
- Launches only after the user taps the explicit Launch button.
- Does not add a boot receiver, auto-start delay, background launch, force-stop, play command, private app access or system-level behavior.

## Compatibility

Package visibility and launch activities depend on Android version and device policy. The list only indicates that Android exposes a launch intent; it does not prove compatibility with the Civic factory display.
