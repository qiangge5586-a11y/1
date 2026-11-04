package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VehicleSnapshotTest {
    @Test public void mockProviderReturnsExplicitDemoSnapshot() {
        VehicleSnapshot snapshot = new MockVehicleDataProvider().getLatestSnapshot();
        assertTrue(snapshot.connected);
        assertEquals("Built-in demo data", snapshot.source);
        assertEquals(0, snapshot.speedKph);
        assertEquals(850, snapshot.engineRpm);
    }

    @Test public void disconnectedSnapshotHidesStaleTelemetry() {
        VehicleSnapshot snapshot = new VehicleSnapshot("No adapter", 0, 0, 0, 0, "", false);
        assertEquals("Vehicle data: disconnected\nSource: No adapter", snapshot.toDisplayString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsImpossibleSpeed() {
        new VehicleSnapshot("test", 401, 800, 50, 80, "P", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFuelOutsidePercentageRange() {
        new VehicleSnapshot("test", 0, 800, 101, 80, "P", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExtremeRpm() {
        new VehicleSnapshot("test", 0, 20001, 50, 80, "P", true);
    }
}
