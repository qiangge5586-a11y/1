package cn.autohack.rebuild;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class CivicCompassOptionsTest {
    private List<MeterPreset.ViewSpec> sample() {
        List<MeterPreset.ViewSpec> views = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            views.add(new MeterPreset.ViewSpec(i, 0, 8, 12,
                    "widget-" + i, "center", 12, "#ffffffff"));
        }
        return views;
    }

    @Test public void compassCanBeHiddenWithoutChangingOtherWidgets() {
        List<MeterPreset.ViewSpec> result = CivicCompassOptions.apply(sample(), false);
        assertEquals(11, result.size());
        assertEquals("widget-2", result.get(2).template);
        assertEquals("widget-4", result.get(3).template);
    }

    @Test public void compassRemainsVisibleWhenEnabled() {
        assertEquals(12, CivicCompassOptions.apply(sample(), true).size());
    }

    @Test public void trackUpLabelIsExplicitlyMarkedAsDemoOrientation() {
        assertEquals("NE", CivicCompassOptions.compassLabel("NE", false));
        assertEquals("↑ NE", CivicCompassOptions.compassLabel("NE", true));
        assertEquals("↑ --", CivicCompassOptions.compassLabel(null, true));
    }

    @Test public void modeDescriptionDoesNotClaimLiveSensorIntegration() {
        assertEquals("Compass disabled", CivicCompassOptions.modeDescription(false, false));
        assertTrue(CivicCompassOptions.modeDescription(true, true).contains("simulated"));
        assertTrue(CivicCompassOptions.modeDescription(true, false).contains("simulated"));
    }
}
