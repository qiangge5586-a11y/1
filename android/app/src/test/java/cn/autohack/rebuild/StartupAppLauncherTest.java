package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StartupAppLauncherTest {
    @Test public void onlyUserInstalledAppsWithLaunchIntentsAreEligible() {
        assertTrue(StartupAppLauncher.isEligible(false, false, true));
        assertFalse(StartupAppLauncher.isEligible(true, false, true));
        assertFalse(StartupAppLauncher.isEligible(false, true, true));
        assertFalse(StartupAppLauncher.isEligible(false, false, false));
    }
}
