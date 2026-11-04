package cn.autohack.rebuild;

/** Bounded settings for a user-triggered local startup-video preview. */
public final class StartupVideoPreviewOptions {
    public final boolean audioEnabled;
    public final boolean fadeOut;
    public final int delaySeconds;
    public final int maxSeconds;

    public StartupVideoPreviewOptions(boolean audioEnabled, boolean fadeOut,
            int delaySeconds, int maxSeconds) {
        this.audioEnabled = audioEnabled;
        this.fadeOut = fadeOut;
        this.delaySeconds = clamp(delaySeconds, 0, 30);
        this.maxSeconds = clamp(maxSeconds, 5, 60);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
