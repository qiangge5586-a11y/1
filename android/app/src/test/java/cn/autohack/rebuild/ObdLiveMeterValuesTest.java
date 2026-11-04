package cn.autohack.rebuild;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class ObdLiveMeterValuesTest {
    private Map<String, String> demo() {
        Map<String, String> values = new HashMap<>();
        values.put("speed", "0");
        values.put("rpm", "850");
        values.put("fuel", "68");
        values.put("coolant", "82");
        values.put("next_road", "Market Road");
        return values;
    }

    @Test public void disconnectedModePreservesDemoValues() {
        Map<String, String> result = ObdLiveMeterValues.overlay(
                demo(), false, 80, 2500, 90, 60, MeterUnitSystem.METRIC);
        assertEquals("0", result.get("speed"));
        assertEquals("850", result.get("rpm"));
        assertEquals("Market Road", result.get("next_road"));
    }

    @Test public void metricLiveValuesOverrideOnlyTheFourObdFields() {
        Map<String, String> base = demo();
        Map<String, String> result = ObdLiveMeterValues.overlay(
                base, true, 80, 2500, 90, 60, MeterUnitSystem.METRIC);
        assertEquals("80", result.get("speed"));
        assertEquals("2500", result.get("rpm"));
        assertEquals("60", result.get("fuel"));
        assertEquals("90", result.get("coolant"));
        assertEquals("--", result.get("next_road"));
        assertEquals("--", result.get("distance_to_empty"));
        assertEquals("--", result.get("avg_fuel_eff"));
        assertEquals("0", base.get("speed"));
    }

    @Test public void usUnitsConvertSpeedAndCoolant() {
        Map<String, String> result = ObdLiveMeterValues.overlay(
                demo(), true, 80, 2500, 90, 60, MeterUnitSystem.US_CUSTOMARY);
        assertEquals("50", result.get("speed"));
        assertEquals("194", result.get("coolant"));
    }

    @Test public void unavailableLivePidsDisplayPlaceholdersNotDemoValues() {
        Map<String, String> result = ObdLiveMeterValues.overlay(
                demo(), true, null, null, null, null, MeterUnitSystem.METRIC);
        assertEquals("--", result.get("speed"));
        assertEquals("--", result.get("rpm"));
        assertEquals("--", result.get("fuel"));
        assertEquals("--", result.get("coolant"));
    }
}
