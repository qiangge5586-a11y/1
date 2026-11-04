package cn.autohack.rebuild;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Decodes original Civic meter integer fields into display strings.
 * This is a pure transformation; it never accesses vehicle IPC, privileged files,
 * broadcasts, CAN, OBD or ECU services.
 */
public final class CivicTelemetryDecoder {
    public static final int MISSING = Integer.MIN_VALUE;

    private CivicTelemetryDecoder() {}

    public static Map<String, String> decode(
            int speed, int distanceToEmpty, int tripDistanceRaw, int tripDistanceUnit,
            int averageFuelRaw, int averageFuelUnit, int tripAAverageFuelRaw,
            int tripAAverageFuelUnit, int coolantRaw, int coolantUnit) {
        Map<String, String> result = new LinkedHashMap<>();
        String distanceUnit = tripDistanceUnit < 0 ? "unknown" : distanceUnit(tripDistanceUnit);
        result.put("speed", speed < 0 ? "--" : Integer.toString(speed));
        result.put("distance_to_empty", distanceToEmpty < 0 || tripDistanceUnit < 0
                ? "---" : distanceToEmpty + " " + distanceUnit);
        result.put("trip_a_distance", tripDistanceUnit < 0 ? "--.-"
                : scaledTenth(tripDistanceRaw, distanceUnit, true));
        result.put("avg_fuel_eff", averageFuelUnit < 0 ? "--.-"
                : scaledTenth(averageFuelRaw, fuelUnit(averageFuelUnit), false));
        result.put("avg_fuel_eff_a", tripAAverageFuelUnit < 0 ? "--.-"
                : scaledTenth(tripAAverageFuelRaw, fuelUnit(tripAAverageFuelUnit), false));
        result.put("coolant", coolant(coolantRaw, coolantUnit));
        result.put("distance_unit", distanceUnit);
        result.put("avg_fuel_eff_unit", averageFuelUnit < 0 ? "unknown" : fuelUnit(averageFuelUnit));
        result.put("avg_fuel_eff_a_unit", tripAAverageFuelUnit < 0
                ? "unknown" : fuelUnit(tripAAverageFuelUnit));
        result.put("coolant_unit", coolantUnit < 0 ? "unknown" : coolantUnit == 0 ? "°C" : "°F");
        return Collections.unmodifiableMap(result);
    }

    public static String distanceUnit(int code) {
        return code == 0 ? "km" : "mi";
    }

    public static String fuelUnit(int code) {
        switch (code) {
            case 0: return "km/L";
            case 1: return "mpg";
            case 2: return "imperial mpg";
            default: return "L/100km";
        }
    }

    private static String scaledTenth(int raw, String unit, boolean truncateLargeDistance) {
        if (raw < 0) return "--.-";
        double value = raw / 10.0;
        String formatted = truncateLargeDistance && raw > 10000
                ? Integer.toString(raw / 10)
                : String.format(Locale.US, "%.1f", value);
        return formatted + " " + unit;
    }

    private static String coolant(int raw, int unit) {
        if (raw == MISSING || unit < 0) return "---";
        int celsius = raw - 40;
        if (unit == 0) return celsius + " °C";
        int fahrenheit = Math.round((celsius * 9.0f / 5.0f) + 32.0f);
        return fahrenheit + " °F";
    }
}
