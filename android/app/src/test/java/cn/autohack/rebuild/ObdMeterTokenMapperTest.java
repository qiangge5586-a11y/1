package cn.autohack.rebuild;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class ObdMeterTokenMapperTest {
    @Test public void overlaysLiveMetricValuesAndHidesUnsupportedDemoFields() {
        Map<String, String> demo = new HashMap<>();
        demo.put("distance_to_empty", "420");
        demo.put("next_road", "Main Street");
        demo.put("compass", "NE");
        Map<String, String> live = ObdMeterTokenMapper.overlay(
                demo, 100, 2500, 60, 90, MeterUnitSystem.METRIC);
        assertEquals("100", live.get("speed"));
        assertEquals("2500", live.get("rpm"));
        assertEquals("60", live.get("fuel"));
        assertEquals("90", live.get("coolant"));
        assertEquals("--", live.get("distance_to_empty"));
        assertEquals("--", live.get("next_road"));
        assertEquals("NE", live.get("compass"));
        assertEquals("km/h", live.get("speed_unit"));
        assertEquals(" °C", live.get("coolant_unit"));
    }

    @Test public void convertsSpeedAndCoolantForUsUnits() {
        Map<String, String> live = ObdMeterTokenMapper.overlay(
                null, 100, 2000, 50, 100, MeterUnitSystem.US_CUSTOMARY);
        assertEquals("62", live.get("speed"));
        assertEquals("212", live.get("coolant"));
        assertEquals("mph", live.get("speed_unit"));
        assertEquals(" °F", live.get("coolant_unit"));
    }

    @Test public void missingLiveValuesStayUnavailableInsteadOfFallingBackToDemo() {
        Map<String, String> demo = new HashMap<>();
        demo.put("speed", "88");
        demo.put("rpm", "1200");
        demo.put("fuel", "70");
        demo.put("coolant", "85");
        Map<String, String> live = ObdMeterTokenMapper.overlay(
                demo, null, null, null, null, MeterUnitSystem.METRIC);
        assertEquals("--", live.get("speed"));
        assertEquals("--", live.get("rpm"));
        assertEquals("--", live.get("fuel"));
        assertEquals("--", live.get("coolant"));
    }

    @Test public void doesNotMutateTheDemoMap() {
        Map<String, String> demo = new HashMap<>();
        demo.put("speed", "88");
        Map<String, String> live = ObdMeterTokenMapper.overlay(
                demo, 42, 1000, 80, 75, MeterUnitSystem.METRIC);
        assertEquals("88", demo.get("speed"));
        assertEquals("42", live.get("speed"));
    }
}
