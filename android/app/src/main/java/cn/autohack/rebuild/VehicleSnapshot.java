package cn.autohack.rebuild;

/** Immutable read-only vehicle telemetry snapshot. No hardware access is performed here. */
public final class VehicleSnapshot {
    public final String source;
    public final int speedKph;
    public final int engineRpm;
    public final int fuelPercent;
    public final int coolantTempC;
    public final String gear;
    public final boolean connected;

    public VehicleSnapshot(String source, int speedKph, int engineRpm, int fuelPercent,
            int coolantTempC, String gear, boolean connected) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException("source is required");
        if (speedKph < 0 || speedKph > 400) throw new IllegalArgumentException("speed out of range");
        if (engineRpm < 0 || engineRpm > 20000) throw new IllegalArgumentException("RPM out of range");
        if (fuelPercent < 0 || fuelPercent > 100) throw new IllegalArgumentException("fuel out of range");
        if (coolantTempC < -50 || coolantTempC > 200) throw new IllegalArgumentException("coolant temperature out of range");
        if (gear == null) throw new IllegalArgumentException("gear is required");
        this.source = source;
        this.speedKph = speedKph;
        this.engineRpm = engineRpm;
        this.fuelPercent = fuelPercent;
        this.coolantTempC = coolantTempC;
        this.gear = gear;
        this.connected = connected;
    }

    public static VehicleSnapshot fromJson(String json) throws org.json.JSONException {
        if (json == null || json.length() > 65536)
            throw new org.json.JSONException("Vehicle snapshot JSON is empty or exceeds 64 KiB");
        org.json.JSONObject object = new org.json.JSONObject(json);
        String[] required = {"speedKph", "engineRpm", "fuelPercent", "coolantTempC", "gear"};
        for (String key : required) {
            if (!object.has(key) || object.isNull(key))
                throw new org.json.JSONException("Missing required field: " + key);
        }
        return new VehicleSnapshot("Imported JSON snapshot",
                object.getInt("speedKph"),
                object.getInt("engineRpm"),
                object.getInt("fuelPercent"),
                object.getInt("coolantTempC"),
                object.getString("gear"),
                object.optBoolean("connected", true));
    }

    public String toDisplayString() {
        if (!connected) return "Vehicle data: disconnected\nSource: " + source;
        return "Vehicle data: demo snapshot"
                + "\nSpeed: " + speedKph + " km/h"
                + "\nEngine speed: " + engineRpm + " rpm"
                + "\nFuel: " + fuelPercent + "%"
                + "\nCoolant: " + coolantTempC + " °C"
                + "\nGear: " + gear
                + "\nSource: " + source;
    }
}
