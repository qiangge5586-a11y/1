package cn.autohack.rebuild;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public final class CivicTelemetryDecoderTest {
    @Test public void decodesMetricValuesUsingOriginalScaleRules() {
        Map<String, String> values = CivicTelemetryDecoder.decode(
                88, 420, 1256, 0, 72, 3, 68, 0, 122, 0);
        assertEquals("88", values.get("speed"));
        assertEquals("420 km", values.get("distance_to_empty"));
        assertEquals("125.6 km", values.get("trip_a_distance"));
        assertEquals("7.2 L/100km", values.get("avg_fuel_eff"));
        assertEquals("6.8 km/L", values.get("avg_fuel_eff_a"));
        assertEquals("82 °C", values.get("coolant"));
    }

    @Test public void decodesMilesAndFuelEfficiencyCodes() {
        Map<String, String> values = CivicTelemetryDecoder.decode(
                50, 100, 12000, 1, 80, 1, 90, 2, 100, 1);
        assertEquals("100 mi", values.get("distance_to_empty"));
        assertEquals("1200 mi", values.get("trip_a_distance"));
        assertEquals("8.0 mpg", values.get("avg_fuel_eff"));
        assertEquals("9.0 imperial mpg", values.get("avg_fuel_eff_a"));
        assertEquals("140 °F", values.get("coolant"));
    }

    @Test public void preservesOriginalTripDistanceLargeValueFormatting() {
        Map<String, String> values = CivicTelemetryDecoder.decode(
                0, 0, 10001, 0, 10001, 3, 10001, 3, 40, 0);
        assertEquals("1000 km", values.get("trip_a_distance"));
        assertEquals("1000.1 L/100km", values.get("avg_fuel_eff"));
        assertEquals("1000.1 L/100km", values.get("avg_fuel_eff_a"));
    }

    @Test public void missingValuesUseOriginalPlaceholders() {
        Map<String, String> values = CivicTelemetryDecoder.decode(
                -1, -1, -1, 0, -1, 0, -1, 0, CivicTelemetryDecoder.MISSING, 0);
        assertEquals("--", values.get("speed"));
        assertEquals("---", values.get("distance_to_empty"));
        assertEquals("--.-", values.get("trip_a_distance"));
        assertEquals("--.-", values.get("avg_fuel_eff"));
        assertEquals("---", values.get("coolant"));
    }
}
