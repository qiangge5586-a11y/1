package cn.autohack.rebuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Applies the original Civic meter's six selectable vehicle-information fields. */
public final class CivicVehicleInfoLayout {
    public static final int FIELD_COUNT = 6;
    public static final String[] KEYS = {
            "distance_to_empty", "current_distance", "avg_fuel_eff",
            "trip_a_distance", "avg_fuel_eff_a", "coolant_temp"
    };
    public static final String[] LABELS = {
            "Distance to empty", "Current drive distance", "Average fuel efficiency",
            "Trip A distance", "Trip A average fuel efficiency", "Engine coolant temperature"
    };

    private CivicVehicleInfoLayout() {}

    public static boolean[] defaultSelection() {
        return new boolean[]{true, true, true, false, false, false};
    }

    /**
     * The Civic template stores its six vehicle fields as the final six views.
     * The selected fields are reflowed evenly across the bottom information row.
     */
    public static List<MeterPreset.ViewSpec> apply(
            List<MeterPreset.ViewSpec> source, boolean[] selected) {
        if (source == null || source.size() < FIELD_COUNT) {
            return source == null ? Collections.emptyList() : new ArrayList<>(source);
        }
        int prefixCount = source.size() - FIELD_COUNT;
        List<MeterPreset.ViewSpec> result = new ArrayList<>();
        for (int i = 0; i < prefixCount; i++) result.add(source.get(i));

        boolean[] choices = selected == null ? defaultSelection() : selected;
        int visibleCount = 0;
        for (int i = 0; i < FIELD_COUNT; i++) {
            if (i < choices.length && choices[i]) visibleCount++;
        }
        if (visibleCount == 0) return result;

        int slot = 0;
        for (int i = 0; i < FIELD_COUNT; i++) {
            if (i >= choices.length || !choices[i]) continue;
            MeterPreset.ViewSpec original = source.get(prefixCount + i);
            int left = 1 + (slot * 98 / visibleCount);
            int right = 1 + ((slot + 1) * 98 / visibleCount);
            int width = Math.max(1, right - left - 1);
            result.add(new MeterPreset.ViewSpec(left, original.top, width,
                    original.height, original.template, original.alignment,
                    original.textSize, original.textColor));
            slot++;
        }
        return result;
    }
}
