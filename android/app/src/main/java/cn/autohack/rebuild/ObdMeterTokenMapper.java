package cn.autohack.rebuild;

import java.util.HashMap;
import java.util.Map;

/** Overlays only validated live OBD fields; all non-OBD demo tokens become unavailable. */
public final class ObdMeterTokenMapper {
    private ObdMeterTokenMapper() {}

    public static Map<String, String> overlay(Map<String, String> base,
            Integer speedKph, Integer engineRpm, Integer fuelPercent,
            Integer coolantTempC, MeterUnitSystem units) {
        MeterUnitSystem system = units == null ? MeterUnitSystem.METRIC : units;
        Map<String, String> values = new HashMap<>();
        if (base != null) values.putAll(base);

        values.put("speed", speedKph == null ? "--"
                : format(system.usesMiles() ? Math.round(speedKph * 0.621371f) : speedKph));
        values.put("rpm", engineRpm == null ? "--" : format(engineRpm));
        values.put("fuel", fuelPercent == null ? "--" : format(fuelPercent));
        Integer coolant = coolantTempC == null ? null
                : (system.usesMiles() ? Math.round(coolantTempC * 9.0f / 5.0f + 32.0f)
                        : coolantTempC);
        values.put("coolant", coolant == null ? "--" : format(coolant));

        String[] unavailable = {
                "distance_to_empty", "current_distance", "avg_fuel_eff",
                "trip_a_distance", "avg_fuel_eff_a", "next_road", "cur_road",
                "route_remain", "seg_dist", "speed_limit", "camera_dist"
        };
        for (String token : unavailable) values.put(token, "--");

        values.put("distance_unit", system.distanceUnit());
        values.put("speed_unit", system.speedUnit());
        values.put("coolant_unit", system.coolantUnit());
        values.put("fuel_eff_unit", system.fuelEfficiencyUnit());
        return values;
    }

    private static String format(int value) {
        return String.valueOf(value);
    }
}
