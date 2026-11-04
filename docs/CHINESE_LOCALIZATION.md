# Simplified Chinese APK localization

The application UI now uses a centralized Simplified-Chinese phrase layer and localized TextView/Button/CheckBox widgets. The launcher label is **本田黑科技**. MainActivity screens translate static and dynamic app-authored labels, status messages, voice feedback, OBD results, dialog copy, preset action menus and Toast messages.

User-entered preset names and destinations are preserved rather than machine-translated. Protocol identifiers, OBD PID values, JSON keys, package names and technical units remain unchanged where translation would break interoperability.

This is a Chinese UI localization pass, not a claim that every third-party Android system dialog, third-party app, ECU response string or imported user-supplied text is translated. Final APK validation is performed by CI; screenshots and a physical-device language review remain outstanding.
