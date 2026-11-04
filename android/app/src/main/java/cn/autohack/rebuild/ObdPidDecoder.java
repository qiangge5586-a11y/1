package cn.autohack.rebuild;

/** Standard SAE J1979 Mode 01 PID decoders for common generic OBD-II values. */
public final class ObdPidDecoder {
    private ObdPidDecoder() {}

    public static Integer speedKph(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x0D, 1);
        return b == null ? null : b[0];
    }

    public static Integer engineRpm(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x0C, 2);
        return b == null ? null : ((b[0] * 256) + b[1]) / 4;
    }

    public static Integer coolantTempC(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x05, 1);
        return b == null ? null : b[0] - 40;
    }

    public static Integer fuelLevelPercent(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x2F, 1);
        return b == null ? null : Math.round(b[0] * 100.0f / 255.0f);
    }

    public static Integer engineLoadPercent(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x04, 1);
        return b == null ? null : Math.round(b[0] * 100.0f / 255.0f);
    }

    public static Integer throttlePercent(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x11, 1);
        return b == null ? null : Math.round(b[0] * 100.0f / 255.0f);
    }

    public static Float mafGramsPerSecond(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x10, 2);
        return b == null ? null : ((b[0] * 256) + b[1]) / 100.0f;
    }

    public static Integer intakeTempC(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x0F, 1);
        return b == null ? null : b[0] - 40;
    }

    public static Integer oilTempC(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x5C, 1);
        return b == null ? null : b[0] - 40;
    }

    public static Integer intakeManifoldPressureKpa(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x0B, 1);
        return b == null ? null : b[0];
    }

    public static Integer barometricPressureKpa(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x33, 1);
        return b == null ? null : b[0];
    }

    public static Float timingAdvanceDegrees(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x0E, 1);
        return b == null ? null : b[0] / 2.0f - 64.0f;
    }

    public static Float fuelRateLitersPerHour(String response) {
        int[] b = Elm327ResponseParser.parsePidBytes(response, 0x5E, 2);
        return b == null ? null : ((b[0] * 256) + b[1]) / 20.0f;
    }
}
