package cn.autohack.rebuild;

/** Fixed sample road information for the offline Civic meter preview. */
public final class RoadInformationDemo {
    private RoadInformationDemo() {}

    public static final String CURRENT_ROAD = "Market Road";
    public static final String NEXT_ROAD = "Main Street";
    public static final String NEXT_MANEUVER = "Turn right";
    public static final String DISTANCE_TO_MANEUVER = "300 metres";

    public static String displayText() {
        return "Current road: " + CURRENT_ROAD
                + "\nNext instruction: " + NEXT_MANEUVER + " onto " + NEXT_ROAD
                + "\nDistance: " + DISTANCE_TO_MANEUVER;
    }

    public static String announcementText() {
        return NEXT_MANEUVER + " onto " + NEXT_ROAD
                + " in " + DISTANCE_TO_MANEUVER + ".";
    }
}
