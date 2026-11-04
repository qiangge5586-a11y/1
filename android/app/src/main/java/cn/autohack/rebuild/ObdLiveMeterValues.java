package cn.autohack.rebuild;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Overlays live read-only OBD values on the meter's separate demo token map. */
public final class ObdLiveMeterValues {
    private ObdLiveMeterValues() {}

    public static Map<String, String> overlay(Map<String, String> demoValues,
            boolean connected, Integer speedKph, Integer engineRpm,
            Integer coolantTempC, Integer fuelPercent, MeterUnitSystem units) {
        Map<String, String> result = new HashMap<>();
        if (demoValues != null) result.putAll(demoValues);
        if (!connected) return Collections.unmodifiableMap(result);

        MeterUnitSystem system = units == null ? MeterUnitSystem.METRIC : units;
        result.put("speed", speedKph == null ? "--"
                : format(system.usesMiles() ? speedKph * 0.621371 : speedKph));
        result.put("rpm", engineRpm == null ? "--" : Integer.toString(engineRpm));
        result.put("fuel", fuelPercent == null ? "--" : Integer.toString(fuelPercent));
        if (coolantTempC == null) {
            result.put("coolant", "--");
        } else {
            double temperature = system.usesMiles()
                    ? coolantTempC * 9.0 / 5.0 + 32.0 : coolantTempC;
            result.put("coolant", format(temperature));
        }

        String[] unavailable = {
                "distance_to_empty", "current_distance", "avg_fuel_eff",
                "trip_a_distance", "avg_fuel_eff_a", "next_road", "cur_road",
                "route_remain", "seg_dist", "speed_limit", "camera_dist"
        };
        for (String token : unavailable) result.put(token, "--");
        return Collections.unmodifiableMap(result);
    }

    private static String format(double value) {
        return Long.toString(Math.round(value));
    }
}
