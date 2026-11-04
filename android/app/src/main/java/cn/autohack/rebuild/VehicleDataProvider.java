package cn.autohack.rebuild;

/** Read-only contract for future documented vehicle telemetry adapters. */
public interface VehicleDataProvider {
    VehicleSnapshot getLatestSnapshot();
}
