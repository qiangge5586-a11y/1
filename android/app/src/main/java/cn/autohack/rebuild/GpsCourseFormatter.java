package cn.autohack.rebuild;

/** Rules for using GPS course as a track-up heading only when movement is meaningful. */
public final class GpsCourseFormatter {
    public static final float MIN_COURSE_SPEED_KMH = 5.0f;

    private GpsCourseFormatter() {}

    public static boolean shouldUseCourse(boolean hasBearing, boolean hasSpeed, float speedKmh) {
        return hasBearing && hasSpeed && !Float.isNaN(speedKmh)
                && !Float.isInfinite(speedKmh) && speedKmh >= MIN_COURSE_SPEED_KMH;
    }

    public static String formatSpeed(boolean hasSpeed, float speedKmh) {
        if (!hasSpeed || Float.isNaN(speedKmh) || Float.isInfinite(speedKmh) || speedKmh < 0f) {
            return "-- km/h";
        }
        return Math.round(speedKmh) + " km/h";
    }
}
