# Original startup automation audit

## App selection and startup launch

The original `StartupActivity` lets the user select:
- None.
- The system launcher.
- A screen saver.
- An installed non-system app.

`MyService.a()` waits for the configured delay, then launches the selected target. The optional play-command path waits again and executes `/system/bin/input keyevent 126` (media play/pause) through the privileged shell helper.

## Agreement auto-confirm

- `auto_confirm_agreement` and a 0–10 second delay are exposed in the original preference screen.
- The Xposed hook path invokes the app's agreement-dialog handling callback after hooking the target process.
- The feature is designed to automatically click an OEM agreement/OK button on startup. This can accept legal terms or dismiss a safety notice without a user reviewing it.

The rebuild will not automatically accept agreements, click dialogs or bypass OEM notices.

## Restore last function

- The original preference `restore_last_function` is connected to an Xposed hook in a host process.
- The hook changes the result of an OEM function-restoration method, suppressing or allowing restoration depending on the setting.
- This is host-process behavior and is not a public Android API.

The rebuild's local “open meter editor on launch” setting only controls its own Activity landing position. It does not modify the vehicle head unit's last-used function.

## Rebuild boundary

- Phase 48 adds an explicit user-app picker and manual launch button.
- No boot receiver, automatic third-party launch, delayed media key injection, shell `input keyevent`, agreement auto-clicking, host-process hooks or OEM last-function modification are included.
- These behaviors require privileged access and exact head-unit integration evidence; the supplied 2019 Civic factory screen is not assumed to expose them.
