package cn.autohack.rebuild;

/** Fixed allowlist of standard OBD-II read-only Mode 01 requests used by this rebuild. */
public final class ObdReadOnlyCommands {
    public static final String[] POLL_COMMANDS = {"010D", "010C", "0105", "012F"};

    private ObdReadOnlyCommands() {}

    public static boolean isAllowed(String command) {
        if (command == null) return false;
        String normalized = command.trim().toUpperCase(java.util.Locale.ROOT);
        for (String allowed : POLL_COMMANDS) {
            if (allowed.equals(normalized)) return true;
        }
        return false;
    }

    public static boolean isAdapterSetupCommand(String command) {
        if (command == null) return false;
        String normalized = command.trim().toUpperCase(java.util.Locale.ROOT);
        return "ATZ".equals(normalized) || "ATE0".equals(normalized)
                || "ATL0".equals(normalized) || "ATS0".equals(normalized)
                || "ATH0".equals(normalized) || "ATSP0".equals(normalized);
    }
}
