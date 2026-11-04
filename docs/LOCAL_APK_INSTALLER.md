# Local APK installation with system confirmation

## Workflow

1. Tap **Choose APK to install**.
2. Select an APK through Android's document picker.
3. On Android 8.0 and later, if this app is not allowed to request package installs, Android opens the app-specific **Install unknown apps** setting. The user must enable it and return to the app.
4. The selected content URI is passed to Android's package installer using the APK MIME type and a read-only URI grant. The URI is temporarily recorded locally during the permission-settings handoff so it can be recovered if the activity is recreated; persistable grants are taken when the document provider offers them.
5. Android displays the package details and installation confirmation. The user can cancel.

## Security boundaries

- Declares `REQUEST_INSTALL_PACKAGES` only for this explicit user-initiated workflow.
- Does not download APKs, enumerate arbitrary storage, install silently, invoke root, use PackageInstaller sessions for unattended installs, or bypass Android's confirmation UI.
- No selected APK is installed automatically on app launch.
- Device/provider behavior must still be manually tested on Android versions and head units in scope.
