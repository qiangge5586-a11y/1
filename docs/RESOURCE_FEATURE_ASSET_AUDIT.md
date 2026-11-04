# Feature Resource Asset Audit

Date: 2026-11-04

## Meter UI configuration

The APK contains two small JSON resource files suitable for documenting the visible meter configuration model:

### `res/raw/meter_view.json`

- Preset name: `经典`
- Contains a `views` array.
- View fields include normalized layout bounds (`left`, `top`, `width`, `height`), background color, text size/color, text template, alignment, stroke and shadow settings.
- The sample text template is `{speed}`.
- This supports a future safe, offline meter-layout editor with mock speed values. It does not reveal the source of live vehicle speed data.

### `res/raw/speed_meters.json`

- Contains two speedometer style records.
- Fields include background image, text size/color, stroke color/size and shadow color/size.
- Uses resource image identifiers such as `speed1` and `speed2`.

## Voice and navigation media

The APK contains many audio resources for voice-assistant prompts, navigation prompts, camera alerts and speed-limit warnings, including Simplified Chinese localized resources under `res/raw-zh-rCN/`. These indicate feature coverage but do not establish the exact trigger logic or permission model. A clean rebuild should use original/licensed or newly recorded audio, not redistribute these embedded recordings without rights clearance.

## Vendor and privileged integration resources

The resource tree contains files whose names indicate Xposed/bootstrap and privileged system modification paths, including:

- Xposed bridge and signature-hook binary resources.
- ARM and x86 app_process binary payloads.
- platform permission XML.
- Mitsubishi and Clarion whitelist XML files.
- vendor-specific installer and recovery shell scripts.

These are recorded as forensic evidence only. Their contents are not reproduced here, and none are to be executed, installed, or copied into the clean rebuild. They may replace system components or modify privileged platform configuration and therefore carry a high risk of boot failure, privilege escalation, or device lockout.

## Safe implementation decisions

1. Implement meter layout editing from the JSON schema using mock values first.
2. Separate visual meter rendering from any vehicle data source.
3. Use a documented, user-authorized interface before connecting live data.
4. Do not bundle or execute original system scripts, app_process payloads, Xposed bridge files, signature-hook payloads, or privileged permission XML.
5. Re-record or license audio before shipping any voice/navigation sound assets.

## Limitation

This is a ZIP/resource inventory and JSON schema inspection. It does not prove which resources are actively used by the original app at runtime.
