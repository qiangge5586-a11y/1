package cn.autohack.rebuild;

/** Display-only unit choices for the offline Civic meter preview. */
public enum MeterUnitSystem {
    METRIC,
    KM_PER_LITER,
    US_CUSTOMARY,
    IMPERIAL;

    public static MeterUnitSystem fromIndex(int index) {
        MeterUnitSystem[] values = values();
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    public boolean usesMiles() {
        return this == US_CUSTOMARY || this == IMPERIAL;
    }

    public String distanceUnit() { return usesMiles() ? "mi" : "km"; }
    public String speedUnit() { return usesMiles() ? "mph" : "km/h"; }
    public String coolantUnit() { return usesMiles() ? " °F" : " °C"; }

    public String fuelEfficiencyUnit() {
        switch (this) {
            case KM_PER_LITER: return "km/L";
            case US_CUSTOMARY: return "mpg";
            case IMPERIAL: return "mpg (Imp)";
            default: return "L/100km";
        }
    }
}
