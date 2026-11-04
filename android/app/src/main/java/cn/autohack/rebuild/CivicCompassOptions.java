package cn.autohack.rebuild;

import java.util.ArrayList;
import java.util.List;

/** Display-only compass preferences matching the original Civic meter options. */
public final class CivicCompassOptions {
    private CivicCompassOptions() {}

    /** In the source-derived Civic preset, the compass is widget index 3. */
    public static List<MeterPreset.ViewSpec> apply(
            List<MeterPreset.ViewSpec> source, boolean enabled) {
        List<MeterPreset.ViewSpec> result = new ArrayList<>();
        if (source == null) return result;
        for (int i = 0; i < source.size(); i++) {
            if (!enabled && i == 3) continue;
            result.add(source.get(i));
        }
        return result;
    }

    /** Track-up is labelled as a simulation because no heading sensor/map is connected. */
    public static String compassLabel(String heading, boolean trackUp) {
        String value = heading == null || heading.trim().isEmpty() ? "--" : heading;
        return trackUp ? "↑ " + value : value;
    }

    public static String modeDescription(boolean compassEnabled, boolean trackUp) {
        return modeDescription(compassEnabled, trackUp, false);
    }

    public static String modeDescription(boolean compassEnabled, boolean trackUp,
            boolean gpsCourseAvailable) {
        if (!compassEnabled) return "Compass disabled";
        if (trackUp && gpsCourseAvailable) return "Track-up · using GPS direction of travel";
        if (trackUp) return "Track-up fallback · waiting for a stable GPS course";
        return "North-up · device compass heading when available";
    }
}
