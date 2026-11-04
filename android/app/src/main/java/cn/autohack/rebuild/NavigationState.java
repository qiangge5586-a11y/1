package cn.autohack.rebuild;

/** Immutable navigation snapshot; values are descriptive only and never drive the vehicle. */
public final class NavigationState {
    public final String source;
    public final String destination;
    public final String currentRoad;
    public final String maneuver;
    public final int distanceMeters;
    public final int etaMinutes;
    public final boolean active;

    public NavigationState(String source, String destination, String currentRoad,
            String maneuver, int distanceMeters, int etaMinutes, boolean active) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException("source is required");
        if (destination == null || currentRoad == null || maneuver == null)
            throw new IllegalArgumentException("navigation text fields must not be null");
        if (distanceMeters < 0 || etaMinutes < 0)
            throw new IllegalArgumentException("distance and ETA must be non-negative");
        this.source = source;
        this.destination = destination;
        this.currentRoad = currentRoad;
        this.maneuver = maneuver;
        this.distanceMeters = distanceMeters;
        this.etaMinutes = etaMinutes;
        this.active = active;
    }

    public String toDisplayString() {
        if (!active) return "Navigation: inactive\nSource: " + source;
        return "Navigation: demo route active"
                + "\nDestination: " + destination
                + "\nCurrent road: " + currentRoad
                + "\nNext maneuver: " + maneuver
                + "\nDistance to maneuver: " + distanceMeters + " m"
                + "\nEstimated time: " + etaMinutes + " min"
                + "\nSource: " + source;
    }
}
