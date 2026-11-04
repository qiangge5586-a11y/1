package cn.autohack.rebuild;

/** Static offline sample values; not connected to CAN, OBD, ECU or the head unit. */
public final class MockVehicleDataProvider implements VehicleDataProvider {
    @Override public VehicleSnapshot getLatestSnapshot() {
        return new VehicleSnapshot("Built-in demo data", 0, 850, 76, 88, "P", true);
    }
}
