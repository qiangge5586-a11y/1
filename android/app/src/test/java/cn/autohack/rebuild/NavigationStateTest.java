package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NavigationStateTest {
    @Test public void mockProviderCyclesThroughRouteAndInactiveState() {
        MockNavigationStateProvider provider = new MockNavigationStateProvider();
        assertTrue(provider.getCurrentState().active);
        assertEquals(300, provider.getCurrentState().distanceMeters);
        assertEquals(1200, provider.advance().distanceMeters);
        assertEquals(80, provider.advance().distanceMeters);
        assertFalse(provider.advance().active);
        assertTrue(provider.advance().active);
    }

    @Test public void resetReturnsToFirstDemoState() {
        MockNavigationStateProvider provider = new MockNavigationStateProvider();
        provider.advance();
        provider.reset();
        assertEquals("Main Street", provider.getCurrentState().currentRoad);
    }

    @Test public void inactiveStateDoesNotExposeStaleRouteDetails() {
        NavigationState state = new NavigationState("Test", "", "", "No active route", 0, 0, false);
        assertEquals("Navigation: inactive\nSource: Test", state.toDisplayString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeDistance() {
        new NavigationState("Test", "A", "B", "Turn", -1, 1, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingSource() {
        new NavigationState(" ", "A", "B", "Turn", 1, 1, true);
    }
}
