package cn.autohack.rebuild;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Source-derived Civic meter fields populated only with explicit demo values. */
public final class CivicMeterDemoData {
    private final Map<String, String> values;

    private CivicMeterDemoData(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    public static CivicMeterDemoData forSpeed(int speedKph) {
        int speed = Math.max(0, Math.min(240, speedKph));
        Map<String, String> v = new HashMap<>();
        v.put("speed", String.valueOf(speed));
        v.put("rpm", String.valueOf(850 + speed * 32));
        v.put("fuel", "68");
        v.put("coolant", "82");
        v.put("distance_to_empty", "420");
        v.put("current_distance", "12.4");
        v.put("avg_fuel_eff", "7.2");
        v.put("trip_a_distance", "125.6");
        v.put("avg_fuel_eff_a", "6.8");
        v.put("compass", "NE");
        v.put("next_road", "Main Street");
        v.put("cur_road", "Market Road");
        v.put("route_remain", "2.8 km");
        v.put("seg_dist", "300 m");
        v.put("speed_limit", "60");
        v.put("camera_dist", "500 m");
        return new CivicMeterDemoData(v);
    }

    public Map<String, String> asTokenMap() {
        return values;
    }
}
