package cn.autohack.rebuild;

/** Converts an azimuth in degrees into one of eight compass labels. */
public final class CompassHeading {
    private static final String[] DIRECTIONS =
            {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private CompassHeading() {}

    public static String fromAzimuth(float degrees) {
        if (Float.isNaN(degrees) || Float.isInfinite(degrees)) return "--";
        float normalized = ((degrees % 360.0f) + 360.0f) % 360.0f;
        int index = ((int) Math.floor((normalized + 22.5f) / 45.0f)) % DIRECTIONS.length;
        return DIRECTIONS[index];
    }
}
