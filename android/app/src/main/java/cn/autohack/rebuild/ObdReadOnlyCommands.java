package cn.autohack.rebuild;

/** Fixed allowlist of standard OBD-II read commands and harmless ELM adapter setup commands. */
public final class ObdReadOnlyCommands {
    public static final String SPEED = "010D";
    public static final String ENGINE_RPM = "010C";
    public static final String COOLANT_TEMP = "0105";
    public static final String FUEL_LEVEL = "012F";
    public static final String BITMAP_00 = "0100";
    public static final String BITMAP_20 = "0120";
    public static final String BITMAP_40 = "0140";
    public static final String STORED_DTC = "03";
    public static final String PENDING_DTC = "07";
    public static final String PERMANENT_DTC = "0A";
    public static final String ENGINE_STATUS = "0101";
    public static final int POLL_COUNT = 4;

    private ObdReadOnlyCommands() {}

    public static String commandAt(int index) {
        switch (index) {
            case 0: return SPEED;
            case 1: return ENGINE_RPM;
            case 2: return COOLANT_TEMP;
            case 3: return FUEL_LEVEL;
            default: return null;
        }
    }

    public static boolean isAllowed(String command) {
        if (command == null) return false;
        String normalized = command.trim().toUpperCase(java.util.Locale.ROOT);
        return SPEED.equals(normalized) || ENGINE_RPM.equals(normalized)
                || COOLANT_TEMP.equals(normalized) || FUEL_LEVEL.equals(normalized)
                || BITMAP_00.equals(normalized) || BITMAP_20.equals(normalized)
                || BITMAP_40.equals(normalized);
    }


    /** Explicitly requested standard Mode 03 read of stored diagnostic codes. */
    public static boolean isDiagnosticReadCommand(String command) {
        if (command == null) return false;
        String normalized = command.trim().toUpperCase(java.util.Locale.ROOT);
        return STORED_DTC.equals(normalized) || PENDING_DTC.equals(normalized)
                || PERMANENT_DTC.equals(normalized);
    }



    /** Standard Mode 01 PID 01 read-only MIL/status query. */
    public static boolean isStatusReadCommand(String command) {
        return command != null && ENGINE_STATUS.equals(
                command.trim().toUpperCase(java.util.Locale.ROOT));
}

    /** Read-only ELM adapter identification commands; they do not alter protocol settings. */
    public static boolean isAdapterInfoCommand(String command) {
        if (command == null) return false;
        String normalized = command.trim().toUpperCase(java.util.Locale.ROOT);
        return "ATDP".equals(normalized) || "ATDPN".equals(normalized);
    }

    public static boolean isAdapterSetupCommand(String command) {
        if (command == null) return false;
        String normalized = command.trim().toUpperCase(java.util.Locale.ROOT);
        return "ATZ".equals(normalized) || "ATE0".equals(normalized)
                || "ATL0".equals(normalized) || "ATS0".equals(normalized)
                || "ATH0".equals(normalized) || "ATSP0".equals(normalized);
    }
}
