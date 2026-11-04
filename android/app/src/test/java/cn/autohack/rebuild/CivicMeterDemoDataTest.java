package cn.autohack.rebuild;

import org.json.JSONException;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public final class CivicMeterDemoDataTest {
    @Test public void suppliesExplicitSampleValuesForOriginalCivicFields() {
        Map<String, String> values = CivicMeterDemoData.forSpeed(100).asTokenMap();
        assertEquals("100", values.get("speed"));
        assertEquals("420", values.get("distance_to_empty"));
        assertEquals("12.4", values.get("current_distance"));
        assertEquals("7.2", values.get("avg_fuel_eff"));
        assertEquals("125.6", values.get("trip_a_distance"));
        assertEquals("6.8", values.get("avg_fuel_eff_a"));
        assertEquals("82", values.get("coolant"));
        assertEquals("km", values.get("distance_unit"));
        assertEquals("L/100km", values.get("fuel_eff_unit"));
        assertEquals(" °C", values.get("coolant_unit"));
    }

    @Test public void usCustomaryUnitsConvertSpeedDistanceFuelAndTemperature() {
        Map<String, String> values = CivicMeterDemoData.forSpeed(100, MeterUnitSystem.US_CUSTOMARY).asTokenMap();
        assertEquals("62", values.get("speed"));
        assertEquals("mph", values.get("speed_unit"));
        assertEquals("261.0", values.get("distance_to_empty"));
        assertEquals("mi", values.get("distance_unit"));
        assertEquals("32.7", values.get("avg_fuel_eff"));
        assertEquals("mpg", values.get("fuel_eff_unit"));
        assertEquals("180", values.get("coolant"));
        assertEquals(" °F", values.get("coolant_unit"));
    }

    @Test public void alternativeFuelEfficiencyUnitsAreSupported() {
        assertEquals("13.9", CivicMeterDemoData.forSpeed(0, MeterUnitSystem.KM_PER_LITER)
                .asTokenMap().get("avg_fuel_eff"));
        assertEquals("km/L", CivicMeterDemoData.forSpeed(0, MeterUnitSystem.KM_PER_LITER)
                .asTokenMap().get("fuel_eff_unit"));
        assertEquals("39.2", CivicMeterDemoData.forSpeed(0, MeterUnitSystem.IMPERIAL)
                .asTokenMap().get("avg_fuel_eff"));
        assertEquals("mpg (Imp)", CivicMeterDemoData.forSpeed(0, MeterUnitSystem.IMPERIAL)
                .asTokenMap().get("fuel_eff_unit"));
    }

    @Test public void speedInputIsClampedToDemoRange() {
        assertEquals("240", CivicMeterDemoData.forSpeed(999).asTokenMap().get("speed"));
        assertEquals("0", CivicMeterDemoData.forSpeed(-10).asTokenMap().get("speed"));
    }

    @Test public void presetCanRenderSourceDerivedCivicTokens() throws Exception {
        MeterPreset preset = MeterPreset.fromJson("{\"name\":\"Civic\",\"views\":["
                + "{\"left\":0,\"top\":0,\"width\":100,\"height\":20,"
                + "\"text\":\"DTE {distance_to_empty} {distance_unit} · Avg {avg_fuel_eff} {fuel_eff_unit}\","
                + "\"alignment\":\"center\",\"textSize\":18,\"textColor\":\"#ffffffff\"}]}");
        String rendered = preset.primaryView.renderMock(CivicMeterDemoData.forSpeed(40).asTokenMap());
        assertEquals("DTE 420 km · Avg 7.2 L/100km", rendered);
    }

    @Test(expected = JSONException.class)
    public void unknownTelemetryTokenIsRejected() throws Exception {
        MeterPreset.fromJson("{\"name\":\"Invalid\",\"views\":["
                + "{\"left\":0,\"top\":0,\"width\":100,\"height\":20,"
                + "\"text\":\"{oil_pressure}\",\"alignment\":\"center\"}]}");
    }
}
