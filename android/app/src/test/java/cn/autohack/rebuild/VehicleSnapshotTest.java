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

    @Test public void parsesImportedJsonSnapshot() throws Exception {
        String json = "{\"speedKph\":42,\"engineRpm\":2100,\"fuelPercent\":65,"
                + "\"coolantTempC\":91,\"gear\":\"D\",\"connected\":true}";
        VehicleSnapshot snapshot = VehicleSnapshot.fromJson(json);
        assertEquals(42, snapshot.speedKph);
        assertEquals(2100, snapshot.engineRpm);
        assertEquals("D", snapshot.gear);
        assertEquals("Imported JSON snapshot", snapshot.source);
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsMissingImportedField() throws Exception {
        VehicleSnapshot.fromJson("{\"speedKph\":1}");
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsFractionalTelemetryValue() throws Exception {
        VehicleSnapshot.fromJson("{\"speedKph\":1.5,\"engineRpm\":1000,"
                + "\"fuelPercent\":50,\"coolantTempC\":80,\"gear\":\"P\"}");
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsInvalidConnectedType() throws Exception {
        VehicleSnapshot.fromJson("{\"speedKph\":1,\"engineRpm\":1000,"
                + "\"fuelPercent\":50,\"coolantTempC\":80,\"gear\":\"P\","
                + "\"connected\":\"yes\"}");
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
