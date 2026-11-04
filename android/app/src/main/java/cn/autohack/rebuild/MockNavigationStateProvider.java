package cn.autohack.rebuild;

/** Deterministic sample route for UI and tests; never reads a real navigation app. */
public final class MockNavigationStateProvider implements NavigationStateProvider {
    private int index;

    private static final NavigationState[] STATES = {
        new NavigationState("Built-in demo", "Central Plaza", "Main Street",
                "Turn right in 300 m", 300, 8, true),
        new NavigationState("Built-in demo", "Central Plaza", "Riverside Avenue",
                "Continue for 1.2 km", 1200, 6, true),
        new NavigationState("Built-in demo", "Central Plaza", "Market Road",
                "Destination ahead", 80, 1, true),
        new NavigationState("Built-in demo", "", "", "No active route", 0, 0, false)
    };

    @Override public NavigationState getCurrentState() {
        return STATES[index];
    }

    public NavigationState advance() {
        index = (index + 1) % STATES.length;
        return getCurrentState();
    }

    public void reset() { index = 0; }
}
