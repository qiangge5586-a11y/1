# Original voice assistant integration audit

## Original recognition pipeline

- `cn.autohack.voiceassistant.o` is the voice-assistant coordinator and BroadcastReceiver/ServiceConnection.
- The original flow records audio to `/sdcard/HondaHack/recording.mp3`, then sends the recording to a recognition task.
- `cn.autohack.voiceassistant.s` constructs an HTTP form body containing `data` and `EngSerViceType` (default `16k_zh`) and parses a JSON result/error response. This is an online speech-recognition path, not Android's on-device recognizer.
- The original service also requests audio focus and plays bundled response clips.

## Original command families observed

- App launch: phrases such as “打开/运行 …” are resolved against installed apps and then launched.
- Navigation: destination phrases, “回家”, route overview, main/side road selection, stop navigation and POI selection are sent to HondaHack helper methods and Amap's private `AUTONAVI_STANDARD_BROADCAST_SEND` interface.
- Music: “播放 …” is routed to QQ Music Car when installed.
- Conversation: greeting, farewell, questions and other phrases select bundled audio responses.
- Recognition and command execution are coordinated through a background service, timers and broadcasts.

## Rebuild behavior and boundary

The rebuild's current prototype uses Android 12+ `SpeechRecognizer.createOnDeviceSpeechRecognizer`, requires microphone permission after a user tap, refuses cloud fallback, and stops when the Activity leaves the foreground.

It currently displays recognized text only. It does not automatically execute speech as a vehicle command.

The rebuild will not copy the original cloud audio-upload endpoint, record to shared storage, private Amap broadcasts, QQ Music internal commands, bundled copyrighted voice clips or background/wake-word service. A safe local command subset may be added only with explicit user confirmation before actions.
