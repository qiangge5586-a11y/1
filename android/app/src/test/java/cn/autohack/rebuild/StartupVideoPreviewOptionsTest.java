package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StartupVideoPreviewOptionsTest {
    @Test public void keepsValidOptions() {
        StartupVideoPreviewOptions options =
                new StartupVideoPreviewOptions(false, false, 10, 45);
        assertFalse(options.audioEnabled);
        assertFalse(options.fadeOut);
        assertEquals(10, options.delaySeconds);
        assertEquals(45, options.maxSeconds);
    }

    @Test public void clampsDelayAndDurationToSupportedBounds() {
        StartupVideoPreviewOptions low =
                new StartupVideoPreviewOptions(true, true, -5, 0);
        assertEquals(0, low.delaySeconds);
        assertEquals(5, low.maxSeconds);

        StartupVideoPreviewOptions high =
                new StartupVideoPreviewOptions(true, true, 99, 120);
        assertEquals(30, high.delaySeconds);
        assertEquals(60, high.maxSeconds);
    }
}
