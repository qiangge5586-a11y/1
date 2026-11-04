package cn.autohack.rebuild;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Source-derived Civic meter fields populated only with explicit demo values. */
public final class CivicMeterDemoData {
    private final Map<String, String> values;

    private CivicMeterDemoData(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    public static CivicMeterDemoData forSpeed(int speedKph) {
        return forSpeed(speedKph, MeterUnitSystem.METRIC);
    }

    public static CivicMeterDemoData forSpeed(int speedKph, MeterUnitSystem units) {
        MeterUnitSystem system = units == null ? MeterUnitSystem.METRIC : units;
        int speed = Math.max(0, Math.min(240, speedKph));
        boolean miles = system.usesMiles();
        double distanceFactor = miles ? 0.621371 : 1.0;
        double speedValue = speed * distanceFactor;
        double dte = 420 * distanceFactor;
        double driveDistance = 12.4 * distanceFactor;
        double tripA = 125.6 * distanceFactor;
        double routeRemain = 2.8 * distanceFactor;
        double speedLimit = 60 * distanceFactor;
        double coolant = miles ? (82 * 9.0 / 5.0) + 32 : 82;
        double average = fuelEfficiency(7.2, system);
        double averageA = fuelEfficiency(6.8, system);

        Map<String, String> v = new HashMap<>();
        v.put("speed", format0(speedValue));
        v.put("rpm", String.valueOf(850 + speed * 32));
        v.put("fuel", "68");
        v.put("coolant", format0(coolant));
        v.put("distance_to_empty", format1(dte));
        v.put("current_distance", format1(driveDistance));
        v.put("avg_fuel_eff", format1(average));
        v.put("trip_a_distance", format1(tripA));
        v.put("avg_fuel_eff_a", format1(averageA));
        v.put("compass", "NE");
        v.put("next_road", "Main Street");
        v.put("cur_road", "Market Road");
        v.put("route_remain", format1(routeRemain) + " " + system.distanceUnit());
        v.put("seg_dist", miles ? "0.2 mi" : "300 m");
        v.put("speed_limit", format0(speedLimit));
        v.put("camera_dist", miles ? "0.3 mi" : "500 m");
        v.put("distance_unit", system.distanceUnit());
        v.put("fuel_eff_unit", system.fuelEfficiencyUnit());
        v.put("coolant_unit", system.coolantUnit());
        v.put("speed_unit", system.speedUnit());
        return new CivicMeterDemoData(v);
    }

    private static double fuelEfficiency(double litersPer100Km, MeterUnitSystem system) {
        switch (system) {
            case KM_PER_LITER: return 100.0 / litersPer100Km;
            case US_CUSTOMARY: return 235.214583 / litersPer100Km;
            case IMPERIAL: return 282.480936 / litersPer100Km;
            default: return litersPer100Km;
        }
    }

    private static String format0(double value) {
        return String.format(Locale.US, "%.0f", value);
    }

    private static String format1(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    public Map<String, String> asTokenMap() {
        return values;
    }
}
