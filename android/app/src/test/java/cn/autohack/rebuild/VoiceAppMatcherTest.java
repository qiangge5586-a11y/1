package cn.autohack.rebuild;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public final class VoiceAppMatcherTest {
    private StartupAppLauncher.Entry app(String label, String packageName) {
        return new StartupAppLauncher.Entry(label, packageName);
    }

    @Test public void prefersOneExactNameOverPartialCandidates() {
        VoiceAppMatcher.Result result = VoiceAppMatcher.resolve(Arrays.asList(
                app("高德地图车机版", "com.autonavi.amapauto"),
                app("高德地图", "com.autonavi.minimap"),
                app("百度地图", "com.baidu.BaiduMap")), "高德地图");
        assertEquals(VoiceAppMatcher.Status.UNIQUE, result.status);
        assertEquals("com.autonavi.minimap", result.entry.packageName);
    }

    @Test public void acceptsOneUnambiguousPartialName() {
        VoiceAppMatcher.Result result = VoiceAppMatcher.resolve(Arrays.asList(
                app("高德地图车机版", "com.autonavi.amapauto"),
                app("百度地图", "com.baidu.BaiduMap")), "高德");
        assertEquals(VoiceAppMatcher.Status.UNIQUE, result.status);
        assertEquals("com.autonavi.amapauto", result.entry.packageName);
    }

    @Test public void rejectsAmbiguousPartialAndExactNames() {
        assertEquals(VoiceAppMatcher.Status.AMBIGUOUS,
                VoiceAppMatcher.resolve(Arrays.asList(
                        app("高德地图", "com.autonavi.auto"),
                        app("高德地图车机版", "com.autonavi.car")), "高德").status);
        assertEquals(VoiceAppMatcher.Status.AMBIGUOUS,
                VoiceAppMatcher.resolve(Arrays.asList(
                        app("导航", "com.example.nav1"),
                        app("导航", "com.example.nav2")), "导航").status);
    }

    @Test public void rejectsEmptyTooShortAndMissingTargets() {
        assertEquals(VoiceAppMatcher.Status.NO_MATCH,
                VoiceAppMatcher.resolve(Collections.<StartupAppLauncher.Entry>emptyList(), "高德").status);
        assertEquals(VoiceAppMatcher.Status.NO_MATCH,
                VoiceAppMatcher.resolve(Arrays.asList(app("高德地图", "com.autonavi.auto")), "高").status);
        assertEquals(VoiceAppMatcher.Status.NO_MATCH,
                VoiceAppMatcher.resolve(Arrays.asList(app("高德地图", "com.autonavi.auto")), "").status);
    }

    @Test public void matchesPackageNamesWithoutCaseSensitivityOrWhitespace() {
        VoiceAppMatcher.Result result = VoiceAppMatcher.resolve(Arrays.asList(
                app("导航程序", "Com.Example.Navigation")), " com.example.navigation ");
        assertEquals(VoiceAppMatcher.Status.UNIQUE, result.status);
        assertEquals("Com.Example.Navigation", result.entry.packageName);
    }
}
