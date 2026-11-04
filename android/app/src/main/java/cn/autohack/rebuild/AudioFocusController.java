package cn.autohack.rebuild;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

/** Explicit, app-scoped audio focus helper. Does not change system volume or play audio. */
public final class AudioFocusController {
    private final AudioManager manager;
    private AudioManager.OnAudioFocusChangeListener listener;
    private Object request;
    private boolean held;

    public AudioFocusController(Context context) {
        manager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean requestTransientFocus(AudioManager.OnAudioFocusChangeListener callback) {
        if (manager == null) return false;
        release();
        listener = callback;
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            android.media.AudioFocusRequest focusRequest = new android.media.AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(listener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build();
            request = focusRequest;
            result = manager.requestAudioFocus(focusRequest);
        } else {
            result = manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return held;
    }

    public void release() {
        if (manager != null && listener != null) {
            if (Build.VERSION.SDK_INT >= 26 && request instanceof android.media.AudioFocusRequest) {
                manager.abandonAudioFocusRequest((android.media.AudioFocusRequest) request);
            } else {
                manager.abandonAudioFocus(listener);
            }
        }
        held = false;
        request = null;
        listener = null;
    }

    public boolean isHeld() { return held; }
}
