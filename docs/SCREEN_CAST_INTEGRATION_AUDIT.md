# Original screen-casting integration audit

## Original preference and warning

The original `pref_meter.xml` exposes:
- `enable_screen_cast` (off by default).
- `screen_cast_quality`, values 5, 8, 12 and 15, labels Low, Middle, High and Highest; default value 8.
- The preference warns that screen casting should only be used for navigation, not video playback while driving.

The source maps the quality integer to the frame-loop delay: `Thread.sleep(1000 / quality)`, i.e. nominal capture-loop targets of 5, 8, 12 or 15 frames per second.

## Confirmed runtime path

- `C0170b` hooks into other processes through Xposed and registers private HondaHack broadcasts.
- Its screen-capture helper binds explicitly to `com.android.systemui/.screenshot.TakeScreenshotService`.
- `Be.onServiceConnected` wraps the SystemUI binder in a `Messenger` and creates a reply handler.
- Message 100 initializes a `ParcelFileDescriptor`/file stream and allocates a 400×240 ARGB_8888 bitmap.
- Message 101 reads 384,000 bytes from the shared descriptor into the bitmap and forwards each frame to a callback.
- Message 102 closes the stream, recycles the bitmap and closes the descriptor.
- `Ae.run` sends message 100, then repeatedly sends message 101 at the configured quality rate while the relevant input source/state and custom-meter/screen-cast flags allow it. It broadcasts private `START_CAST` / `STOP_CAST` actions around the stream.
- The toggle path also checks the modified sample's activation stub and forces the custom meter on.

## Rebuild decision

This is not the public Android MediaProjection flow. It depends on a private SystemUI screenshot service binder, Xposed hooks in host processes, internal source-state values and app-private broadcasts. A normal third-party app cannot assume those interfaces exist or are bindable on the 2019 Civic factory display.

The rebuild therefore does not bind to SystemUI's screenshot service, inject Xposed hooks, read host-process frame buffers or send private OEM/HondaHack broadcasts. A future independent screen-share adapter would need to use the public MediaProjection consent flow and a documented, authorized receiver on the target head unit. MediaProjection consent alone would capture a device screen; it would not prove that the Civic OEM display can receive or render the stream.

## Status

Source-level path and frame pacing have been reviewed. No screen-cast implementation or Civic compatibility claim is made. The feature remains blocked on a documented target receiver/API and bench validation.
